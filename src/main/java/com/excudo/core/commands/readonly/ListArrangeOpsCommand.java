package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

/**
 * Lists all available arrange operations.
 * Sessionless -- works without a loaded presentation.
 */
public class ListArrangeOpsCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("List all available arrange operations")
        .example("list-arrange-ops")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ListArrangeOpsCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ListArrangeOpsCommand(ctx.requireDisplay());
    }


    private final CommandDisplay display;
    private boolean executed = false;

    public ListArrangeOpsCommand(CommandDisplay display) {
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
        display.displayMessage("Available Arrange Operations:");

        display.displayMessage("");
        display.displayMessage("  ALIGNMENT:");
        display.displayMessage("    align-left        Align all shapes to leftmost edge");
        display.displayMessage("    align-right       Align all shapes to rightmost edge");
        display.displayMessage("    align-top         Align all shapes to topmost edge");
        display.displayMessage("    align-bottom      Align all shapes to bottommost edge");
        display.displayMessage("    align-center-h    Align centers horizontally");
        display.displayMessage("    align-center-v    Align centers vertically");

        display.displayMessage("");
        display.displayMessage("  DISTRIBUTION (requires 3+ shapes):");
        display.displayMessage("    distribute-h      Equal horizontal spacing between shapes");
        display.displayMessage("    distribute-v      Equal vertical spacing between shapes");

        display.displayMessage("");
        display.displayMessage("  MATCH (uses anchor or first shape as reference):");
        display.displayMessage("    match-width       Match all shapes to reference width");
        display.displayMessage("    match-height      Match all shapes to reference height");
        display.displayMessage("    match-size        Match both width and height");

        display.displayMessage("");
        display.displayMessage("  CONVENIENCE:");
        display.displayMessage("    center-on-slide   Center shape(s) on slide");
        display.displayMessage("    snap-to-grid      Snap position to 1-inch grid");

        display.displayMessage("");
        display.displayMessage("  SELECTORS:");
        display.displayMessage("    2,3,4,5           Explicit SPIDs");
        display.displayMessage("    all               All shapes (excludes group container)");
        display.displayMessage("    type:rectangle    All shapes of type");
        display.displayMessage("    text:*            All shapes with text");
        display.displayMessage("    name:Title*       Glob match on shape name");

        display.displayMessage("");
        display.displayMessage("Usage: arrange <slide> <operation> <targets> [--anchor <spid>]");
        display.displayMessage("       reorder <slide> <spid> <front|back|forward|backward>");

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Read-only command");
    }

    @Override
    public boolean canUndo() { return false; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() { return "List Arrange Operations"; }
}
