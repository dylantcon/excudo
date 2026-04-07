package com.excudo.console.utils;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for ConsoleOutputFormatter static utility methods.
 * Covers help text generation, shape list formatting, animation formatting,
 * and layout list formatting.
 */
public class ConsoleOutputFormatterTest {

    // ========== getCommandHelp ==========

    @Test
    public void getCommandHelp_knownCommand_returnsHelpString() {
        String help = ConsoleOutputFormatter.getCommandHelp("create");
        assertNotNull("Should return non-null help for known command", help);
        assertFalse("Should not return empty string", help.isEmpty());
        assertFalse("Should not be the unknown-command fallback", help.startsWith("Unknown command:"));
    }

    @Test
    public void getCommandHelp_unknownCommand_returnsUnknownMessage() {
        String help = ConsoleOutputFormatter.getCommandHelp("xyz-not-a-command");
        assertNotNull("Should return non-null for unknown command", help);
        assertTrue("Should start with 'Unknown command:'", help.startsWith("Unknown command:"));
        assertTrue("Should include the command name", help.contains("xyz-not-a-command"));
    }

    @Test
    public void getCommandHelp_isCaseInsensitive() {
        String lowerHelp = ConsoleOutputFormatter.getCommandHelp("load");
        String upperHelp = ConsoleOutputFormatter.getCommandHelp("LOAD");
        assertEquals("Case should not matter for command lookup", lowerHelp, upperHelp);
    }

    // ========== generateHelpText ==========

    @Test
    public void generateHelpText_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateHelpText();
        assertNotNull("Should return non-null list", help);
        assertFalse("Should return non-empty list", help.isEmpty());
    }

    // ========== topic help methods ==========

    @Test
    public void generateSlideHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateSlideHelp();
        assertNotNull(help);
        assertFalse("generateSlideHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateShapeHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateShapeHelp();
        assertNotNull(help);
        assertFalse("generateShapeHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateAnimationHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateAnimationHelp();
        assertNotNull(help);
        assertFalse("generateAnimationHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateThemeHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateThemeHelp();
        assertNotNull(help);
        assertFalse("generateThemeHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateDebugHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateDebugHelp();
        assertNotNull(help);
        assertFalse("generateDebugHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateAIHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateAIHelp();
        assertNotNull(help);
        assertFalse("generateAIHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateSessionHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateSessionHelp();
        assertNotNull(help);
        assertFalse("generateSessionHelp should return non-empty list", help.isEmpty());
    }

    @Test
    public void generateGettingStartedHelp_returnsNonEmptyList() {
        List<String> help = ConsoleOutputFormatter.generateGettingStartedHelp();
        assertNotNull(help);
        assertFalse("generateGettingStartedHelp should return non-empty list", help.isEmpty());
    }

    // ========== formatShapeList ==========

    @Test
    public void formatShapeList_emptyList_returnsHeaderOnly() {
        List<SlideShape> shapes = Collections.emptyList();
        List<String> result = ConsoleOutputFormatter.formatShapeList(shapes, 1);

        assertNotNull(result);
        // The formatter outputs an empty line and then the header line
        assertEquals("Empty shape list should produce 2 lines (blank + header)", 2, result.size());
        assertTrue("Second line should describe slide number and shape count",
            result.get(1).contains("slide 1") && result.get(1).contains("0 shapes"));
    }

    @Test
    public void formatShapeList_withShapes_includesSpidAndType() {
        ShapeGeometry geom = new ShapeGeometry(100L, 200L, 300L, 400L);
        SlideShape shape = new SlideShape(42, "TestShape", SlideShape.ShapeType.RECTANGLE,
            "Hello", geom, null);

        List<SlideShape> shapes = new ArrayList<>();
        shapes.add(shape);

        List<String> result = ConsoleOutputFormatter.formatShapeList(shapes, 3);

        assertNotNull(result);
        assertTrue("Result should have more than 2 lines when shapes are present", result.size() > 2);

        String combined = String.join("\n", result);
        assertTrue("Output should contain the SPID value 42", combined.contains("42"));
        assertTrue("Output should reference slide number 3",
            result.stream().anyMatch(line -> line.contains("slide 3")));
    }

    @Test
    public void formatShapeList_withShapes_includesShapeType() {
        ShapeGeometry geom = new ShapeGeometry(0L, 0L, 914400L, 914400L);
        SlideShape shape = new SlideShape(7, "EllipseShape", SlideShape.ShapeType.ELLIPSE,
            null, geom, null);

        List<SlideShape> shapes = new ArrayList<>();
        shapes.add(shape);

        List<String> result = ConsoleOutputFormatter.formatShapeList(shapes, 2);
        String combined = String.join("\n", result);

        assertTrue("Output should contain the shape type", combined.contains("ELLIPSE"));
    }

    // ========== formatAnimations ==========

    @Test
    public void formatAnimations_emptyList_returnsNoAnimationsMessage() {
        List<AnimationBinding> bindings = Collections.emptyList();
        List<String> result = ConsoleOutputFormatter.formatAnimations(bindings);

        assertNotNull(result);
        assertFalse("Result should not be empty", result.isEmpty());

        String combined = String.join("\n", result);
        assertTrue("Should report no animations found",
            combined.contains("No animations found"));
    }

    @Test
    public void formatAnimations_withBindings_groupsByTrigger() {
        AnimationBinding binding = new AnimationBinding(
            5, AnimationType.FADE, "in", "500", "0");

        List<AnimationBinding> bindings = new ArrayList<>();
        bindings.add(binding);

        List<String> result = ConsoleOutputFormatter.formatAnimations(bindings);

        assertNotNull(result);
        assertFalse("Should produce output for non-empty binding list", result.isEmpty());

        String combined = String.join("\n", result);
        assertTrue("Output should reference the target SPID",
            combined.contains("5") || combined.contains("SPID 5"));
    }

    // ========== formatLayoutList ==========

    @Test
    public void formatLayoutList_includesLayoutNames() {
        LayoutInfo layout1 = new LayoutInfo(
            "slideLayout1", "Title Slide", "ppt/slideLayouts/slideLayout1.xml",
            true, false, true, "Title and subtitle layout");
        LayoutInfo layout2 = new LayoutInfo(
            "slideLayout2", "Title and Content", "ppt/slideLayouts/slideLayout2.xml",
            true, true, false, "Title with content area");

        List<LayoutInfo> layouts = new ArrayList<>();
        layouts.add(layout1);
        layouts.add(layout2);

        List<String> result = ConsoleOutputFormatter.formatLayoutList(layouts);

        assertNotNull(result);
        assertFalse("Result should not be empty", result.isEmpty());

        String combined = String.join("\n", result);
        assertTrue("Output should contain first layout name", combined.contains("Title Slide"));
        assertTrue("Output should contain second layout name", combined.contains("Title and Content"));
    }

    @Test
    public void formatLayoutList_emptyList_returnsHeaderOnly() {
        List<LayoutInfo> layouts = Collections.emptyList();
        List<String> result = ConsoleOutputFormatter.formatLayoutList(layouts);

        assertNotNull(result);
        assertFalse("Should return at least the header lines", result.isEmpty());

        String combined = String.join("\n", result);
        assertTrue("Header should indicate zero layouts", combined.contains("0 total"));
    }
}
