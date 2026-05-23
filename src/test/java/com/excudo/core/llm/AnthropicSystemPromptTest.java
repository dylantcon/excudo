package com.excudo.core.llm;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for AnthropicSystemPrompt class.
 * Tests auto-generated prompt content and static reference sections.
 */
public class AnthropicSystemPromptTest {

    @Test
    public void testPowerPointEditorPromptExists() {
        assertNotNull("PowerPoint editor prompt should not be null",
                     AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT);
        assertFalse("PowerPoint editor prompt should not be empty",
                    AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT.trim().isEmpty());
    }

    @Test
    public void testGeneratePromptMatchesConstant() {
        String generated = AnthropicSystemPrompt.generatePrompt();
        assertNotNull(generated);
        assertEquals("Constant should equal generated prompt",
                    AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT, generated);
    }

    @Test
    public void testOOXMLStructureReferenceExists() {
        assertNotNull("OOXML structure reference should not be null",
                     AnthropicSystemPrompt.OOXML_STRUCTURE_REFERENCE);
        assertFalse("OOXML structure reference should not be empty",
                    AnthropicSystemPrompt.OOXML_STRUCTURE_REFERENCE.trim().isEmpty());
    }

    @Test
    public void testPromptContainsCommandNames() {
        String prompt = AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT;

        assertTrue("Should contain create command", prompt.contains("create"));
        assertTrue("Should contain content-edit command", prompt.contains("content-edit"));
        assertTrue("Should contain add-shape command", prompt.contains("add-shape"));
        assertTrue("Should contain add-animation command", prompt.contains("add-animation"));
    }

    @Test
    public void testPromptContainsCoreConcepts() {
        String prompt = AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT;

        assertTrue("Should mention SPID", prompt.contains("SPID"));
        assertTrue("Should mention EMU", prompt.contains("EMU"));
        assertTrue("Should mention layoutId", prompt.contains("layoutId"));
        assertTrue("Should contain JSON schema", prompt.contains("schemaVersion"));
        assertTrue("Should contain actions", prompt.contains("actions"));
    }

    @Test
    public void testPromptContainsRules() {
        String prompt = AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT;

        assertTrue("Should mention JSON-only response", prompt.contains("JSON"));
        assertTrue("Should mention exact SPIDs", prompt.contains("exact SPIDs"));
        assertTrue("Should mention direction constraints", prompt.contains("direction"));
    }

    @Test
    public void testOOXMLReferenceContainsStructure() {
        String reference = AnthropicSystemPrompt.OOXML_STRUCTURE_REFERENCE;

        assertTrue("Should contain timing structure", reference.contains("p:timing"));
        assertTrue("Should contain animation effects", reference.contains("p:animEffect"));
        assertTrue("Should contain build list", reference.contains("p:bldLst"));
        assertTrue("Should contain target elements", reference.contains("p:tgtEl"));
        assertTrue("Should contain shape targeting", reference.contains("p:spTgt"));
    }

    @Test
    public void testOOXMLReferenceContainsAnimationRules() {
        String reference = AnthropicSystemPrompt.OOXML_STRUCTURE_REFERENCE;

        assertTrue("Should contain timing node ID rules", reference.contains("timing node IDs"));
        assertTrue("Should contain click trigger rules", reference.contains("delay=\"indefinite\""));
        assertTrue("Should contain visibility rules", reference.contains("visibility"));
        assertTrue("Should contain build list rules", reference.contains("grpId"));
    }
}
