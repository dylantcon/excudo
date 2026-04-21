package com.excudo.view.components;

import com.excudo.view.MainController;
import com.excudo.core.llm.*;
import com.excudo.core.orchestration.*;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.console.ConsoleStyle;
import com.excudo.console.LLMConsoleHandler;
import com.excudo.view.console.StyledConsoleView;
import com.excudo.view.console.UIConsoleEngine;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import javafx.geometry.Bounds;
import javafx.scene.control.ListView;
import javafx.stage.Popup;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.Font;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import java.time.format.DateTimeFormatter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Controller for the technical console providing direct Model method invocation
 * and LLM agentic functionality through technical command interface.
 */
public class TechnicalConsoleController implements Initializable {
    
    // ========== FXML COMPONENTS ==========
    
    @FXML private VBox consoleContainer;
    @FXML private TextFlow consoleOutput;
    @FXML private ScrollPane consoleScrollPane;
    @FXML private TextField commandInput;
    @FXML private Button executeButton;
    @FXML private Button clearButton;
    @FXML private Button helpButton;
    @FXML private Label statusLabel;

    // ========== STATE ==========

    private MainController mainController;
    private LLMConsoleHandler llmConsoleHandler;
    private UIConsoleEngine consoleEngine;
    private StyledConsoleView styledView;
    private List<String> commandHistory;
    private int historyIndex;
    
    // Autocomplete state
    private Popup autocompletePopup;
    private ListView<String> autocompleteSuggestions;
    private List<String> currentSuggestions;
    private Map<String, String> commandDescriptions;
    private Map<String, List<String>> commandParameters;
    
    // Inline autocomplete silhouette
    private Label silhouetteLabel;
    private StackPane inputContainer;
    private String currentSilhouette = "";
    
    // Console styling
    private static final String CONSOLE_STYLE = 
        "-fx-font-family: 'Courier New', monospace; " +
        "-fx-font-size: 12px; " +
        "-fx-background-color: #1e1e1e; " +
        "-fx-text-fill: #d4d4d4;";
    
    private static final String INPUT_STYLE = 
        "-fx-font-family: 'Courier New', monospace; " +
        "-fx-font-size: 12px; " +
        "-fx-background-color: #2d2d30; " +
        "-fx-text-fill: #d4d4d4; " +
        "-fx-prompt-text-fill: #6a6a6a;";
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComponents();
        setupEventHandlers();
        setupInitialState();
    }
    
    // ========== INITIALIZATION ==========
    
    /**
     * Set reference to main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;

        // Eagerly initialise the console engine. Post Session Unification,
        // MainController has no orchestrator of its own and
        // SessionManager.getActiveOrchestrator() is null at boot (no
        // session yet), so gating init behind "orchestrator != null"
        // leaves consoleSessionManager + commandFactory null and every
        // menu-Open / console-open / create-session path NPEs on first use.
        if (mainController != null) {
            initializeLLMHandler();
        }
    }
    
    /**
     * Set console components from FXML
     */
    public void setConsoleComponents(TextFlow consoleOutput, ScrollPane consoleScrollPane,
                                     TextField commandInput, Button executeButton) {
        this.consoleOutput = consoleOutput;
        this.consoleScrollPane = consoleScrollPane;
        this.commandInput = commandInput;
        this.executeButton = executeButton;

        // Re-setup components now that we have them
        setupComponents();
        setupEventHandlers();
        setupInitialState();
        
        // Defer silhouette setup to ensure scene graph is ready
        if (commandInput != null) {
            Platform.runLater(() -> {
                setupInlineSilhouette();
            });
        }
        
        // Initialise the engine as soon as the main controller is wired
        // up; post Session Unification we can't wait for an active-session
        // orchestrator because there isn't one until the user opens a deck.
        if (mainController != null) {
            initializeLLMHandler();
        }
    }
    
    private void setupComponents() {
        // Style console components
        if (consoleOutput != null) {
            consoleOutput.setStyle(CONSOLE_STYLE);
        }
        if (consoleScrollPane != null) {
            consoleScrollPane.setStyle("-fx-background-color: #1e1e1e;");
        }

        if (commandInput != null) {
            commandInput.setStyle(INPUT_STYLE);
            commandInput.setPromptText("Enter command");
        }

        // Initialize command history
        commandHistory = new ArrayList<>();
        historyIndex = -1;

        // Initialize styled view and UIConsoleEngine
        if (consoleOutput != null) {
            styledView = new StyledConsoleView(consoleOutput, consoleScrollPane);
            consoleEngine = new UIConsoleEngine(null);
            consoleEngine.setStyledHandler(styledView::appendLine);
            consoleEngine.setStatusHandler(this::updateStatus);
            consoleEngine.setModeChangeHandler(this::onArrangeModeChanged);
            // orchestratorChangeHandler wiring removed: the
            // MainController subscribes directly to
            // SessionManager.addStateListener (Session Unification
            // refactor) and catches MCP-created sessions for free,
            // not just this engine's session changes.
        }

        // Initialize autocomplete
        initializeAutocomplete();
    }
    
    private void setupEventHandlers() {
        // Command input handlers
        if (commandInput != null) {
            commandInput.setOnKeyPressed(this::handleKeyPressed);
            commandInput.setOnAction(e -> executeCommand());
        }
        
        // Button handlers
        if (executeButton != null) {
            executeButton.setOnAction(e -> executeCommand());
        }
        if (clearButton != null) {
            clearButton.setOnAction(e -> clearConsole());
        }
        if (helpButton != null) {
            helpButton.setOnAction(e -> showHelp());
        }
    }
    
    private void setupInitialState() {
        printWelcomeMessage();
        updateStatus("Console ready");
    }
    
    /**
     * Initialize the LLM console handler using proper delegation
     */
    private void initializeLLMHandler() {
        if (mainController == null) return;

        // The engine needs an orchestrator to construct its CommandFactory,
        // LLMConsoleHandler, and ConsoleSessionManager. Post Session
        // Unification the active orchestrator is null until the first
        // session is created, so fall back to a fresh sessionless
        // PPTXOrchestratorImpl at boot; the first setCurrentSession call
        // will replace the engine's orchestrator with the session-bound
        // one via the standard UIConsoleEngine/SessionManager plumbing.
        PPTXOrchestrator orch = mainController.getCurrentOrchestrator();
        if (orch == null) {
            try {
                orch = new PPTXOrchestratorImpl();
            } catch (Exception e) {
                printError("Failed to initialize console engine: " + e.getMessage());
                return;
            }
        }

        llmConsoleHandler = new LLMConsoleHandler(orch);

        if (consoleEngine != null) {
            consoleEngine.initialize(orch);
            if (styledView != null) {
                consoleEngine.setStyledHandler(styledView::appendLine);
            }
            consoleEngine.setStatusHandler(this::updateStatus);
        }

        printInfo("Console engine initialized");
    }
    
    /**
     * Re-initialize LLM handler when presentation is loaded
     * This should be called by MainController after loading a presentation
     */
    public void onPresentationLoaded() {
        initializeLLMHandler();
    }
    
    // ========== COMMAND EXECUTION ==========
    
    /**
     * Execute command from input field - delegate to UIConsoleEngine
     */
    @FXML
    private void executeCommand() {
        if (commandInput != null && consoleEngine != null) {
            String command = commandInput.getText().trim();
            if (!command.isEmpty()) {
                // Add to history
                commandHistory.add(command);
                historyIndex = commandHistory.size();
                
                // Delegate to console engine
                consoleEngine.executeCommand(command);
                
                // Clear input
                commandInput.clear();
            }
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    // ========== EVENT HANDLERS ==========
    
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.UP) {
            navigateHistory(-1);
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            navigateHistory(1);
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            if (autocompletePopup != null && autocompletePopup.isShowing()) {
                acceptAutocompleteSuggestion();
            } else {
                executeCommand();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.TAB) {
            // Tab is handled in the event filter for silhouette acceptance
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            hideAutocomplete();
            event.consume();
        }
    }
    
    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        
        historyIndex += direction;
        historyIndex = Math.max(0, Math.min(historyIndex, commandHistory.size()));
        
        if (historyIndex < commandHistory.size()) {
            commandInput.setText(commandHistory.get(historyIndex));
            commandInput.positionCaret(commandInput.getText().length());
        } else {
            commandInput.clear();
        }
    }
    
    @FXML
    private void clearConsole() {
        if (styledView != null) {
            styledView.clear();
            printWelcomeMessage();
        }
    }
    
    @FXML
    private void showHelp() {
        // Delegate to console engine
        if (consoleEngine != null) {
            consoleEngine.executeCommand("help");
        }
    }
    
    // ========== OUTPUT METHODS ==========

    private void printWelcomeMessage() {
        if (styledView == null) return;
        String banner = """
            +====================================================+
            |                 Excudo Console                      |
            |          Comprehensive .pptx Toolchain              |
            +====================================================+""";
        styledView.appendLine(banner, ConsoleStyle.HEADER);
        styledView.appendLine("", ConsoleStyle.NONE);
        styledView.appendLine("  Type 'help' for commands, 'help <topic>' for details, or 'load <file>' to start",
            ConsoleStyle.DIM);
    }

    /**
     * Callback from UIConsoleEngine when the console enters/exits arrange mode.
     * Updates the command input's prompt and style class so the user gets a
     * visible indicator that they're typing into a multi-turn agentic session.
     */
    private void onArrangeModeChanged(boolean arrangeMode) {
        if (commandInput == null) return;
        if (arrangeMode) {
            commandInput.setPromptText("arrange > type or paste, blank line to send");
            if (!commandInput.getStyleClass().contains("arrange-mode")) {
                commandInput.getStyleClass().add("arrange-mode");
            }
            updateStatus("Arrange mode");
        } else {
            commandInput.setPromptText("Enter command");
            commandInput.getStyleClass().remove("arrange-mode");
            updateStatus("Console ready");
        }
    }

    private void printError(String error) {
        if (styledView != null) {
            styledView.appendLine("ERROR: " + error, ConsoleStyle.ERROR);
        }
    }

    private void printInfo(String info) {
        if (styledView != null) {
            styledView.appendLine(info, ConsoleStyle.NONE);
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }
    
    private void runBackgroundTask(Task<?> task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return String.format("%.1f MB / %.1f MB", 
                usedMemory / 1024.0 / 1024.0, 
                maxMemory / 1024.0 / 1024.0);
    }
    
    // ========== GETTERS ==========

    public List<String> getCommandHistory() {
        return new ArrayList<>(commandHistory);
    }

    /**
     * Expose the UIConsoleEngine so MainController can reach the active
     * session's CommandInvoker for Undo/Redo menu actions.
     */
    public UIConsoleEngine getConsoleEngine() {
        return consoleEngine;
    }
    
    // ========== AUTOCOMPLETE IMPLEMENTATION ==========
    
    /**
     * Initialize autocomplete functionality.
     *
     * Command names, descriptions, and parameter hints come from the central
     * CommandRegistry (single source of truth) rather than a hardcoded map.
     * The registry is static and immutable after startup, so we cache its
     * contents once here.
     */
    private void initializeAutocomplete() {

        commandDescriptions = new HashMap<>();
        commandParameters = new HashMap<>();

        // Pull every schema from CommandRegistry. This replaces a stale 9-command
        // hardcoded list that referenced removed commands (approve/deny/details,
        // invoke/inspect) and was missing 60+ real commands.
        for (CommandSchema schema : CommandRegistry.getAllSchemas().values()) {
            commandDescriptions.put(schema.getName(), schema.getDescription());

            List<String> paramHints = new ArrayList<>();
            for (Parameter p : schema.getParameters()) {
                paramHints.add(formatParamHint(p));
            }
            commandParameters.put(schema.getName(), paramHints);
        }

        // A few console-only helpers that aren't in CommandRegistry:
        commandDescriptions.putIfAbsent("clear", "Clear console output");
        commandDescriptions.putIfAbsent("status", "Display system status information");
        
        // Setup autocomplete UI
        autocompletePopup = new Popup();
        autocompletePopup.setAutoHide(true);
        
        autocompleteSuggestions = new ListView<>();
        autocompleteSuggestions.setPrefHeight(200);
        autocompleteSuggestions.setPrefWidth(480);
        autocompleteSuggestions.setStyle(
            "-fx-background-color: #2d2d30; " +
            "-fx-border-color: #569cd6; " +
            "-fx-border-width: 1px;"
        );

        // Cell factory: show "name  -  description" with description truncated.
        // Items stored in the list are bare command names; display text is
        // formatted here so acceptAutocompleteSuggestion can still extract
        // the command cleanly.
        autocompleteSuggestions.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String cmd, boolean empty) {
                super.updateItem(cmd, empty);
                if (empty || cmd == null) {
                    setText(null);
                    return;
                }
                String desc = commandDescriptions.get(cmd);
                if (desc == null || desc.isEmpty()) {
                    setText(cmd);
                } else {
                    // Truncate long descriptions to keep the popup readable
                    String truncated = desc.length() > 70
                        ? desc.substring(0, 67) + "..."
                        : desc;
                    setText(String.format("%-22s  %s", cmd, truncated));
                }
                setStyle("-fx-text-fill: #d4d4d4; -fx-background-color: transparent;");
            }
        });
        
        // Handle selection
        autocompleteSuggestions.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                acceptAutocompleteSuggestion();
            }
        });
        
        autocompleteSuggestions.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                acceptAutocompleteSuggestion();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hideAutocomplete();
                commandInput.requestFocus();
                e.consume();
            }
        });
        
        autocompletePopup.getContent().add(autocompleteSuggestions);
        
        // Monitor text changes for autocomplete
        if (commandInput != null) {
            commandInput.textProperty().addListener((obs, oldText, newText) -> {
                updateInlineSilhouette(newText);
                if (autocompletePopup.isShowing()) {
                    updateAutocompleteSuggestions(newText);
                }
            });
            
            // Add key handler for accepting silhouette with Tab
            commandInput.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.TAB && !currentSilhouette.isEmpty()) {
                    acceptInlineSilhouette();
                    e.consume();
                }
            });
        }
    }
    
    
    /**
     * Format a Parameter as a short hint token: <required> or [optional].
     */
    private String formatParamHint(Parameter p) {
        String wrap = p.isRequired() ? "<%s>" : "[%s]";
        return String.format(wrap, p.getName());
    }

    /**
     * Show autocomplete suggestions
     */
    private void showAutocomplete() {
        if (commandInput == null) return;
        
        String currentText = commandInput.getText();
        updateAutocompleteSuggestions(currentText);
        
        if (currentSuggestions != null && !currentSuggestions.isEmpty()) {
            // Position popup below input field
            Bounds bounds = commandInput.localToScreen(commandInput.getBoundsInLocal());
            autocompletePopup.show(commandInput, bounds.getMinX(), bounds.getMaxY());
            
            autocompleteSuggestions.getSelectionModel().selectFirst();
            autocompleteSuggestions.requestFocus();
        }
    }
    
    /**
     * Update autocomplete suggestions based on current text.
     *
     * Command names come from CommandRegistry directly so the list always
     * matches what the console actually understands. Parameter hints come
     * from each command's CommandSchema.
     */
    private void updateAutocompleteSuggestions(String text) {
        if (text == null || text.trim().isEmpty()) {
            // Show all commands from the registry
            currentSuggestions = new ArrayList<>(CommandRegistry.getCommandNames());
            java.util.Collections.sort(currentSuggestions);
        } else {
            String[] parts = text.trim().split("\\s+");

            if (parts.length == 1 && !text.endsWith(" ")) {
                // Autocomplete command names
                String prefix = parts[0].toLowerCase();
                currentSuggestions = CommandRegistry.getCommandNames().stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
            } else {
                // Show parameter hints for the command
                String command = parts[0].toLowerCase();
                List<String> params = commandParameters.get(command);
                if (params != null && !params.isEmpty()) {
                    int paramIndex = Math.max(0, parts.length - 1);
                    if (text.endsWith(" ")) {
                        paramIndex = parts.length;
                    }
                    if (paramIndex < params.size()) {
                        String nextParam = params.get(paramIndex);
                        currentSuggestions = List.of(text.stripTrailing() + " " + nextParam);
                    } else {
                        currentSuggestions = new ArrayList<>();
                    }
                } else {
                    currentSuggestions = new ArrayList<>();
                }
            }
        }

        // Update ListView with raw command names; the cell factory formats display text
        if (autocompleteSuggestions != null) {
            autocompleteSuggestions.getItems().setAll(currentSuggestions);

            if (currentSuggestions.isEmpty()) {
                hideAutocomplete();
            }
        }
    }
    
    /**
     * Accept selected autocomplete suggestion.
     *
     * Items in the ListView are now stored as raw command names (the cell
     * factory formats the display text), so no parsing is needed.
     */
    private void acceptAutocompleteSuggestion() {
        if (autocompleteSuggestions == null) return;

        String command = autocompleteSuggestions.getSelectionModel().getSelectedItem();
        if (command != null) {
            // If the entry is a parameter-hint line (starts with the current input),
            // use it as-is. Otherwise it's a bare command name; append a trailing space.
            if (command.contains(" ")) {
                commandInput.setText(command);
            } else {
                commandInput.setText(command + " ");
            }

            commandInput.positionCaret(commandInput.getText().length());
            hideAutocomplete();
            commandInput.requestFocus();
        }
    }
    
    /**
     * Hide autocomplete popup
     */
    private void hideAutocomplete() {
        if (autocompletePopup != null && autocompletePopup.isShowing()) {
            autocompletePopup.hide();
        }
    }
    
    /**
     * Get available commands for external use
     */
    public Map<String, String> getAvailableCommands() {
        return new HashMap<>(commandDescriptions);
    }
    
    /**
     * Get command parameters for external use
     */
    public Map<String, List<String>> getCommandParameters() {
        return new HashMap<>(commandParameters);
    }
    
    // ========== INLINE SILHOUETTE IMPLEMENTATION ==========
    
    /**
     * Setup inline silhouette overlay for command input
     */
    private void setupInlineSilhouette() {
        if (commandInput == null || commandInput.getParent() == null) return;
        
        // Check if already setup
        if (silhouetteLabel != null) return;
        
        // Create silhouette label
        silhouetteLabel = new Label();
        silhouetteLabel.setStyle(
            "-fx-text-fill: #6a6a6a; " +
            "-fx-font-family: 'Courier New', monospace; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 4 7 4 10;"  // Added 3px to left padding (7 + 3 = 10)
        );
        silhouetteLabel.setMouseTransparent(true);
        
        // Create a container that overlays the silhouette behind the input
        inputContainer = new StackPane();
        inputContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        // Make the TextField background transparent so we can see the label behind it
        commandInput.setStyle(INPUT_STYLE + " -fx-background-color: transparent; -fx-padding: 4 7 4 7;");
        
        // Replace command input in parent with container
        var parent = commandInput.getParent();
        if (parent instanceof javafx.scene.layout.HBox) {
            javafx.scene.layout.HBox hbox = (javafx.scene.layout.HBox) parent;
            int index = hbox.getChildren().indexOf(commandInput);
            if (index >= 0) {
                hbox.getChildren().remove(commandInput);
                
                // Add background to the container with proper insets
                inputContainer.setStyle(
                    "-fx-background-color: #2d2d30; " +
                    "-fx-background-radius: 3; " +
                    "-fx-border-color: #3e3e40; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 3;"
                );
                
                inputContainer.getChildren().addAll(silhouetteLabel, commandInput);
                hbox.getChildren().add(index, inputContainer);
                javafx.scene.layout.HBox.setHgrow(inputContainer, javafx.scene.layout.Priority.ALWAYS);
                
            }
        } else {
        }
    }
    
    /**
     * Update inline silhouette based on current input
     */
    private void updateInlineSilhouette(String input) {
        if (input == null) return;
        
        // If silhouette not set up, just update prompt text as fallback
        if (silhouetteLabel == null && commandInput != null) {
            // Fallback: show suggestions in prompt text
            if (!input.isEmpty()) {
                String suggestion = getSuggestionForInput(input);
                if (suggestion != null) {
                    commandInput.setPromptText(input + suggestion);
                }
            }
            return;
        }
        
        if (silhouetteLabel == null) return;
        
        currentSilhouette = "";
        
        // Don't show silhouette if input is empty or ends with space (unless showing parameters)
        if (input.trim().isEmpty()) {
            silhouetteLabel.setText("");
            return;
        }
        
        String[] parts = input.trim().split("\\s+");
        String lastPart = parts[parts.length - 1];
        
        // Available commands for silhouette suggestions
        List<String> availableCommands = new ArrayList<>(commandDescriptions.keySet());
        
        if (parts.length == 1 && !input.endsWith(" ")) {
            // Autocomplete command name
            String prefix = lastPart.toLowerCase();
            
            // Find best match
            String bestMatch = availableCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(prefix))
                .min((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
            
            if (bestMatch != null && !bestMatch.equals(prefix)) {
                // Show the remaining part of the command
                String remaining = bestMatch.substring(prefix.length());
                currentSilhouette = remaining;
                
                // Show full text with silhouette
                silhouetteLabel.setText(input + remaining);
            } else {
                silhouetteLabel.setText("");
            }
        } else if (parts.length >= 1 && (input.endsWith(" ") || parts.length > 1)) {
            // Show parameter hints
            String command = parts[0].toLowerCase();
            if (commandParameters.containsKey(command)) {
                List<String> params = commandParameters.get(command);
                int paramIndex = parts.length - 1;
                
                if (input.endsWith(" ") && paramIndex < params.size()) {
                    // Show next parameter
                    String nextParam = params.get(paramIndex);
                    currentSilhouette = nextParam;
                    
                    // Show current input plus parameter hint
                    silhouetteLabel.setText(input + nextParam);
                } else if (!input.endsWith(" ") && paramIndex > 0 && paramIndex <= params.size()) {
                    // Currently typing a parameter
                    silhouetteLabel.setText("");
                } else {
                    silhouetteLabel.setText("");
                }
            } else {
                silhouetteLabel.setText("");
            }
        } else {
            silhouetteLabel.setText("");
        }
    }
    
    /**
     * Accept the inline silhouette suggestion
     */
    private void acceptInlineSilhouette() {
        if (!currentSilhouette.isEmpty() && commandInput != null) {
            String currentText = commandInput.getText();
            
            if (currentSilhouette.startsWith("<") || currentSilhouette.startsWith("[")) {
                // It's a parameter placeholder, add it
                commandInput.setText(currentText + currentSilhouette + " ");
            } else {
                // It's a command completion
                commandInput.setText(currentText + currentSilhouette + " ");
            }
            
            commandInput.positionCaret(commandInput.getText().length());
            currentSilhouette = "";
            silhouetteLabel.setText("");
        }
    }
    
    /**
     * Helper method to get suggestion for input
     */
    private String getSuggestionForInput(String input) {
        if (input == null || input.isEmpty()) return null;
        
        String[] parts = input.trim().split("\\s+");
        String prefix = parts[parts.length - 1].toLowerCase();
        
        // Find matching commands
        List<String> availableCommands = new ArrayList<>(commandDescriptions.keySet());
        
        return availableCommands.stream()
            .filter(cmd -> cmd.toLowerCase().startsWith(prefix))
            .min((a, b) -> Integer.compare(a.length(), b.length()))
            .map(match -> match.substring(prefix.length()))
            .orElse(null);
    }
}
