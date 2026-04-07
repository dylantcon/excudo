package com.excudo.core.commands;

import com.excudo.core.model.AnimationType;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit tests for AnimationParameterRequirement.
 *
 * Covers requirements lookup, parameter validation, help text generation,
 * ValidationResult factory methods, and trigger constant values.
 */
public class AnimationParameterRequirementTest {

    // ========== getRequirements(AnimationType) ==========

    @Test
    public void getRequirements_fade_returnsNonNull() {
        AnimationParameterRequirement.AnimationRequirement result =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);
        assertNotNull(result);
    }

    @Test
    public void getRequirements_spin_returnsNonNull() {
        AnimationParameterRequirement.AnimationRequirement result =
                AnimationParameterRequirement.getRequirements(AnimationType.SPIN);
        assertNotNull(result);
    }

    @Test
    public void getRequirements_nullAnimationType_returnsNull() {
        AnimationParameterRequirement.AnimationRequirement result =
                AnimationParameterRequirement.getRequirements((AnimationType) null);
        assertNull(result);
    }

    // ========== getRequirements(String) ==========

    @Test
    public void getRequirements_stringFade_returnsNonNull() {
        AnimationParameterRequirement.AnimationRequirement result =
                AnimationParameterRequirement.getRequirements("fade");
        assertNotNull(result);
    }

    // ========== getAllRequirements() ==========

    @Test
    public void getAllRequirements_returnsNonEmptyMap() {
        Map<AnimationType, AnimationParameterRequirement.AnimationRequirement> all =
                AnimationParameterRequirement.getAllRequirements();
        assertNotNull(all);
        assertFalse(all.isEmpty());
    }

    // ========== FADE parameter content ==========

    @Test
    public void fadeRequirement_hasDirectionAndTriggerParams() {
        AnimationParameterRequirement.AnimationRequirement req =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);
        List<String> paramNames = req.getParameters().stream()
                .map(AnimationParameterRequirement.ParameterDefinition::getName)
                .collect(Collectors.toList());

        assertTrue("Expected direction parameter for FADE", paramNames.contains("direction"));
        assertTrue("Expected trigger parameter for FADE",   paramNames.contains("trigger"));
    }

    @Test
    public void fadeRequirement_directionParam_hasInAndOutValidValues() {
        AnimationParameterRequirement.AnimationRequirement req =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);

        AnimationParameterRequirement.ParameterDefinition directionParam = req.getParameters()
                .stream()
                .filter(p -> "direction".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull("direction parameter not found", directionParam);
        List<String> validValues = directionParam.getValidValues();
        assertNotNull(validValues);
        assertTrue("Expected 'in' in direction valid values",  validValues.contains("in"));
        assertTrue("Expected 'out' in direction valid values", validValues.contains("out"));
    }

    @Test
    public void triggerParam_hasAllThreeTriggerValidValues() {
        AnimationParameterRequirement.AnimationRequirement req =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);

        AnimationParameterRequirement.ParameterDefinition triggerParam = req.getParameters()
                .stream()
                .filter(p -> "trigger".equals(p.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull("trigger parameter not found", triggerParam);
        List<String> validValues = triggerParam.getValidValues();
        assertNotNull(validValues);
        assertTrue(validValues.contains(AnimationParameterRequirement.TRIGGER_ON_CLICK));
        assertTrue(validValues.contains(AnimationParameterRequirement.TRIGGER_WITH_PREVIOUS));
        assertTrue(validValues.contains(AnimationParameterRequirement.TRIGGER_AFTER_PREVIOUS));
    }

    // ========== validateArguments ==========

    @Test
    public void validateArguments_validFadeArgs_returnsSuccess() {
        // FADE required params in order: slide#, spid, direction, trigger
        String[] args = {"1", "3", "in", AnimationParameterRequirement.TRIGGER_ON_CLICK};
        AnimationParameterRequirement.ValidationResult result =
                AnimationParameterRequirement.validateArguments(AnimationType.FADE, args);
        assertTrue(result.isSuccess());
    }

    @Test
    public void validateArguments_nullAnimationType_returnsError() {
        String[] args = {"1", "3", "in", AnimationParameterRequirement.TRIGGER_ON_CLICK};
        AnimationParameterRequirement.ValidationResult result =
                AnimationParameterRequirement.validateArguments(null, args);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ========== generateHelpText ==========

    @Test
    public void generateHelpText_fade_returnsNonEmptyString() {
        String help = AnimationParameterRequirement.generateHelpText(AnimationType.FADE);
        assertNotNull(help);
        assertFalse(help.isEmpty());
    }

    // ========== ValidationResult factory methods ==========

    @Test
    public void validationResult_success_isSuccessTrue() {
        AnimationParameterRequirement.ValidationResult result =
                AnimationParameterRequirement.ValidationResult.success();
        assertTrue(result.isSuccess());
    }

    @Test
    public void validationResult_error_isSuccessFalse() {
        AnimationParameterRequirement.ValidationResult result =
                AnimationParameterRequirement.ValidationResult.error("test error");
        assertFalse(result.isSuccess());
    }

    @Test
    public void validationResult_error_preservesMessage() {
        String message = "missing required parameter";
        AnimationParameterRequirement.ValidationResult result =
                AnimationParameterRequirement.ValidationResult.error(message);
        assertEquals(message, result.getErrorMessage());
    }

    // ========== Trigger constants ==========

    @Test
    public void triggerConstants_haveExpectedValues() {
        assertEquals("on-click",       AnimationParameterRequirement.TRIGGER_ON_CLICK);
        assertEquals("with-previous",  AnimationParameterRequirement.TRIGGER_WITH_PREVIOUS);
        assertEquals("after-previous", AnimationParameterRequirement.TRIGGER_AFTER_PREVIOUS);
    }

    // ========== ParameterDefinition.isValidValue ==========

    @Test
    public void parameterDefinition_isValidValue_returnsTrue_forValidInput() {
        AnimationParameterRequirement.AnimationRequirement req =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);
        AnimationParameterRequirement.ParameterDefinition directionParam = req.getParameters()
                .stream()
                .filter(p -> "direction".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("direction param not found"));

        assertTrue(directionParam.isValidValue("in"));
    }

    @Test
    public void parameterDefinition_isValidValue_returnsFalse_forInvalidInput() {
        AnimationParameterRequirement.AnimationRequirement req =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);
        AnimationParameterRequirement.ParameterDefinition directionParam = req.getParameters()
                .stream()
                .filter(p -> "direction".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("direction param not found"));

        assertFalse(directionParam.isValidValue("diagonal"));
    }

    // ========== AnimationRequirement.getRequiredParameterCount ==========

    @Test
    public void animationRequirement_getRequiredParameterCount_matchesRequiredFields() {
        AnimationParameterRequirement.AnimationRequirement req =
                AnimationParameterRequirement.getRequirements(AnimationType.FADE);

        long expectedRequired = req.getParameters().stream()
                .filter(AnimationParameterRequirement.ParameterDefinition::isRequired)
                .count();

        assertEquals(expectedRequired, req.getRequiredParameterCount());
        assertTrue("FADE should have at least one required parameter",
                req.getRequiredParameterCount() > 0);
    }

    // ========== AnimationType classification sanity checks ==========

    @Test
    public void animationType_fade_isNotEmphasis() {
        assertFalse(AnimationType.FADE.isEmphasis());
    }

    @Test
    public void animationType_spin_isEmphasis() {
        assertTrue(AnimationType.SPIN.isEmphasis());
    }

    @Test
    public void animationType_motionCustom_isMotionPath() {
        assertTrue(AnimationType.MOTION_CUSTOM.isMotionPath());
    }

    @Test
    public void animationType_parseType_fadeString_returnsFade() {
        assertEquals(AnimationType.FADE, AnimationType.parseType("fade"));
    }
}
