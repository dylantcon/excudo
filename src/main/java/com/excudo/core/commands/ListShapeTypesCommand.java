package com.excudo.core.commands;

import com.excudo.core.model.SlideShape;

/**
 * Lists all available shape types, grouped by category.
 * Sessionless -- works without a loaded presentation.
 */
public class ListShapeTypesCommand implements Command {

    private final CommandDisplay display;
    private boolean executed = false;

    public ListShapeTypesCommand(CommandDisplay display) {
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.display = display;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        }

        display.displayMessage("");
        display.displayMessage("Available Shape Types (" + countPresetTypes() + " with OOXML presets):");

        printCategory("BASIC SHAPES",
            SlideShape.ShapeType.RECTANGLE, SlideShape.ShapeType.ELLIPSE,
            SlideShape.ShapeType.TRIANGLE, SlideShape.ShapeType.DIAMOND,
            SlideShape.ShapeType.PARALLELOGRAM, SlideShape.ShapeType.TRAPEZOID,
            SlideShape.ShapeType.PENTAGON, SlideShape.ShapeType.HEXAGON,
            SlideShape.ShapeType.HEPTAGON, SlideShape.ShapeType.OCTAGON,
            SlideShape.ShapeType.DECAGON, SlideShape.ShapeType.DODECAGON);

        printCategory("RECTANGLES",
            SlideShape.ShapeType.ROUNDED_RECTANGLE,
            SlideShape.ShapeType.SNIP_SINGLE_CORNER_RECTANGLE,
            SlideShape.ShapeType.SNIP_SAME_SIDE_CORNER_RECTANGLE,
            SlideShape.ShapeType.SNIP_DIAGONAL_CORNER_RECTANGLE,
            SlideShape.ShapeType.ROUND_SINGLE_CORNER_RECTANGLE,
            SlideShape.ShapeType.ROUND_SAME_SIDE_CORNER_RECTANGLE,
            SlideShape.ShapeType.ROUND_DIAGONAL_CORNER_RECTANGLE,
            SlideShape.ShapeType.SNIP_ROUND_RECTANGLE);

        printCategory("ARROWS",
            SlideShape.ShapeType.RIGHT_ARROW, SlideShape.ShapeType.LEFT_ARROW,
            SlideShape.ShapeType.UP_ARROW, SlideShape.ShapeType.DOWN_ARROW,
            SlideShape.ShapeType.LEFT_RIGHT_ARROW, SlideShape.ShapeType.UP_DOWN_ARROW,
            SlideShape.ShapeType.QUAD_ARROW, SlideShape.ShapeType.BENT_ARROW,
            SlideShape.ShapeType.UTURN_ARROW, SlideShape.ShapeType.CURVED_RIGHT_ARROW,
            SlideShape.ShapeType.CURVED_LEFT_ARROW, SlideShape.ShapeType.STRIPED_RIGHT_ARROW,
            SlideShape.ShapeType.NOTCHED_RIGHT_ARROW, SlideShape.ShapeType.CIRCULAR_ARROW);

        printCategory("STARS & EXPLOSIONS",
            SlideShape.ShapeType.STAR_4_POINTS, SlideShape.ShapeType.STAR_5_POINTS,
            SlideShape.ShapeType.STAR_6_POINTS, SlideShape.ShapeType.STAR_8_POINTS,
            SlideShape.ShapeType.STAR_10_POINTS, SlideShape.ShapeType.STAR_12_POINTS,
            SlideShape.ShapeType.STAR_16_POINTS, SlideShape.ShapeType.STAR_24_POINTS,
            SlideShape.ShapeType.STAR_32_POINTS,
            SlideShape.ShapeType.EXPLOSION_8_POINTS, SlideShape.ShapeType.EXPLOSION_14_POINTS);

        printCategory("CALLOUTS",
            SlideShape.ShapeType.RECTANGULAR_CALLOUT, SlideShape.ShapeType.ROUNDED_RECTANGULAR_CALLOUT,
            SlideShape.ShapeType.OVAL_CALLOUT, SlideShape.ShapeType.CLOUD_CALLOUT,
            SlideShape.ShapeType.WEDGE_RECT_CALLOUT, SlideShape.ShapeType.WEDGE_ROUND_RECT_CALLOUT,
            SlideShape.ShapeType.WEDGE_ELLIPSE_CALLOUT);

        printCategory("FLOWCHART",
            SlideShape.ShapeType.FLOWCHART_PROCESS, SlideShape.ShapeType.FLOWCHART_DECISION,
            SlideShape.ShapeType.FLOWCHART_INPUT_OUTPUT, SlideShape.ShapeType.FLOWCHART_DOCUMENT,
            SlideShape.ShapeType.FLOWCHART_MULTIDOCUMENT, SlideShape.ShapeType.FLOWCHART_TERMINATOR,
            SlideShape.ShapeType.FLOWCHART_PREPARATION, SlideShape.ShapeType.FLOWCHART_CONNECTOR,
            SlideShape.ShapeType.FLOWCHART_ALTERNATE_PROCESS, SlideShape.ShapeType.FLOWCHART_OFFPAGE_CONNECTOR);

        printCategory("SPECIAL SHAPES",
            SlideShape.ShapeType.LINE, SlideShape.ShapeType.ARC,
            SlideShape.ShapeType.HEART, SlideShape.ShapeType.LIGHTNING_BOLT,
            SlideShape.ShapeType.SUN, SlideShape.ShapeType.MOON,
            SlideShape.ShapeType.CLOUD, SlideShape.ShapeType.SMILEY_FACE,
            SlideShape.ShapeType.CROSS, SlideShape.ShapeType.DONUT,
            SlideShape.ShapeType.FRAME, SlideShape.ShapeType.CUBE,
            SlideShape.ShapeType.CAN, SlideShape.ShapeType.BEVEL,
            SlideShape.ShapeType.FOLDED_CORNER, SlideShape.ShapeType.CHEVRON);

        printCategory("MATH",
            SlideShape.ShapeType.PLUS, SlideShape.ShapeType.MINUS,
            SlideShape.ShapeType.MULTIPLY, SlideShape.ShapeType.DIVIDE,
            SlideShape.ShapeType.EQUAL, SlideShape.ShapeType.NOT_EQUAL);

        printCategory("BANNERS & RIBBONS",
            SlideShape.ShapeType.RIBBON, SlideShape.ShapeType.RIBBON_2,
            SlideShape.ShapeType.ELLIPSE_RIBBON, SlideShape.ShapeType.ELLIPSE_RIBBON_2,
            SlideShape.ShapeType.VERTICAL_SCROLL, SlideShape.ShapeType.HORIZONTAL_SCROLL);

        printCategory("CONNECTORS",
            SlideShape.ShapeType.STRAIGHT_CONNECTOR,
            SlideShape.ShapeType.ELBOW_CONNECTOR,
            SlideShape.ShapeType.CURVED_CONNECTOR);

        printCategory("ACTION BUTTONS",
            SlideShape.ShapeType.ACTION_BUTTON_BLANK, SlideShape.ShapeType.ACTION_BUTTON_HOME,
            SlideShape.ShapeType.ACTION_BUTTON_HELP, SlideShape.ShapeType.ACTION_BUTTON_INFORMATION,
            SlideShape.ShapeType.ACTION_BUTTON_BACK_OR_PREVIOUS, SlideShape.ShapeType.ACTION_BUTTON_FORWARD_OR_NEXT);

        display.displayMessage("");
        display.displayMessage("Usage: add-shape <slide> <TYPE> \"text\" <x> <y> <w> <h> [--fill-color HEX] [--line-color HEX]");

        executed = true;
    }

    private void printCategory(String name, SlideShape.ShapeType... types) {
        display.displayMessage("");
        display.displayMessage("  " + name + ":");
        StringBuilder line = new StringBuilder("    ");
        for (int i = 0; i < types.length; i++) {
            String typeName = types[i].name();
            if (line.length() + typeName.length() + 2 > 90) {
                display.displayMessage(line.toString());
                line = new StringBuilder("    ");
            }
            line.append(typeName);
            if (i < types.length - 1) line.append(", ");
        }
        if (line.length() > 4) display.displayMessage(line.toString());
    }

    private int countPresetTypes() {
        int count = 0;
        for (SlideShape.ShapeType t : SlideShape.ShapeType.values()) {
            if (t.hasOoxmlPreset()) count++;
        }
        return count;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", "Read-only command");
    }

    @Override
    public boolean canUndo() { return false; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() { return "List Shape Types"; }
}
