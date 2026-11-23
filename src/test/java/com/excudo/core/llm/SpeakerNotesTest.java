package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.mutating.notes.AddNotesCommand;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PackageIntegrityValidator;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.xml.writers.SpeakerNotesWriter;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Speaker notes on a deck built in memory. add-notes was a stub that always
 * failed ("not yet migrated to in-memory path") and wasn't offered to the agent.
 */
public class SpeakerNotesTest {

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private ToolDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "One", "slideLayout7");
        orchestrator.createSlide(2, "Two", "slideLayout7");
        orchestrator.createSlide(3, "Three", "slideLayout7");
        doc = orchestrator.getContext().get().getDocument();
        dispatcher = new ToolDispatcher(orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
    }

    @Test
    public void theAgentCanWriteNotes() {
        String out = notes(1, "Open with the 513 number.\nThen the cost.");
        assertTrue(out, out.startsWith("OK: 1 command(s) executed"));
        assertEquals("Open with the 513 number.\nThen the cost.", SpeakerNotesWriter.getNotes(doc, 1));
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));
    }

    @Test
    public void notesReplaceAndShareOneNotesMaster() {
        notes(1, "first");
        notes(1, "second");
        notes(2, "other slide");
        assertEquals("second", SpeakerNotesWriter.getNotes(doc, 1));
        assertEquals("other slide", SpeakerNotesWriter.getNotes(doc, 2));
        assertNull("slide 3 has no notes", SpeakerNotesWriter.getNotes(doc, 3));
        assertEquals(1, doc.getPartNamesByPrefix("ppt/notesMasters/notesMaster").stream()
            .filter(n -> n.endsWith(".xml")).count());
        assertEquals(2, doc.getPartNamesByPrefix("ppt/notesSlides/notesSlide").stream()
            .filter(n -> n.endsWith(".xml")).count());
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));
    }

    @Test
    public void undoRestoresThePreviousNotes() {
        notes(1, "kept");
        AddNotesCommand change = new AddNotesCommand(1, "replaced", orchestrator);
        change.execute();
        assertEquals("replaced", SpeakerNotesWriter.getNotes(doc, 1));
        change.undo();
        assertEquals("kept", SpeakerNotesWriter.getNotes(doc, 1));
    }

    @Test
    public void notesSurviveSaveAndReload() throws Exception {
        notes(2, "Talk about the agent.");
        File out = Files.createTempFile("notes", ".pptx").toFile();
        out.deleteOnExit();
        assertTrue(orchestrator.savePresentation(out).isSuccess());
        PPTXDocument reloaded = PPTXDocument.loadFromZip(out);
        assertEquals("Talk about the agent.", SpeakerNotesWriter.getNotes(reloaded, 2));
        assertEquals(List.of(), PackageIntegrityValidator.validate(reloaded));
    }

    @Test
    public void slidesWithNotesCanBeDeletedAndMoved() {
        notes(1, "one");
        notes(2, "two");
        notes(3, "three");
        assertTrue(orchestrator.deleteSlide(1).isSuccess());
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));
        assertTrue(orchestrator.moveSlide(2, 1).isSuccess());
        assertEquals(List.of(), PackageIntegrityValidator.validate(doc));
        assertEquals("three", SpeakerNotesWriter.getNotes(doc, 1));
        assertEquals("two", SpeakerNotesWriter.getNotes(doc, 2));
    }

    private String notes(int slide, String text) {
        JsonObject cmd = new JsonObject();
        cmd.addProperty("type", "add-notes");
        cmd.addProperty("slideNumber", slide);
        cmd.addProperty("text", text);
        JsonObject input = new JsonObject();
        input.addProperty("commands", "[" + cmd + "]");
        String out = dispatcher.dispatch("execute_commands", input.toString());
        assertTrue(out, out.startsWith("OK: 1 command(s) executed"));
        return out;
    }
}
