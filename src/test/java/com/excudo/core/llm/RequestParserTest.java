package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.validation.ValidationResult;
import com.excudo.core.results.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class RequestParserTest {

    private RequestParser parser;

    // A minimal request that satisfies all structural checks and strict validation.
    // The JSON array key is "operations"; each element uses "type" for the action name.
    private static final String VALID_MINIMAL_JSON =
        "{" +
        "\"schemaVersion\": \"" + RequestSchema.SCHEMA_VERSION + "\"," +
        "\"operations\": [" +
        "  {\"type\": \"create\", \"parameters\": {\"position\": 1}}" +
        "]" +
        "}";

    @Before
    public void setUp() {
        parser = new RequestParser();
    }

    // ========== CONSTRUCTOR TESTS ==========

    @Test
    public void defaultConstructorCreatesParserWithoutException() {
        RequestParser p = new RequestParser();
        assertNotNull(p);
    }

    @Test
    public void constructorWithStrictFalseCreatesParserWithoutException() {
        RequestParser p = new RequestParser(false);
        assertNotNull(p);
    }

    @Test
    public void constructorWithNullOrchestratorCreatesParserWithoutException() {
        RequestParser p = new RequestParser(false, null);
        assertNotNull(p);
    }

    // ========== validateJsonStructure TESTS ==========

    @Test
    public void validateNullInputReturnsInvalid() {
        ValidationResult result = parser.validateJsonStructure(null);
        assertFalse("Null input must produce an invalid result", result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    public void validateEmptyStringReturnsInvalid() {
        ValidationResult result = parser.validateJsonStructure("");
        assertFalse("Empty string must produce an invalid result", result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    public void validateNonJsonStringReturnsInvalid() {
        ValidationResult result = parser.validateJsonStructure("not json at all");
        assertFalse(result.isValid());
    }

    @Test
    public void validateWellFormedJsonObjectReturnsValid() {
        ValidationResult result = parser.validateJsonStructure(VALID_MINIMAL_JSON);
        assertTrue("Well-formed JSON must produce a valid structural result", result.isValid());
    }

    @Test
    public void validateStringMissingOperationsFieldReturnsInvalid() {
        String json = "{\"schemaVersion\": \"" + RequestSchema.SCHEMA_VERSION + "\"}";
        ValidationResult result = parser.validateJsonStructure(json);
        assertFalse("JSON without 'operations' field must be invalid", result.isValid());
        boolean mentionsOperations = result.getErrors().stream()
                .anyMatch(e -> e.toLowerCase().contains("operations"));
        assertTrue("Error list must mention the missing 'operations' field", mentionsOperations);
    }

    @Test
    public void validateStringMissingOpeningBraceReturnsInvalid() {
        String json = "\"schemaVersion\": \"" + RequestSchema.SCHEMA_VERSION + "\"}";
        ValidationResult result = parser.validateJsonStructure(json);
        assertFalse("String without opening brace must be invalid", result.isValid());
    }

    // ========== parseRequest TESTS ==========

    @Test
    public void parseValidMinimalRequestReturnsSuccess() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(VALID_MINIMAL_JSON);
        assertTrue("Valid minimal request must parse successfully", result.isSuccess());
    }

    @Test
    public void parseNullInputReturnsFailure() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(null);
        assertFalse("Parsing null must return a failure result", result.isSuccess());
    }

    @Test
    public void parseEmptyStringReturnsFailure() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest("");
        assertFalse("Parsing empty string must return a failure result", result.isSuccess());
    }

    @Test
    public void parseMalformedJsonReturnsFailure() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest("{not valid json");
        assertFalse("Parsing malformed JSON must return a failure result", result.isSuccess());
    }

    @Test
    public void parseRequestWithInvalidSchemaVersionFailsUnderStrictValidation() {
        String json =
            "{" +
            "\"schemaVersion\": \"99.99\"," +
            "\"operations\": [" +
            "  {\"type\": \"create\", \"parameters\": {\"position\": 1}}" +
            "]" +
            "}";
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(json);
        assertFalse("Unknown schema version must be rejected under strict validation", result.isSuccess());
    }

    @Test
    public void parseRequestWithMultipleOperationsParsesAllActions() {
        String json =
            "{" +
            "\"schemaVersion\": \"" + RequestSchema.SCHEMA_VERSION + "\"," +
            "\"operations\": [" +
            "  {\"type\": \"create\", \"parameters\": {\"position\": 1}}," +
            "  {\"type\": \"create\", \"parameters\": {\"position\": 2}}" +
            "]" +
            "}";
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(json);
        assertTrue("Request with two operations must parse successfully", result.isSuccess());
        Optional<RequestSchema.LLMRequest> data = result.getData();
        assertTrue(data.isPresent());
        assertEquals(2, data.get().getActions().size());
    }

    @Test
    public void parseValidRequestPopulatesSchemaVersion() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(VALID_MINIMAL_JSON);
        assertTrue(result.isSuccess());
        Optional<RequestSchema.LLMRequest> data = result.getData();
        assertTrue(data.isPresent());
        assertEquals(RequestSchema.SCHEMA_VERSION, data.get().getSchemaVersion());
    }

    @Test
    public void parseValidRequestPopulatesOperationsList() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(VALID_MINIMAL_JSON);
        assertTrue(result.isSuccess());
        Optional<RequestSchema.LLMRequest> data = result.getData();
        assertTrue(data.isPresent());
        List<RequestSchema.ActionRequest> actions = data.get().getActions();
        assertNotNull(actions);
        assertFalse("Parsed request must have at least one operation", actions.isEmpty());
    }

    @Test
    public void parseFailureResultHasNonNullErrorMessage() {
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(null);
        assertFalse(result.isSuccess());
        assertNotNull("Error message must not be null on failure", result.getErrorMessage());
    }

    @Test
    public void parseRequestWithInvalidEnumValueIsRejected() {
        // "direction":"None" is not in validValues("in", "out", "emphasis")
        String json =
            "{" +
            "\"schemaVersion\": \"" + RequestSchema.SCHEMA_VERSION + "\"," +
            "\"actions\": [" +
            "  {\"type\": \"add-animation\", \"parameters\": {" +
            "    \"slideNumber\": 2, \"targetSpid\": 3," +
            "    \"animationType\": \"Fade\", \"direction\": \"None\"" +
            "  }}" +
            "]" +
            "}";
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(json);
        assertFalse("Request with invalid enum value for direction must be rejected", result.isSuccess());
    }

    @Test
    public void parseRequestWithValidEnumValueIsAccepted() {
        String json =
            "{" +
            "\"schemaVersion\": \"" + RequestSchema.SCHEMA_VERSION + "\"," +
            "\"actions\": [" +
            "  {\"type\": \"add-animation\", \"parameters\": {" +
            "    \"slideNumber\": 2, \"targetSpid\": 3," +
            "    \"animationType\": \"fade\", \"direction\": \"in\"" +
            "  }}" +
            "]" +
            "}";
        ExecutionResult<RequestSchema.LLMRequest> result = parser.parseRequest(json);
        assertTrue("Request with valid enum value for direction must be accepted", result.isSuccess());
    }

    @Test
    public void parseRequestWithInvalidSchemaVersionSucceedsUnderLenientValidation() {
        String json =
            "{" +
            "\"schemaVersion\": \"99.99\"," +
            "\"operations\": [" +
            "  {\"type\": \"create\", \"parameters\": {\"position\": 1}}" +
            "]" +
            "}";
        RequestParser lenient = new RequestParser(false);
        ExecutionResult<RequestSchema.LLMRequest> result = lenient.parseRequest(json);
        assertTrue("Unknown schema version must be accepted when strictValidation=false", result.isSuccess());
    }
}
