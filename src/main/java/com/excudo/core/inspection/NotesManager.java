package com.excudo.core.inspection;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.console.utils.ConsoleCommandValidator;
import com.excudo.xml.writers.SlideNotesWriter;
import java.util.function.Consumer;

/**
 * Utility class for slide notes management in console operations.
 * Centralizes notes handling to eliminate code duplication in AbstractConsoleEngine.
 */
public class NotesManager {

    private final Consumer<String> displayMessage;
    private final Consumer<String> displayError;

    public NotesManager(Consumer<String> displayMessage, Consumer<String> displayError) {
        this.displayMessage = displayMessage;
        this.displayError = displayError;
    }

    /**
     * Handle list-notes command - displays notes content for slides
     */
    public void handleListNotes(PPTXOrchestrator sessionOrchestrator, String slideArg) {
        // Validate session and orchestrator
        ConsoleCommandValidator.ValidationResult validation = ConsoleCommandValidator.validateSessionOrchestrator(sessionOrchestrator);
        if (!validation.isValid()) {
            displayError.accept(validation.getErrorMessage());
            return;
        }

        try {
            String presentationPath = (String) sessionOrchestrator.getContext().get().getContextValue("originalFilePath", String.class);
            if (presentationPath == null) {
                displayError.accept("Original presentation file path not found in session context.");
                return;
            }

            SlideNotesWriter notesWriter = new SlideNotesWriter(presentationPath);

            if (slideArg == null) {
                // List notes for all slides
                displayMessage.accept("SLIDE NOTES:");
                displayMessage.accept("=============");

                // Get slide count from the orchestrator
                int slideCount = sessionOrchestrator.getPresentationMetadata().getSlideCount();
                boolean foundNotes = false;

                for (int i = 1; i <= slideCount; i++) {
                    try {
                        String notes = notesWriter.getSlideNotes(i);
                        if (notes != null && !notes.trim().isEmpty()) {
                            displayMessage.accept("Slide " + i + ":");
                            displayMessage.accept(notes.trim());
                            displayMessage.accept("");
                            foundNotes = true;
                        }
                    } catch (Exception e) {
                        // Slide might not have notes, continue with next slide
                    }
                }

                if (!foundNotes) {
                    displayMessage.accept("No notes found in any slides.");
                }
            } else {
                // List notes for specific slide
                try {
                    int slideNumber = Integer.parseInt(slideArg);
                    String notes = notesWriter.getSlideNotes(slideNumber);

                    if (notes != null && !notes.trim().isEmpty()) {
                        displayMessage.accept("Notes for Slide " + slideNumber + ":");
                        displayMessage.accept("=============================");
                        displayMessage.accept(notes.trim());
                    } else {
                        displayMessage.accept("No notes found for slide " + slideNumber);
                    }
                } catch (NumberFormatException e) {
                    displayError.accept("Invalid slide number: " + slideArg);
                    displayMessage.accept("Usage: list-notes [slide#]");
                }
            }

        } catch (Exception e) {
            displayError.accept("Failed to read notes: " + e.getMessage());
        }
    }
}
