package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.NodeShapeHint;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShapeHintMapperTest {

    @Test
    public void testAllHintsMapped() {
        for (NodeShapeHint hint : NodeShapeHint.values()) {
            String preset = ShapeHintMapper.toOoxmlPreset(hint);
            assertNotNull("Hint " + hint + " should map to a preset", preset);
            assertFalse("Preset should not be empty for " + hint, preset.isEmpty());
        }
    }

    @Test
    public void testSpecificMappings() {
        assertEquals("rect", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.RECT));
        assertEquals("roundRect", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.ROUND_RECT));
        assertEquals("flowChartDecision", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.DIAMOND));
        assertEquals("ellipse", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.CIRCLE));
        assertEquals("roundRect", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.STADIUM));
        assertEquals("flowChartPredefinedProcess", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.SUBROUTINE));
        assertEquals("can", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.CYLINDER));
        assertEquals("homePlate", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.ASYMMETRIC));
        assertEquals("parallelogram", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.PARALLELOGRAM));
        assertEquals("hexagon", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.HEXAGON));
        assertEquals("trapezoid", ShapeHintMapper.toOoxmlPreset(NodeShapeHint.TRAPEZOID));
    }
}
