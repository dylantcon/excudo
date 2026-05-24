package com.excudo.core.llm;

import com.excudo.core.commands.mutating.slide.EnhancedContentCommand;
import com.excudo.core.commands.RequestSchema;
import com.excudo.core.validation.ValidationResult;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.orchestration.*;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.Parameter.ParameterType;
import com.excudo.core.services.ContextService;
import com.excudo.core.model.SlideShape;
import com.excudo.core.utils.JsonHelper;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.google.gson.*;
import java.util.*;
import java.io.File;

/**
 * Parser and validator for LLM request JSON.
 * Provides JSON parsing, schema validation, and request structure validation.
 */
public class RequestParser {

    private static final ComponentLogger logger = Logger.getLogger(RequestParser.class);

    private final boolean strictValidation;
    private final PPTXOrchestrator orchestrator;
    private final ContextService contextService;
    // disableHtmlEscaping so the array/object re-serialization at line 197
    // below doesn't escape apostrophes to \u0027 (see ToolDispatcher for the
    // same rationale).
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Schema-driven parameter registry: every parameter name any LLM-enabled command accepts, mapped to its type. */
    private static final Map<String, ParameterType> ALL_LLM_PARAMS = buildAllLlmParams();

    private static Map<String, ParameterType> buildAllLlmParams() {
        Map<String, ParameterType> result = new LinkedHashMap<>();
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            if (!schema.isLlmEnabled()) continue;
            for (Parameter p : schema.getParameters()) {
                result.putIfAbsent(p.getEffectiveLlmName(), p.getType());
                result.putIfAbsent(p.getName(), p.getType());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public RequestParser(boolean strictValidation) {
        this.strictValidation = strictValidation;
        this.orchestrator = null;
        this.contextService = null;
    }

    public RequestParser() {
        this(true);
    }

    /**
     * Constructor with Model-aware validation capabilities
     */
    public RequestParser(boolean strictValidation, PPTXOrchestrator orchestrator) {
        this.strictValidation = strictValidation;
        this.orchestrator = orchestrator;

        // Initialize ContextService if orchestrator is available
        ContextService tempContextService = null;
        if (orchestrator != null) {
            try {
                // Use the shared ContextService from the orchestrator's context
                tempContextService = orchestrator.getContextService();
            } catch (Exception e) {
                logger.warn("Could not initialize ContextService for Model-aware validation: {}", e.getMessage());
            }
        }
        this.contextService = tempContextService;
    }

    // ========== JSON PARSING ==========

    /**
     * Parse JSON request string into LLMRequest structure
     */
    public ExecutionResult<RequestSchema.LLMRequest> parseRequest(String requestJson) {
        try {
            ValidationResult validation = validateJsonStructure(requestJson);
            if (!validation.isValid()) {
                return ExecutionResult.failure("request-parsing", validation);
            }

            RequestSchema.LLMRequest request = parseJsonToRequest(requestJson);

            if (strictValidation) {
                ValidationResult requestValidation = validateRequestStructure(request);
                if (!requestValidation.isValid()) {
                    return ExecutionResult.failure("request-validation", requestValidation);
                }
            }

            return ExecutionResult.success("request-parsing", request);

        } catch (Exception e) {
            return ExecutionResult.failure("request-parsing", "JSON parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON request using Gson.
     */
    private RequestSchema.LLMRequest parseJsonToRequest(String json) {
        JsonObject root = JsonHelper.parseObject(json);

        // Extract schema version
        String schemaVersion = JsonHelper.getString(root, "schemaVersion");

        // Extract actions array (models may use either key)
        List<RequestSchema.ActionRequest> actions = parseActions(root);

        // Extract metadata
        RequestSchema.RequestMetadata metadata = parseMetadata(root);

        return new RequestSchema.LLMRequest(schemaVersion, actions, metadata);
    }

    private List<RequestSchema.ActionRequest> parseActions(JsonObject root) {
        List<RequestSchema.ActionRequest> actions = new ArrayList<>();

        // Try "operations" first, then "actions"
        JsonArray actionsArray = null;
        if (root.has("operations")) {
            actionsArray = root.getAsJsonArray("operations");
        } else if (root.has("actions")) {
            actionsArray = root.getAsJsonArray("actions");
        }
        if (actionsArray == null) return actions;

        for (JsonElement el : actionsArray) {
            if (!el.isJsonObject()) continue;
            RequestSchema.ActionRequest action = parseAction(el.getAsJsonObject());
            if (action != null) {
                actions.add(action);
            }
        }

        return actions;
    }

    private RequestSchema.ActionRequest parseAction(JsonObject actionObj) {
        String type = JsonHelper.getString(actionObj, "type");
        String description = JsonHelper.getString(actionObj, "description");

        // Extract priority as integer
        Integer priority = actionObj.has("priority") ? actionObj.get("priority").getAsInt() : null;

        // Extract parameters
        Map<String, Object> parameters = parseParameters(actionObj);

        return new RequestSchema.ActionRequest(type, parameters, description, priority);
    }

    private Map<String, Object> parseParameters(JsonObject actionObj) {
        // Look for a nested "parameters" object, or use top-level keys
        JsonObject paramsObj;
        if (actionObj.has("parameters") && actionObj.get("parameters").isJsonObject()) {
            paramsObj = actionObj.getAsJsonObject("parameters");
        } else {
            // No nested parameters object -- use the action object itself
            // (minus type/description/priority which are metadata)
            paramsObj = actionObj;
        }

        return parseParametersObject(paramsObj);
    }

    private Map<String, Object> parseParametersObject(JsonObject paramsObj) {
        Map<String, Object> params = new HashMap<>();

        // Schema-driven extraction: try every known LLM parameter name
        for (Map.Entry<String, ParameterType> entry : ALL_LLM_PARAMS.entrySet()) {
            String name = entry.getKey();
            ParameterType type = entry.getValue();

            if (!paramsObj.has(name)) continue;
            JsonElement el = paramsObj.get(name);
            if (el.isJsonNull()) continue;

            switch (type) {
                case INTEGER, SLIDE_NUMBER, SPID -> params.put(name, el.getAsInt());
                case DOUBLE -> params.put(name, el.getAsDouble());
                case BOOLEAN -> params.put(name, el.getAsBoolean());
                default -> {
                    if (el.isJsonPrimitive()) {
                        params.put(name, el.getAsString());
                    } else if (el.isJsonArray()) {
                        // For array values (e.g. content: ["col1", "col2"]), store as JSON string
                        params.put(name, GSON.toJson(el));
                    }
                }
            }
        }

        // Ensure values are accessible under both canonical and LLM names
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            if (!schema.isLlmEnabled()) continue;
            for (Parameter p : schema.getParameters()) {
                String canonical = p.getName();
                String llm = p.getEffectiveLlmName();
                if (!canonical.equals(llm)) {
                    if (params.containsKey(canonical) && !params.containsKey(llm))
                        params.put(llm, params.get(canonical));
                    else if (params.containsKey(llm) && !params.containsKey(canonical))
                        params.put(canonical, params.get(llm));
                }
            }
        }

        // Geometry bundling: if x/y/width/height at top level (no nested geometry object),
        // wrap into geometry map for downstream compatibility
        if (!paramsObj.has("geometry")) {
            if (paramsObj.has("x") && paramsObj.has("y")
                    && paramsObj.has("width") && paramsObj.has("height")) {
                Map<String, Object> geometry = new HashMap<>();
                geometry.put("x", paramsObj.get("x").getAsDouble());
                geometry.put("y", paramsObj.get("y").getAsDouble());
                geometry.put("width", paramsObj.get("width").getAsDouble());
                geometry.put("height", paramsObj.get("height").getAsDouble());
                params.put("geometry", geometry);
            }
        }

        // Legacy nested object extraction (shapeData, animationData, geometry)
        for (String nestedKey : List.of("geometry", "shapeData", "animationData")) {
            if (paramsObj.has(nestedKey) && paramsObj.get(nestedKey).isJsonObject()) {
                params.put(nestedKey, extractNestedObject(paramsObj.getAsJsonObject(nestedKey)));
            }
        }

        return params;
    }

    private Map<String, Object> extractNestedObject(JsonObject obj) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement val = entry.getValue();
            if (val.isJsonNull()) continue;
            if (val.isJsonPrimitive()) {
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    result.put(entry.getKey(), prim.getAsDouble());
                } else if (prim.isBoolean()) {
                    result.put(entry.getKey(), prim.getAsBoolean());
                } else {
                    result.put(entry.getKey(), prim.getAsString());
                }
            } else if (val.isJsonObject()) {
                result.put(entry.getKey(), extractNestedObject(val.getAsJsonObject()));
            }
        }
        return result;
    }

    private RequestSchema.RequestMetadata parseMetadata(JsonObject root) {
        Double confidence = root.has("confidence") ? root.get("confidence").getAsDouble() : null;
        String reasoning = JsonHelper.getString(root, "reasoning");
        Boolean contextUsed = root.has("contextUsed") ? root.get("contextUsed").getAsBoolean() : null;
        List<String> warnings = new ArrayList<>();

        return new RequestSchema.RequestMetadata(confidence, reasoning, warnings, contextUsed);
    }

    // ========== VALIDATION ==========

    /**
     * Validate JSON structure and syntax
     */
    public ValidationResult validateJsonStructure(String json) {
        ValidationResult.Builder builder = ValidationResult.builder();

        if (json == null || json.trim().isEmpty()) {
            builder.addError("Request JSON is empty");
            return builder.build();
        }

        // Basic JSON structure validation
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            builder.addError("JSON must be a valid object starting with { and ending with }");
        }

        // Check for required fields
        if (!json.contains("\"schemaVersion\"")) {
            builder.addError("Missing required field: schemaVersion");
        }

        if (!json.contains("\"operations\"") && !json.contains("\"actions\"")) {
            builder.addError("Missing required field: operations (or actions)");
        }

        // Validate JSON syntax with Gson
        try {
            JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            builder.addError("Invalid JSON syntax: " + e.getMessage());
        }

        return builder.build();
    }

    /**
     * Validate parsed request structure
     */
    public ValidationResult validateRequestStructure(RequestSchema.LLMRequest request) {
        ValidationResult.Builder builder = ValidationResult.builder();

        if (request == null) {
            builder.addError("Request is null");
            return builder.build();
        }

        // Validate schema version
        if (!RequestSchema.SCHEMA_VERSION.equals(request.getSchemaVersion())) {
            builder.addError("Unsupported schema version: " + request.getSchemaVersion());
        }

        // Validate operations
        if (request.getActions() == null || request.getActions().isEmpty()) {
            builder.addError("Operations list is empty");
        } else {
            if (request.getActions().size() > 20) {
                builder.addError("Too many operations (max 20): " + request.getActions().size());
            }

            // Validate each operation
            for (int i = 0; i < request.getActions().size(); i++) {
                RequestSchema.ActionRequest op = request.getActions().get(i);
                ValidationResult opValidation = validateAction(op, i);
                builder.addErrors(opValidation.getErrors());
                for (String warning : opValidation.getWarnings()) {
                    builder.addWarning(warning);
                }
            }
        }

        // Validate metadata
        if (request.getMetadata() != null) {
            ValidationResult metaValidation = validateMetadata(request.getMetadata());
            for (String warning : metaValidation.getWarnings()) {
                builder.addWarning(warning);
            }
        }

        return builder.build();
    }

    private ValidationResult validateAction(RequestSchema.ActionRequest operation, int index) {
        ValidationResult.Builder builder = ValidationResult.builder();
        String prefix = "Operation " + (index + 1) + ": ";

        if (operation.getType() == null || operation.getType().trim().isEmpty()) {
            builder.addError(prefix + "Missing operation type");
        } else {
            if (!LLMRequestBridge.isRecognizedActionType(operation.getType())) {
                builder.addError(prefix + "Invalid operation type: " + operation.getType()
                    + ". Valid types: " + String.join(", ", LLMRequestBridge.getLLMEnabledCommandNames()));
            }
        }

        if (operation.getParameters() == null) {
            builder.addError(prefix + "Missing parameters");
        } else {
            // Validate type-specific parameters
            ValidationResult paramValidation = validateActionParameters(operation);
            for (String error : paramValidation.getErrors()) {
                builder.addError(prefix + error);
            }
            for (String warning : paramValidation.getWarnings()) {
                builder.addWarning(prefix + warning);
            }
        }

        return builder.build();
    }

    private ValidationResult validateActionParameters(RequestSchema.ActionRequest operation) {
        ValidationResult.Builder builder = ValidationResult.builder();
        Map<String, Object> params = operation.getParameters();

        // Resolve to canonical command name so both legacy (slide-creation)
        // and current (create) type names hit the right validation branch
        String resolvedType;
        try {
            resolvedType = LLMRequestBridge.resolveCommandName(operation.getType());
        } catch (IllegalArgumentException e) {
            return builder.build();
        }

        // Schema-driven parameter validation
        com.excudo.core.parsing.CommandSchema schema =
            com.excudo.core.parsing.CommandRegistry.getSchema(resolvedType);
        if (schema != null) {
            for (com.excudo.core.parsing.Parameter p : schema.getParameters()) {
                String llmName = p.getEffectiveLlmName();
                String canonicalName = p.getName();

                // Warn on missing required params
                if (p.isRequired()) {
                    if (!params.containsKey(llmName) && !params.containsKey(canonicalName)) {
                        builder.addWarning("Missing parameter: " + llmName);
                    }
                }

                // Reject invalid enum values
                if (p.getValidValues() != null && !p.getValidValues().isEmpty()) {
                    Object value = params.containsKey(llmName) ? params.get(llmName)
                                 : params.get(canonicalName);
                    if (value != null) {
                        String strValue = String.valueOf(value);
                        if (!p.getValidValues().contains(strValue)) {
                            builder.addError("Invalid value for " + llmName + ": \"" + strValue
                                + "\". Must be one of: " + String.join(", ", p.getValidValues()));
                        }
                    }
                }
            }
        }

        // Command-specific validation that can't be derived from schema
        if ("edit-bullet".equals(resolvedType) && params.containsKey("editType")) {
            String editType = (String) params.get("editType");
            validateBulletPointEditOperation(editType, params, builder);
        }
        if (EnhancedContentCommand.NAME.equals(resolvedType) && params.containsKey("geometry")) {
            Map<?, ?> geometry = (Map<?, ?>) params.get("geometry");
            validateGeometry(geometry, builder);
        }

        return builder.build();
    }

    private ValidationResult validateMetadata(RequestSchema.RequestMetadata metadata) {
        ValidationResult.Builder builder = ValidationResult.builder();

        if (metadata.getConfidence() != null) {
            double conf = metadata.getConfidence();
            if (conf < 0.0 || conf > 1.0) {
                builder.addWarning("Confidence should be between 0.0 and 1.0: " + conf);
            } else if (conf < 0.7) {
                builder.addWarning("Low confidence in command: " + conf);
            }
        }

        return builder.build();
    }

    // ========== MODEL-AWARE VALIDATION METHODS ==========


    /**
     * Validate bullet point edit operations
     */
    private void validateBulletPointEditOperation(String editType, Map<String, Object> params, ValidationResult.Builder builder) {
        if (contextService == null) return;

        try {
            switch (editType) {
                case "update-bullet":
                    validateUpdateBulletOperation(params, builder);
                    break;
                case "insert-bullet":
                    validateInsertBulletOperation(params, builder);
                    break;
                case "delete-bullet":
                    validateDeleteBulletOperation(params, builder);
                    break;
                case "animate-bullet":
                    validateAnimateBulletOperation(params, builder);
                    break;
                default:
                    builder.addWarning("Unknown bullet point edit type: " + editType);
            }
        } catch (Exception e) {
            builder.addWarning("Bullet point validation failed: " + e.getMessage());
        }
    }

    private void validateUpdateBulletOperation(Map<String, Object> params, ValidationResult.Builder builder) throws XMLParsingException {
        int slideNumber = ((Number) params.get("slideNumber")).intValue();
        int targetSpid = ((Number) params.get("targetSpid")).intValue();

        if (!validateTextShape(slideNumber, targetSpid, builder)) return;

        if (!params.containsKey("bulletIndex")) {
            builder.addError("Missing required parameter for update-bullet: bulletIndex");
            return;
        }

        if (!params.containsKey("content")) {
            builder.addError("Missing required parameter for update-bullet: content");
            return;
        }

        int bulletIndex = ((Number) params.get("bulletIndex")).intValue();
        validateBulletIndexExists(slideNumber, targetSpid, bulletIndex, builder);
    }

    private void validateInsertBulletOperation(Map<String, Object> params, ValidationResult.Builder builder) throws XMLParsingException {
        int slideNumber = ((Number) params.get("slideNumber")).intValue();
        int targetSpid = ((Number) params.get("targetSpid")).intValue();

        if (!validateTextShape(slideNumber, targetSpid, builder)) return;

        if (!params.containsKey("content")) {
            builder.addError("Missing required parameter for insert-bullet: content");
            return;
        }

        if (params.containsKey("insertIndex")) {
            int insertIndex = ((Number) params.get("insertIndex")).intValue();
            if (insertIndex < 0) {
                builder.addError("insertIndex must be non-negative");
            }
        }
    }

    private void validateDeleteBulletOperation(Map<String, Object> params, ValidationResult.Builder builder) throws XMLParsingException {
        int slideNumber = ((Number) params.get("slideNumber")).intValue();
        int targetSpid = ((Number) params.get("targetSpid")).intValue();

        if (!validateTextShape(slideNumber, targetSpid, builder)) return;

        if (!params.containsKey("bulletIndex")) {
            builder.addError("Missing required parameter for delete-bullet: bulletIndex");
            return;
        }

        int bulletIndex = ((Number) params.get("bulletIndex")).intValue();
        validateBulletIndexExists(slideNumber, targetSpid, bulletIndex, builder);
    }

    private void validateAnimateBulletOperation(Map<String, Object> params, ValidationResult.Builder builder) throws XMLParsingException {
        int slideNumber = ((Number) params.get("slideNumber")).intValue();
        int targetSpid = ((Number) params.get("targetSpid")).intValue();

        if (!validateTextShape(slideNumber, targetSpid, builder)) return;

        if (!params.containsKey("bulletIndex")) {
            builder.addError("Missing required parameter for animate-bullet: bulletIndex");
            return;
        }

        if (!params.containsKey("animationData")) {
            builder.addError("Missing required parameter for animate-bullet: animationData");
            return;
        }

        int bulletIndex = ((Number) params.get("bulletIndex")).intValue();
        validateBulletIndexExists(slideNumber, targetSpid, bulletIndex, builder);
    }

    private boolean validateTextShape(int slideNumber, int targetSpid, ValidationResult.Builder builder) throws XMLParsingException {
        boolean spidExists = contextService.validateSpidExists(targetSpid, slideNumber);
        if (!spidExists) {
            builder.addError("Target SPID " + targetSpid + " does not exist on slide " + slideNumber);
            return false;
        }

        ContextService.SlideContext slideContext = contextService.getSlideContext(slideNumber);
        SlideShape targetShape = slideContext.getSlideData().getShapeRegistry().getShape(targetSpid);
        if (targetShape == null || !targetShape.hasText()) {
            builder.addError("Target shape " + targetSpid + " does not contain text content");
            return false;
        }

        if (!targetShape.hasBulletPoints()) {
            builder.addWarning("Target shape " + targetSpid + " does not contain bullet points");
        }

        return true;
    }

    private void validateBulletIndexExists(int slideNumber, int targetSpid, int bulletIndex, ValidationResult.Builder builder) throws XMLParsingException {
        ContextService.SlideContext slideContext = contextService.getSlideContext(slideNumber);
        SlideShape targetShape = slideContext.getSlideData().getShapeRegistry().getShape(targetSpid);

        if (targetShape != null && targetShape.hasParagraphMetadata()) {
            int bulletCount = targetShape.getBulletPointCount();
            if (bulletIndex < 0 || bulletIndex >= bulletCount) {
                builder.addError("Bullet index " + bulletIndex + " is out of range (0-" + (bulletCount - 1) + ")");
            }
        }
    }



    /**
     * Validate geometry parameters
     */
    private void validateGeometry(Map<?, ?> geometry, ValidationResult.Builder builder) {
        String[] requiredFields = {"x", "y", "width", "height"};
        for (String field : requiredFields) {
            if (!geometry.containsKey(field)) {
                builder.addError("Missing geometry field: " + field);
            } else {
                Object value = geometry.get(field);
                if (!(value instanceof Number)) {
                    builder.addError("Geometry field " + field + " must be numeric");
                } else {
                    double numValue = ((Number) value).doubleValue();
                    if (field.equals("width") || field.equals("height")) {
                        if (numValue <= 0) {
                            builder.addError("Geometry " + field + " must be positive");
                        }
                    }
                }
            }
        }
    }


    /**
     * Validate animation data parameters
     */
    private void validateAnimationData(Map<?, ?> animData, ValidationResult.Builder builder) {
        if (!animData.containsKey("animationType")) {
            builder.addError("Missing animation parameter: animationType");
        }

        if (!animData.containsKey("clickTrigger")) {
            builder.addError("Missing animation parameter: clickTrigger");
        } else {
            Object clickTrigger = animData.get("clickTrigger");
            if (!(clickTrigger instanceof Number)) {
                builder.addError("clickTrigger must be numeric");
            } else {
                int clickNum = ((Number) clickTrigger).intValue();
                if (clickNum < 1) {
                    builder.addError("clickTrigger must be 1 or greater");
                }
            }
        }

        if (animData.containsKey("direction")) {
            String direction = (String) animData.get("direction");
            if (!List.of("entrance", "exit", "emphasis", "motion").contains(direction)) {
                builder.addWarning("Unknown animation direction: " + direction);
            }
        }

        if (animData.containsKey("animationGroup")) {
            String group = (String) animData.get("animationGroup");
            if (!List.of("on-click", "with-previous", "after-previous").contains(group)) {
                builder.addWarning("Unknown animation group: " + group);
            }
        }
    }
}
