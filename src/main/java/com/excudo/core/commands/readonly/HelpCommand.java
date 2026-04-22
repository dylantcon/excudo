package com.excudo.core.commands.readonly;

import com.excudo.core.commands.AnimationParameterRequirement;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.AnimationType;
import com.excudo.console.utils.ConsoleOutputFormatter;

/**
 * GoF Command for displaying help information.
 * 
 * This is a read-only utility command that shows general help or
 * specific help for commands/animation types. Does not support undo
 * since it performs no mutations.
 */
public class HelpCommand implements Command {
    
    private final CommandDisplay display;
    private final String topic;
    private boolean executed = false;
    
    /**
     * Create a HelpCommand for general help.
     * 
     * @param display the console display interface
     */
    public HelpCommand(CommandDisplay display) {
        this(display, null);
    }
    
    /**
     * Create a HelpCommand for specific topic help.
     * 
     * @param display the console display interface
     * @param topic the specific help topic (command or animation type)
     */
    public HelpCommand(CommandDisplay display, String topic) {
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.display = display;
        this.topic = topic;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            if (topic == null || topic.trim().isEmpty()) {
                showGeneralHelp();
            } else {
                showSpecificHelp(topic.trim().toLowerCase());
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to display help: " + e.getMessage(), e);
        }
    }
    
    private void showGeneralHelp() {
        showSpecificHelp("commands");
    }
    
    private void showSpecificHelp(String lowerTopic) {
        // Check known help topics first (before animation type lookup, since
        // AnimationType.parseType() silently defaults to FADE for unknown input)
        switch (lowerTopic) {
            case "animations":
            case "animation":
                display.displayMessage("Available Animation Types:");
                display.displayMessage("");
                String[] allTypes = AnimationType.getAllTypeNames();
                for (String type : allTypes) {
                    display.displayMessage("  " + type);
                }
                display.displayMessage("");
                display.displayMessage("Use 'help <animation-type>' for specific animation parameters");
                return;

            case "add-animation":
                display.displayMessage("Add Animation Command Help:");
                display.displayMessage("Usage: add-animation <slide#> <spid> <type> [parameters...]");
                display.displayMessage("");
                display.displayMessage("Parameters vary by animation type. Examples:");
                display.displayMessage("  " + AnimationParameterRequirement.getRequirements(AnimationType.FADE).getUsageExample());
                display.displayMessage("  " + AnimationParameterRequirement.getRequirements(AnimationType.SPIN).getUsageExample());
                display.displayMessage("  " + AnimationParameterRequirement.getRequirements(AnimationType.MOTION_LINEAR).getUsageExample());
                display.displayMessage("");
                display.displayMessage("Animation Grouping (PowerPoint compatibility):");
                display.displayMessage("  - '" + AnimationParameterRequirement.TRIGGER_ON_CLICK + "' trigger: Creates new animation group");
                display.displayMessage("  - '" + AnimationParameterRequirement.TRIGGER_WITH_PREVIOUS + "' trigger: Joins current group (simultaneous)");
                display.displayMessage("  - '" + AnimationParameterRequirement.TRIGGER_AFTER_PREVIOUS + "' trigger: Joins current group (sequential)");
                display.displayMessage("");
                display.displayMessage("Use 'help animations' to see all animation types");
                return;

            case "commands":
            case "command":
                for (String line : ConsoleOutputFormatter.generateHelpText()) {
                    display.displayMessage(line);
                }
                return;

            // Category help topics
            case "getting-started":
            case "start":
                for (String line : ConsoleOutputFormatter.generateGettingStartedHelp()) {
                    display.displayMessage(line);
                }
                return;

            case "shapes":
            case "shape":
                for (String line : ConsoleOutputFormatter.generateShapeHelp()) {
                    display.displayMessage(line);
                }
                return;

            case "slides":
            case "slide":
                for (String line : ConsoleOutputFormatter.generateSlideHelp()) {
                    display.displayMessage(line);
                }
                return;

            case "themes":
            case "theme":
                for (String line : ConsoleOutputFormatter.generateThemeHelp()) {
                    display.displayMessage(line);
                }
                return;

            case "debug":
            case "xml":
            case "debugging":
                for (String line : ConsoleOutputFormatter.generateDebugHelp()) {
                    display.displayMessage(line);
                }
                return;

            case "ai":
            case "llm":
            case "smart":
            case "smartcontent":
                for (String line : ConsoleOutputFormatter.generateAIHelp()) {
                    display.displayMessage(line);
                }
                return;

            case "sessions":
            case "session":
            case "history":
                for (String line : ConsoleOutputFormatter.generateSessionHelp()) {
                    display.displayMessage(line);
                }
                return;
        }

        // Check if it's a registered command name
        String commandHelp = ConsoleOutputFormatter.getCommandHelp(lowerTopic);
        if (!commandHelp.startsWith("Unknown command:")) {
            display.displayMessage(commandHelp);
            return;
        }

        // Check if it's a known animation type name (exact match only)
        for (AnimationType type : AnimationType.values()) {
            if (type.getUserFriendlyName().equals(lowerTopic)) {
                String helpText = AnimationParameterRequirement.generateHelpText(type);
                display.displayMessage(helpText);
                return;
            }
        }

        display.displayError("No help available for topic: " + topic);
        display.displayMessage("Available topics: shapes, slides, animations, themes, debug, ai, session");
        display.displayMessage("Also: help <command-name>  |  help <animation-type>");
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "HelpCommand is read-only and does not support undo");
    }
    
    @Override
    public boolean canUndo() {
        return false; // Read-only operation
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return topic != null ? "Help for " + topic : "General help";
    }
}