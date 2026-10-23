package com.excudo.core.parsing;

import com.excudo.core.commands.meta.LLMCommand;
import com.excudo.core.commands.meta.LLMConfigCommand;
import com.excudo.core.commands.meta.LoadCommand;
import com.excudo.core.commands.meta.NewPresentationCommand;
import com.excudo.core.commands.meta.SessionCreateCommand;
import com.excudo.core.commands.mutating.deck.CreateSlideCommand;
import com.excudo.core.commands.readonly.HelpCommand;
import com.excudo.core.commands.readonly.ListSlidesCommand;
import com.excudo.core.commands.readonly.ShowSlideCommand;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Command-name resolution: acronym-aware derived names, aliases,
 * case-insensitivity, and two-word spellings of hyphenated commands.
 */
public class CommandAliasTest {

    @Test
    public void acronymClassNames_deriveSingleWordNames() {
        assertEquals("llm", LLMCommand.NAME);
        assertEquals("llm-config", LLMConfigCommand.NAME);
    }

    @Test
    public void aliases_resolveToCanonicalNames() {
        assertEquals(HelpCommand.NAME, CommandRegistry.resolveName("?"));
        assertEquals(LoadCommand.NAME, CommandRegistry.resolveName("open"));
        assertEquals(NewPresentationCommand.NAME, CommandRegistry.resolveName("new"));
        assertEquals(ListSlidesCommand.NAME, CommandRegistry.resolveName("list"));
        assertEquals(ShowSlideCommand.NAME, CommandRegistry.resolveName("show"));
        assertEquals(CreateSlideCommand.NAME, CommandRegistry.resolveName("create"));
        assertEquals("content-edit", CommandRegistry.resolveName("edit-content"));
        assertNull(CommandRegistry.resolveName("definitely-not-a-command"));
    }

    @Test
    public void lookup_isCaseInsensitive() {
        assertEquals(HelpCommand.NAME, CommandRegistry.resolveName("HELP"));
        assertEquals(LoadCommand.NAME, CommandRegistry.resolveName("Open"));
    }

    @Test
    public void parse_viaAlias_carriesCanonicalName() throws Exception {
        CommandParameters p = CommandRegistry.parse("open deck.pptx");
        assertEquals(LoadCommand.NAME, p.getCommandName());
    }

    @Test
    public void parse_twoWordForm_joinsIntoHyphenatedCommand() throws Exception {
        assertEquals(SessionCreateCommand.NAME,
            CommandRegistry.parse("session create deck.pptx").getCommandName());
        assertEquals(LLMConfigCommand.NAME,
            CommandRegistry.parse("llm config show").getCommandName());
    }

    @Test
    public void parse_helpTopic_isPassedThrough() throws Exception {
        CommandParameters p = CommandRegistry.parse("help add-shape");
        assertEquals(HelpCommand.NAME, p.getCommandName());
        assertEquals("add-shape", p.getString("topic"));
    }

    @Test
    public void aliases_neverShadowCanonicalNames() {
        for (String alias : CommandRegistry.getAllAliases().keySet()) {
            assertFalse("alias '" + alias + "' shadows a command",
                CommandRegistry.getCommandNames().contains(alias));
        }
    }
}
