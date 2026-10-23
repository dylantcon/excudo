package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.AnimationParameterRequirement;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.AnimationType;
import com.excudo.core.parsing.CommandRegistry;

/**
 * GoF Command for displaying help information.
 * 
 * This is a read-only utility command that shows general help or
 * specific help for commands/animation types. Does not support undo
 * since it performs no mutations.
 */
public class HelpCommand implements Command {

    static final Parameter<String> TOPIC = Parameter.ofString("topic")
        .description("Command name or alias, category, or animation type")
        .required(false).variableLength(true).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Show help: overview, a category, or a specific command")
        .parameter(TOPIC)
        .example("help")
        .example("help add-shape")
        .example("help shapes")
        .example("help session create")
        .example("help animations")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(HelpCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new HelpCommand(ctx.requireDisplay(), p.getString(TOPIC.getName()));
    }

    
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
        showLines(HelpCatalog.overview());
    }

    private void showLines(java.util.List<String> lines) {
        for (String line : lines) {
            display.displayMessage(line);
        }
    }
    
    private void showSpecificHelp(String lowerTopic) {
        String[] words = lowerTopic.split("\\s+");
        if (lowerTopic.equals("commands") || lowerTopic.equals("command") || lowerTopic.equals("all")) {
            showGeneralHelp();
            return;
        }

        // A command name or alias, including two-word spellings ("session create").
        String commandTopic = null;
        if (words.length > 1 && CommandRegistry.hasCommand(words[0] + "-" + words[1])) {
            commandTopic = words[0] + "-" + words[1];
        } else if (CommandRegistry.hasCommand(words[0])) {
            commandTopic = words[0];
        }
        if (commandTopic != null) {
            showLines(HelpCatalog.commandHelp(commandTopic));
            String canonical = CommandRegistry.resolveName(commandTopic);
            if (canonical.equals(com.excudo.core.commands.mutating.slide.AddAnimationCommand.NAME)) {
                display.displayMessage("");
                showAddAnimationNotes();
            } else if (canonical.equals(com.excudo.core.commands.mutating.slide.ArrangeCommand.NAME)) {
                display.displayMessage("");
                display.displayMessage("Note: bare 'arrange' (no arguments) enters AI arrange mode instead.");
            }
            return;
        }

        if (words[0].equals("arrange-mode") || words[0].equals("llm-mode")) {
            showLines(HelpCatalog.categoryHelp("ai"));
            return;
        }

        // Category listing; "animations" also lists the animation types.
        java.util.List<String> category = HelpCatalog.categoryHelp(words[0]);
        if (category != null) {
            showLines(category);
            if (HelpCatalog.Category.fromTopic(words[0]) == HelpCatalog.Category.ANIMATIONS) {
                display.displayMessage("");
                display.displayMessage("Animation types:");
                for (String type : AnimationType.getAllTypeNames()) {
                    display.displayMessage("  " + type);
                }
                display.displayMessage("Use 'help <animation-type>' for its parameters.");
            }
            return;
        }

        // Exact animation type name (AnimationType.parseType() silently
        // defaults to FADE for unknown input, so don't use it here).
        for (AnimationType type : AnimationType.values()) {
            if (type.getUserFriendlyName().equalsIgnoreCase(lowerTopic)) {
                display.displayMessage(AnimationParameterRequirement.generateHelpText(type));
                return;
            }
        }

        display.displayError("No help available for topic: " + topic);
        display.displayMessage("Try 'help' for the command list, or one of these categories:");
        display.displayMessage("  " + String.join(", ", categoryTopics()));
    }

    private static java.util.List<String> categoryTopics() {
        java.util.List<String> topics = new java.util.ArrayList<>();
        for (HelpCatalog.Category c : HelpCatalog.commandsByCategory().keySet()) {
            topics.add(c.topics().get(0));
        }
        return topics;
    }

    private void showAddAnimationNotes() {
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
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
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