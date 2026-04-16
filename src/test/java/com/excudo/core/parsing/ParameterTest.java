package com.excudo.core.parsing;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests Parameter builder, validation, and help generation.
 */
public class ParameterTest {

    @Test
    public void builderCreatesBasicParameter() {
        Parameter param = Parameter.builder("slide")
            .required(true)
            .type(Parameter.ParameterType.INTEGER)
            .description("Slide number")
            .build();

        assertEquals("slide", param.getName());
        assertTrue(param.isRequired());
        assertEquals(Parameter.ParameterType.INTEGER, param.getType());
        assertEquals("Slide number", param.getDescription());
    }

    @Test
    public void optionalParameterWithDefault() {
        Parameter param = Parameter.builder("width")
            .required(false)
            .defaultValue("1280")
            .type(Parameter.ParameterType.INTEGER)
            .build();

        assertFalse(param.isRequired());
        assertEquals("1280", param.getDefaultValue());
    }

    @Test
    public void validValuesConstraint() {
        Parameter param = Parameter.builder("alignment")
            .type(Parameter.ParameterType.STRING)
            .validValues("left", "center", "right")
            .build();

        Set<String> valid = param.getValidValues();
        assertNotNull(valid);
        assertTrue(valid.contains("left"));
        assertTrue(valid.contains("center"));
        assertTrue(valid.contains("right"));
        assertFalse(valid.contains("justify"));
    }

    @Test
    public void isValidValueChecksConstraints() {
        Parameter param = Parameter.builder("type")
            .type(Parameter.ParameterType.STRING)
            .validValues("rect", "ellipse", "triangle")
            .build();

        assertTrue(param.isValidValue("rect"));
        assertTrue(param.isValidValue("ellipse"));
        assertFalse(param.isValidValue("hexagon"));
    }

    @Test
    public void isValidValueAcceptsAnythingWhenNoConstraints() {
        Parameter param = Parameter.builder("text")
            .type(Parameter.ParameterType.STRING)
            .build();

        assertTrue(param.isValidValue("anything"));
        assertTrue(param.isValidValue(""));
    }

    @Test
    public void variableLengthParameter() {
        Parameter param = Parameter.builder("shapes")
            .variableLength(true)
            .type(Parameter.ParameterType.STRING)
            .build();

        assertTrue(param.isVariableLength());
    }

    @Test
    public void llmNameOverride() {
        Parameter param = Parameter.builder("spid")
            .llmName("shape_id")
            .type(Parameter.ParameterType.INTEGER)
            .build();

        assertEquals("shape_id", param.getLlmName());
        assertEquals("shape_id", param.getEffectiveLlmName());
    }

    @Test
    public void effectiveLlmNameFallsBackToName() {
        Parameter param = Parameter.builder("slide")
            .type(Parameter.ParameterType.INTEGER)
            .build();

        assertNull(param.getLlmName());
        assertEquals("slide", param.getEffectiveLlmName());
    }

    @Test
    public void generateHelpContainsTypeInfo() {
        Parameter param = Parameter.builder("count")
            .required(true)
            .type(Parameter.ParameterType.INTEGER)
            .description("Number of items")
            .build();

        String help = param.generateHelp();
        assertNotNull(help);
        assertTrue(help.contains("count"));
    }

    @Test
    public void typeDescriptionReturnsReadableString() {
        for (Parameter.ParameterType type : Parameter.ParameterType.values()) {
            Parameter param = Parameter.builder("test")
                .type(type)
                .build();
            assertNotNull(param.getTypeDescription());
            assertTrue(param.getTypeDescription().length() > 0);
        }
    }

    @Test
    public void allParameterTypesExist() {
        Parameter.ParameterType[] types = Parameter.ParameterType.values();
        assertTrue("Should have multiple parameter types", types.length >= 3);
    }
}
