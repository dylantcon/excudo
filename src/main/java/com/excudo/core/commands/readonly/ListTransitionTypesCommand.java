package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;

import com.excudo.core.model.TransitionType;

/**
 * Command that lists all available slide transition types.
 */
public class ListTransitionTypesCommand implements Command {

    private final CommandDisplay display;

    public ListTransitionTypesCommand(CommandDisplay display) {
        this.display = display;
    }

    @Override
    public void execute() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Slide Transition Types:\n\n");

        for (TransitionType type : TransitionType.values()) {
            sb.append(String.format("  %-25s %s\n", type.getUserFriendlyName(), type.getDescription()));
        }

        sb.append("\nUsage: set-transition <slide> <type> [--speed slow|med|fast] [--advance <ms>]");
        display.displayMessage(sb.toString());
    }

    @Override
    public void undo() {}

    @Override
    public boolean canUndo() { return false; }

    @Override
    public boolean isExecuted() { return true; }

    @Override
    public String getDescription() { return "List transition types"; }
}
