package com.excudo.core.parsing;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for CommandSchema and its Builder.
 *
 * CommandSchema is a GoF Builder pattern: private constructor, only created
 * through CommandSchema.Builder. Parameter is also a Builder-only class.
 */
public class CommandSchemaTest {

    // ========== HELPER BUILDERS ==========

    private Parameter requiredStringParam(String name, String description) {
        return Parameter.builder(name)
                .description(description)
                .type(Parameter.ParameterType.STRING)
                .required(true)
                .build();
    }

    private Parameter optionalIntParam(String name, String description) {
        return Parameter.builder(name)
                .description(description)
                .type(Parameter.ParameterType.INTEGER)
                .required(false)
                .build();
    }

    private CommandSchema buildTwoParamSchema() {
        return CommandSchema.builder("test-cmd")
                .description("A test command")
                .parameter(requiredStringParam("param1", "First parameter"))
                .parameter(optionalIntParam("param2", "Second parameter"))
                .example("test-cmd foo 42")
                .build();
    }

    // ========== TEST CASES ==========

    @Test
    public void builderCreatesSchemaWithCorrectName() {
        CommandSchema schema = CommandSchema.builder("my-command")
                .description("Does something")
                .build();

        assertEquals("my-command", schema.getName());
    }

    @Test
    public void builderCreatesSchemaWithCorrectDescription() {
        CommandSchema schema = CommandSchema.builder("cmd")
                .description("Does something useful")
                .build();

        assertEquals("Does something useful", schema.getDescription());
    }

    @Test
    public void getParametersReturnsAllParametersInOrder() {
        CommandSchema schema = buildTwoParamSchema();

        List<Parameter> params = schema.getParameters();

        assertEquals(2, params.size());
        assertEquals("param1", params.get(0).getName());
        assertEquals("param2", params.get(1).getName());
    }

    @Test
    public void getParameterByNameReturnsCorrectParameter() {
        CommandSchema schema = buildTwoParamSchema();

        Parameter p = schema.getParameter("param1");

        assertNotNull(p);
        assertEquals("param1", p.getName());
    }

    @Test
    public void getParameterForUnknownNameReturnsNull() {
        CommandSchema schema = buildTwoParamSchema();

        Parameter p = schema.getParameter("does-not-exist");

        assertNull(p);
    }

    @Test
    public void isLlmEnabledReflectsBuilderSetting() {
        CommandSchema enabled = CommandSchema.builder("cmd")
                .llmEnabled(true)
                .build();
        CommandSchema disabled = CommandSchema.builder("cmd2")
                .llmEnabled(false)
                .build();

        assertTrue(enabled.isLlmEnabled());
        assertFalse(disabled.isLlmEnabled());
    }

    @Test
    public void getLlmDescriptionReflectsBuilderSetting() {
        CommandSchema schema = CommandSchema.builder("cmd")
                .description("Console description")
                .llmEnabled(true)
                .llmDescription("LLM-specific description")
                .build();

        assertEquals("LLM-specific description", schema.getLlmDescription());
    }

    @Test
    public void getLlmDescriptionFallsBackToDescriptionWhenNotSet() {
        CommandSchema schema = CommandSchema.builder("cmd")
                .description("Console description")
                .llmEnabled(true)
                .build();

        assertEquals("Console description", schema.getLlmDescription());
    }

    @Test
    public void getLlmAliasReflectsBuilderSetting() {
        CommandSchema schema = CommandSchema.builder("cmd")
                .llmAlias("legacy-alias")
                .build();

        assertEquals("legacy-alias", schema.getLlmAlias());
    }

    @Test
    public void parseWithCorrectPositionalArgsReturnsParsedCommand() throws CommandParseException {
        CommandSchema schema = CommandSchema.builder("greet")
                .parameter(requiredStringParam("name", "Recipient name"))
                .allowsNamedParameters(false)
                .build();

        ParsedCommand result = schema.parse(new String[]{"Alice"});

        assertEquals("greet", result.getCommandName());
        assertEquals("Alice", result.getString("name"));
    }

    @Test(expected = CommandParseException.class)
    public void parseWithMissingRequiredArgThrowsCommandParseException() throws CommandParseException {
        CommandSchema schema = CommandSchema.builder("greet")
                .parameter(requiredStringParam("name", "Recipient name"))
                .allowsNamedParameters(false)
                .build();

        schema.parse(new String[]{});
    }

    @Test
    public void validateWithAllRequiredParamsSucceeds() throws CommandParseException {
        CommandSchema schema = CommandSchema.builder("cmd")
                .parameter(requiredStringParam("title", "Slide title"))
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("title", "My Title");

        schema.validate(params);
    }

    @Test(expected = CommandParseException.class)
    public void validateWithMissingRequiredParamThrowsCommandParseException() throws CommandParseException {
        CommandSchema schema = CommandSchema.builder("cmd")
                .parameter(requiredStringParam("title", "Slide title"))
                .build();

        schema.validate(new HashMap<>());
    }

    @Test
    public void generateHelpReturnsNonEmptyStringContainingCommandName() {
        CommandSchema schema = buildTwoParamSchema();

        String help = schema.generateHelp();

        assertNotNull(help);
        assertFalse(help.isEmpty());
        assertTrue(help.contains("test-cmd") || help.contains("TEST-CMD"));
    }

    @Test
    public void generateHelpIncludesParameterDescriptions() {
        CommandSchema schema = CommandSchema.builder("my-cmd")
                .description("Does things")
                .parameter(requiredStringParam("input", "The input value"))
                .build();

        String help = schema.generateHelp();

        assertTrue(help.contains("input"));
        assertTrue(help.contains("The input value"));
    }

    @Test
    public void builderWithNoParametersCreatesValidSchema() {
        CommandSchema schema = CommandSchema.builder("no-params")
                .description("A parameterless command")
                .build();

        assertNotNull(schema);
        assertEquals("no-params", schema.getName());
        assertTrue(schema.getParameters().isEmpty());
    }

    @Test
    public void toLLMToolSchemaReturnsJsonLikeStringForLlmEnabledSchema() {
        CommandSchema schema = CommandSchema.builder("add-shape")
                .description("Adds a shape to a slide")
                .llmEnabled(true)
                .llmDescription("Insert a new shape on the specified slide")
                .parameter(requiredStringParam("type", "Shape type"))
                .build();

        String json = schema.toLLMToolSchema();

        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("add-shape"));
        assertTrue(json.contains("\"description\""));
        assertTrue(json.contains("\"parameters\""));
        assertTrue(json.contains("\"properties\""));
    }

    @Test
    public void buildLlmToCanonicalParamMapIncludesCanonicalNamesAsSelfMappings() {
        CommandSchema schema = CommandSchema.builder("cmd")
                .parameter(requiredStringParam("slide", "Slide number"))
                .build();

        Map<String, String> mapping = schema.buildLlmToCanonicalParamMap();

        assertTrue(mapping.containsKey("slide"));
        assertEquals("slide", mapping.get("slide"));
    }

    @Test
    public void buildLlmToCanonicalParamMapIncludesLlmAliasWhenSet() {
        Parameter param = Parameter.builder("slide")
                .description("Slide number")
                .type(Parameter.ParameterType.INTEGER)
                .required(true)
                .llmName("slideNumber")
                .build();

        CommandSchema schema = CommandSchema.builder("cmd")
                .parameter(param)
                .build();

        Map<String, String> mapping = schema.buildLlmToCanonicalParamMap();

        assertTrue(mapping.containsKey("slideNumber"));
        assertEquals("slide", mapping.get("slideNumber"));
    }

    @Test
    public void generateSuggestionReturnsStringForWrongArgs() {
        CommandSchema schema = CommandSchema.builder("move")
                .parameter(requiredStringParam("direction", "Movement direction"))
                .allowsNamedParameters(false)
                .build();

        String suggestion = schema.generateSuggestion(new String[]{"not-a-valid-direction"});

        assertNotNull(suggestion);
    }
}
