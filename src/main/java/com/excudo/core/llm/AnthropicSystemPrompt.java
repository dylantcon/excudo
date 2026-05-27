package com.excudo.core.llm;

import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * System prompt for PowerPoint OOXML-aware LLM interactions.
 * Command reference is auto-generated from CommandSchema definitions.
 */
public class AnthropicSystemPrompt {

    /**
     * Generate the full system prompt with auto-generated command reference.
     */
    public static String generatePrompt() {
        return PROMPT_HEADER + "\n\n" + generateCommandReference() + "\n" + PROMPT_FOOTER;
    }

    /**
     * Human-readable per-command reference for the system prompt.
     * One line per LLM-enabled command: name, parameter list with types and
     * defaults/enums/optionality markers, then the LLM description.
     */
    private static String generateCommandReference() {
        StringBuilder sb = new StringBuilder();
        sb.append("COMMANDS:\n");

        List<CommandSchema> sorted = new ArrayList<>(CommandRegistry.getAllSchemas().values());
        sorted.sort(Comparator.comparing(CommandSchema::getName));

        for (CommandSchema schema : sorted) {
            if (!schema.isLlmEnabled()) continue;

            sb.append("- ").append(schema.getName()).append(": ");

            boolean firstParam = true;
            for (Parameter<?> p : schema.getParameters()) {
                if (!firstParam) sb.append(", ");
                firstParam = false;

                String llmName = p.getEffectiveLlmName();
                sb.append(llmName).append("(").append(promptTypeOf(p.getType())).append(")");

                if (p.getValidValues() != null && !p.getValidValues().isEmpty()) {
                    List<String> vals = new ArrayList<>(p.getValidValues());
                    Collections.sort(vals);
                    sb.append("[").append(String.join("|", vals)).append("]");
                } else if (p.getDefaultValue() != null) {
                    sb.append("=").append(p.getDefaultValue());
                }
                if (!p.isRequired()) sb.append("?");
            }

            String llmDesc = schema.getLlmDescription();
            if (llmDesc != null && !llmDesc.isEmpty()) {
                sb.append(" -- ").append(llmDesc);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String promptTypeOf(Parameter.ParameterType type) {
        return switch (type) {
            case INTEGER, SLIDE_NUMBER, SPID -> "integer";
            case DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    private static final String PROMPT_HEADER = """
        You are a PowerPoint editor. Respond with ONLY valid JSON, no prose.

        {"schemaVersion":"1.0","actions":[{"type":"<command>","parameters":{...}}]}
        """;

    private static final String PROMPT_FOOTER = """
        RULES:
        - Use exact SPIDs from context. SPID 1 is structural; SPID 2+ from layout.
        - Edit existing shapes (edit-content) before creating new ones (add-shape).
        - Use layoutId from context. EMU: 914400/inch, slide=9144000x6858000.
        - Animations require direction [in|out|emphasis] and trigger [on-click|with-previous|after-previous].
        - CUSTOM LAYOUTS: duplicate-layout to clone, then add-placeholder/remove-placeholder to customize.
          Create slides from custom layouts for reuse across multiple slides.
        """;

    // Keep legacy constant for backward compatibility (references generatePrompt())
    public static final String POWERPOINT_EDITOR_PROMPT = generatePrompt();

    /**
     * Compact system prompt for local/small models (e.g. Ollama 14B).
     * ~180 tokens -- fits within 8K context with room for response.
     * Command reference is NOT appended; only core commands are listed inline.
     */
    public static final String COMPACT_EDITOR_PROMPT = """
        You are a PowerPoint editor. Respond with ONLY valid JSON, no explanation.

        Schema:
        {"schemaVersion":"1.0","actions":[{"type":"<command>","parameters":{...}}]}

        Core commands:
        - create: slideNumber(int), title(string), layoutId(string), content(string)
        - edit-content: slideNumber(int), spid(int), content(string)
        - add-shape: slideNumber(int), shapeType(string), x/y/width/height(int EMUs), content(string)
        - add-animation: slideNumber(int), targetSpid(int), animationType(string), direction(string)
        - delete: slideNumber(int)
        - copy: sourceSlide(int), targetPosition(int)

        EMUs: 1 inch = 914400. Slide = 9144000 x 6858000.

        Text supports markdown: **bold**, *italic*, - bullets, 1. numbered lists.
        Indent bullets 2 spaces per nesting level. Use \\n for line breaks.

        Context shows each slide's shapes as SPID<n>(role)="text".
        To edit a shape, use its SPID number as the spid parameter.
        Example: SPID2(title)="Hello" means spid:2 targets that title shape.
        Use exact SPIDs from context. JSON only.
        """;
    
    /**
     * Get a prompt explaining OOXML structure for direct XML manipulation
     */
    public static final String OOXML_STRUCTURE_REFERENCE = """
        ## OOXML Animation Timing Structure Reference
        
        ### Basic Animation with Visibility Control:
        ```xml
        <p:timing>
          <p:tnLst>
            <p:par>
              <p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot">
                <p:childTnLst>
                  <p:seq concurrent="1" nextAc="seek">
                    <p:cTn id="2" dur="indefinite" nodeType="mainSeq">
                      <p:childTnLst>
                        <p:par>
                          <p:cTn id="3" fill="hold">
                            <p:stCondLst>
                              <p:cond delay="indefinite"/>
                            </p:stCondLst>
                            <p:childTnLst>
                              <p:par>
                                <p:cTn id="4" fill="hold">
                                  <p:childTnLst>
                                    <p:set>
                                      <p:cBhvr>
                                        <p:cTn id="5" dur="1" fill="hold">
                                          <p:stCondLst>
                                            <p:cond delay="0"/>
                                          </p:stCondLst>
                                        </p:cTn>
                                        <p:tgtEl>
                                          <p:spTgt spid="[shapeId]"/>
                                        </p:tgtEl>
                                        <p:attrNameLst>
                                          <p:attrName>style.visibility</p:attrName>
                                        </p:attrNameLst>
                                      </p:cBhvr>
                                      <p:to>
                                        <p:strVal val="visible"/>
                                      </p:to>
                                    </p:set>
                                    <p:animEffect transition="in" filter="fade">
                                      <p:cBhvr>
                                        <p:cTn id="6" dur="500"/>
                                        <p:tgtEl>
                                          <p:spTgt spid="[shapeId]"/>
                                        </p:tgtEl>
                                      </p:cBhvr>
                                    </p:animEffect>
                                  </p:childTnLst>
                                </p:cTn>
                              </p:par>
                            </p:childTnLst>
                          </p:cTn>
                        </p:par>
                      </p:childTnLst>
                    </p:cTn>
                  </p:seq>
                </p:childTnLst>
              </p:cTn>
            </p:par>
          </p:tnLst>
        </p:timing>
        ```
        
        ### Animation Build List:
        ```xml
        <p:bldLst>
          <p:bldP spid="[shapeId]" grpId="[groupId]" build="p"/>
        </p:bldLst>
        ```
        
        ### Key OOXML Rules:
        - Each animation needs unique timing node IDs
        - Click triggers use stCondLst with delay="indefinite"
        - Entrance animations set visibility to "visible"
        - Exit animations set visibility to "hidden"
        - Build list tracks animation order by grpId
        - SPID must match existing shapes in slide
        """;
}