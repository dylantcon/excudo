package com.excudo.core.commands;

import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class-keyed registry of self-describing Commands.
 *
 * <p>A Command opts in by declaring two static members:
 * <ul>
 *   <li>{@code public static final CommandSchema SCHEMA} — the parameter contract</li>
 *   <li>{@code public static Command fromParameters(CommandParameters p, CommandContext ctx)}
 *       — the factory invoked when {@code execute_commands} or the REPL parser
 *       hands over validated parameters.</li>
 * </ul>
 *
 * <p>The canonical command name is derived from the class name via
 * {@link Command#getCommandName()} ({@code AddShapeCommand} → {@code add-shape}).
 * Magic-string registration is no longer required.
 *
 * <p>This registry lives in {@code core/commands} (not {@code core/parsing})
 * because it must reference {@link Command} and {@link CommandContext} which
 * sit in {@code core/commands}. The legacy {@link CommandRegistry} schemas
 * map is still populated as a side effect — schema lookups (validators,
 * system-prompt generators, dispatcher) keep working uniformly during the
 * migration.
 */
public final class CommandClassRegistry {

    @FunctionalInterface
    public interface CommandFactory {
        Command create(CommandParameters params, CommandContext ctx);
    }

    private record ClassEntry(Class<? extends Command> commandClass,
                              CommandFactory factory) {}

    private static final Map<String, ClassEntry> CLASS_REGISTRY = new HashMap<>();

    static {
        // Self-describing Commands. Each declares its own SCHEMA + fromParameters;
        // the canonical name derives from the class name.
        registerCommandClass(com.excudo.core.commands.mutating.slide.AddShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.MoveShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.ReorderShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.deck.MoveSlideCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.UndoCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.RemoveShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.notes.AddNotesCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.SetActionCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.SetBodyPropsCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.ResizeShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.DuplicateShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.GroupShapesCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.ContentEditCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.BulletPointEditCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.SetTextCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.AddConnectorCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.AddAnimationCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.RemoveAnimationCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.UpdateAnimationCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.SetTransitionCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.RemoveTransitionCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.InjectIconCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.EnhancedContentCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.deck.CreateSlideCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.deck.DeleteSlideCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.deck.CopySlideCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListSlidesCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ShowSlideCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.RenderSlideCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.RedoCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.HistoryCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.NewPresentationCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.LoadCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.SaveCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.SessionCreateCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.SessionListCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.SessionInfoCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.SessionCloseCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.SessionSwitchCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.LLMCommand.class);
        registerCommandClass(com.excudo.core.commands.meta.LLMConfigCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListAnimationTypesCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListShapeTypesCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListTransitionTypesCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListArrangeOpsCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListThemesCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.HelpCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ShowThemeCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ShowMasterCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.theme.ApplyThemeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.theme.DeleteThemeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.layout.DeleteLayoutCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.layout.RenameLayoutCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.layout.DuplicateLayoutCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.layout.RemovePlaceholderCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.UngroupCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.master.EditMasterBgCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListLayoutsCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListSpidsCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListAnimationsCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ListNotesCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.DumpShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.DumpTimingCommand.class);
        registerCommandClass(com.excudo.core.commands.readonly.ShowShapeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.theme.CreateThemeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.theme.EditThemeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.theme.SetObjectDefaultsCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.layout.AddLayoutCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.layout.AddPlaceholderCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.ArrangeCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.SetFontCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.SetStyleCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.slide.CopyStyleCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.master.EditMasterStyleCommand.class);
        registerCommandClass(com.excudo.core.commands.mutating.master.EditMasterClrMapCommand.class);
    }

    private CommandClassRegistry() {}

    /**
     * Register a Command class. Throws at startup with a precise message if either
     * static contract member is missing, so the failure mode is loud and immediate
     * (not a silent runtime null at first dispatch).
     */
    public static void registerCommandClass(Class<? extends Command> commandClass) {
        Objects.requireNonNull(commandClass, "commandClass");
        try {
            CommandSchema schema = (CommandSchema) commandClass
                .getField("SCHEMA").get(null);

            MethodHandle factoryHandle = MethodHandles.lookup().findStatic(
                commandClass, "fromParameters",
                MethodType.methodType(Command.class, CommandParameters.class, CommandContext.class));

            // Canonical name is derived from the class -- the single source of
            // truth. Class-registered SCHEMAs are declared nameless; we stamp
            // the derived name onto the schema here so schema consumers
            // (validators, LLM tool-schema generation) see it. assignName
            // throws if the schema carries a conflicting hardcoded name.
            String name = nameOf(commandClass);
            schema.assignName(name);

            CommandFactory factory = (params, ctx) -> {
                try {
                    return (Command) factoryHandle.invoke(params, ctx);
                } catch (Throwable e) {
                    if (e instanceof RuntimeException re) throw re;
                    throw new RuntimeException(
                        "Failed to construct " + commandClass.getSimpleName()
                        + " from parameters: " + e.getMessage(), e);
                }
            };

            CLASS_REGISTRY.put(name, new ClassEntry(commandClass, factory));
            CommandRegistry.addSchema(name, schema);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(commandClass.getSimpleName()
                + " must declare 'public static final CommandSchema SCHEMA' "
                + "to use registerCommandClass().", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(commandClass.getSimpleName()
                + " must declare 'public static Command fromParameters(CommandParameters p, CommandContext ctx)' "
                + "to use registerCommandClass().", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(commandClass.getSimpleName()
                + " has SCHEMA / fromParameters but they're not accessible: "
                + e.getMessage(), e);
        }
    }

    /**
     * Construct a Command instance via the class-keyed registry. Returns
     * {@code null} when the command name isn't registered — the caller falls
     * back to the legacy switch-based factory path for unmigrated commands.
     */
    public static Command createFromParameters(CommandParameters params, CommandContext ctx) {
        if (params == null) return null;
        ClassEntry entry = CLASS_REGISTRY.get(params.getCommandName());
        if (entry == null) return null;
        return entry.factory().create(params, ctx);
    }

    /** Names of commands registered via {@link #registerCommandClass}. */
    public static Set<String> getRegisteredCommandNames() {
        return Collections.unmodifiableSet(CLASS_REGISTRY.keySet());
    }

    /**
     * Derive the canonical command name from a Command class:
     * PascalCase → kebab-case, stripping a trailing {@code "Command"}.
     * {@code AddShapeCommand} → {@code add-shape}.
     *
     * <p>Exposed so each migrated Command can publish its name as a
     * compile-time-checked symbolic constant:
     * <pre>{@code
     * public static final String NAME = CommandClassRegistry.nameOf(MyCommand.class);
     * }</pre>
     * Callers (tests, dispatchers, LLM-bridge tests) reference
     * {@code MyCommand.NAME} instead of hardcoding the kebab string -- the
     * class is the single source of truth and a rename breaks at compile
     * time instead of silently in tests.
     */
    public static String nameOf(Class<? extends Command> commandClass) {
        String simple = commandClass.getSimpleName();
        String stripped = simple.endsWith("Command")
            ? simple.substring(0, simple.length() - "Command".length())
            : simple;
        StringBuilder sb = new StringBuilder(stripped.length() + 4);
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append('-');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
