package com.excudo.core.commands;

import com.excudo.core.smartcontent.IconRepository;

/**
 * Interface for console contexts that support icon management commands.
 * Implemented by AbstractConsoleEngine to provide the IconCommand with
 * access to the icon repository and interactive prompts.
 */
public interface IconContext {
    IconRepository getIconRepository();
    void displayMessage(String message);
    void displayError(String message);
    String promptUser(String prompt);
}
