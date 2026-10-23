package com.excudo.core.commands.readonly;

import com.excudo.core.parsing.CommandRegistry;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HelpCatalogTest {

    @Test
    public void overview_listsEveryRegisteredCommand() {
        String overview = String.join("\n", HelpCatalog.overview());
        for (String name : CommandRegistry.getCommandNames()) {
            assertTrue("overview missing '" + name + "'", overview.contains(name));
        }
    }

    @Test
    public void everyCommand_isInExactlyOneCategory() {
        Set<String> seen = new HashSet<>();
        for (List<String> names : HelpCatalog.commandsByCategory().values()) {
            for (String n : names) assertTrue("duplicate " + n, seen.add(n));
        }
        assertEquals(CommandRegistry.getCommandNames(), seen);
    }

    @Test
    public void commandHelp_worksForCanonicalNameAndAlias() {
        List<String> canonical = HelpCatalog.commandHelp("load");
        assertNotNull(canonical);
        assertEquals(canonical, HelpCatalog.commandHelp("open"));
        assertTrue(String.join("\n", canonical).contains("Aliases: open"));
    }

    @Test
    public void commandHelp_unknownReturnsNull() {
        assertNull(HelpCatalog.commandHelp("xyz-not-a-command"));
    }

    @Test
    public void categoryHelp_resolvesTopicSynonyms() {
        assertNotNull(HelpCatalog.categoryHelp("shapes"));
        assertNotNull(HelpCatalog.categoryHelp("session"));
        assertNull(HelpCatalog.categoryHelp("nonsense-topic"));
    }
}
