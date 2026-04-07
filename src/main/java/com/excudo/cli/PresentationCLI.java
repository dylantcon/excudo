package com.excudo.cli;

import com.excudo.xml.writers.SPIDManager;
import com.excudo.core.llm.LLMIntegrationService;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

import java.io.File;
import java.util.*;

/**
 * Unified CLI interface for Excudo.
 * Provides cross-platform command-line access to all functionality.
 */
public class PresentationCLI {
    
    private static final ComponentLogger logger = Logger.cli();
    private static final String VERSION = "1.0.0";
    private static boolean verboseMode = false;
    
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                showHelp();
                return;
            }
            
            CLICommand command = parseCommand(args);
            executeCommand(command);
            
        } catch (Exception e) {
            logger.error("CLI error: {}", e.getMessage());
            if (verboseMode) {
                logger.error("Full stack trace:", e);
            }
            System.exit(1);
        }
    }
    
    private static CLICommand parseCommand(String[] args) {
        CLICommand.Builder builder = new CLICommand.Builder();
        
        // Parse global flags first
        List<String> commandArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--verbose") || arg.equals("-v")) {
                verboseMode = true;
            } else if (arg.equals("--help") || arg.equals("-h")) {
                showHelp();
                System.exit(0);
            } else if (arg.equals("--version")) {
                logger.info("Excudo CLI v{}", VERSION);
                System.exit(0);
            } else {
                commandArgs.add(arg);
            }
        }
        
        if (commandArgs.isEmpty()) {
            throw new IllegalArgumentException("No command specified");
        }
        
        String action = commandArgs.get(0);
        builder.action(action);
        
        // Parse command-specific arguments
        for (int i = 1; i < commandArgs.size(); i++) {
            String arg = commandArgs.get(i);
            if (arg.startsWith("--input=") || arg.startsWith("-i=")) {
                builder.inputFile(arg.split("=", 2)[1]);
            } else if (arg.startsWith("--output=") || arg.startsWith("-o=")) {
                builder.outputFile(arg.split("=", 2)[1]);
            } else if (arg.startsWith("--command=") || arg.startsWith("-c=")) {
                builder.llmCommand(arg.split("=", 2)[1]);
            } else if (arg.startsWith("--test-name=")) {
                builder.testName(arg.split("=", 2)[1]);
            } else if (arg.equals("--headless")) {
                builder.headless(true);
            } else if (arg.equals("--interactive")) {
                builder.interactive(true);
            } else {
                // Positional arguments
                if (builder.getInputFile() == null) {
                    builder.inputFile(arg);
                } else if (builder.getOutputFile() == null) {
                    builder.outputFile(arg);
                }
            }
        }
        
        return builder.build();
    }
    
    private static void executeCommand(CLICommand command) throws Exception {
        log("Executing command: " + command.getAction());
        
        switch (command.getAction().toLowerCase()) {
            case "test":
                executeTest(command);
                break;
            case "demo":
                executeDemo(command);
                break;
            case "validate":
                executeValidate(command);
                break;
            case "info":
                executeInfo(command);
                break;
            case "interactive":
                executeInteractive();
                break;
            default:
                throw new IllegalArgumentException("Unknown command: " + command.getAction());
        }
    }
    
    private static void executeTest(CLICommand command) throws Exception {
        TestRunner runner = new TestRunner(verboseMode);
        
        if (command.getTestName() != null) {
            log("Running specific test: " + command.getTestName());
            runner.runTest(command.getTestName());
        } else {
            log("Running all tests");
            runner.runAllTests();
        }
    }
    
    private static void executeDemo(CLICommand command) throws Exception {
        log("Running SPID allocation demo");
        
        // Run a basic demo using existing functionality
        logger.info("Excudo Demo");
        logger.info("==============================");
        
        SPIDManager spidManager = SPIDManager.getInstance();
        
        logger.info("Testing SPID allocation:");
        for (int i = 1; i <= 5; i++) {
            int spid = spidManager.allocateSpidForShape("custom", 1, false, false, null);
            logger.info("  Shape {}: SPID {}", i, spid);
        }
        
        logger.info("[OK] Demo completed successfully");
    }
    
    private static void executeValidate(CLICommand command) throws Exception {
        if (command.getInputFile() == null) {
            throw new IllegalArgumentException("Input file required for validate command");
        }
        
        log("Validating presentation: " + command.getInputFile());
        
        File inputFile = new File(command.getInputFile());
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + command.getInputFile());
        }
        
        logger.info("[OK] File exists and is readable");
        // Basic validation for now
    }
    
    private static void executeInfo(CLICommand command) throws Exception {
        if (command.getInputFile() == null) {
            throw new IllegalArgumentException("Input file required for info command");
        }
        
        log("Analyzing presentation: " + command.getInputFile());
        
        File inputFile = new File(command.getInputFile());
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + command.getInputFile());
        }
        
        // Print basic file info
        logger.info("Presentation Information:");
        logger.info("========================");
        logger.info("File: {}", command.getInputFile());
        logger.info("Size: {} bytes", inputFile.length());
        logger.info("Last modified: {}", new Date(inputFile.lastModified()));
    }
    
    private static void executeInteractive() {
        logger.info("Excudo Interactive Mode");
        logger.info("==========================================");
        logger.info("Type 'help' for commands, 'exit' to quit");
        
        Scanner scanner = new Scanner(System.in);
        String currentFile = null;
        
        while (true) {
            System.out.print(currentFile != null ? 
                "[" + new File(currentFile).getName() + "] > " : "> ");
            
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();
            
            try {
                switch (cmd) {
                    case "exit":
                    case "quit":
                        return;
                    case "help":
                        showInteractiveHelp();
                        break;
                    case "load":
                        if (parts.length < 2) {
                            logger.info("Usage: load <file.pptx>");
                            break;
                        }
                        currentFile = parts[1];
                        File file = new File(currentFile);
                        if (file.exists()) {
                            logger.info("[OK] Loaded: {}", currentFile);
                        } else {
                            logger.warn("[FAIL] File not found: {}", currentFile);
                            currentFile = null;
                        }
                        break;
                    case "info":
                        if (currentFile == null) {
                            logger.info("No presentation loaded");
                            break;
                        }
                        File infoFile = new File(currentFile);
                        logger.info("File: {}", currentFile);
                        logger.info("Size: {} bytes", infoFile.length());
                        break;
                    case "test":
                        if (parts.length > 1) {
                            TestRunner runner = new TestRunner(verboseMode);
                            runner.runTest(parts[1]);
                        } else {
                            logger.info("Usage: test <test-name>");
                        }
                        break;
                    default:
                        logger.warn("Unknown command: {}. Type 'help' for commands.", cmd);
                }
            } catch (Exception e) {
                logger.error("Interactive command error: {}", e.getMessage());
                if (verboseMode) {
                    logger.error("Full stack trace:", e);
                }
            }
        }
    }
    
    private static void showHelp() {
        logger.info("Excudo CLI v{}", VERSION);
        logger.info("===============================================");
        logger.info("");
        logger.info("USAGE:");
        logger.info("  java -cp ... PresentationCLI <command> [options]");
        logger.info("");
        logger.info("COMMANDS:");
        logger.info("  test [--test-name=<name>]      Run tests");
        logger.info("  demo                           Run demonstration");
        logger.info("  validate <input.pptx>          Basic file validation");
        logger.info("  info <input.pptx>              Show file info");
        logger.info("  interactive                    Interactive mode");
        logger.info("");
        logger.info("OPTIONS:");
        logger.info("  --input=<file>, -i=<file>      Input file");
        logger.info("  --output=<file>, -o=<file>     Output file"); 
        logger.info("  --command=<cmd>, -c=<cmd>      LLM command");
        logger.info("  --test-name=<name>             Specific test to run");
        logger.info("  --headless                     Headless mode (no GUI)");
        logger.info("  --interactive                  Interactive mode");
        logger.info("  --verbose, -v                  Verbose output");
        logger.info("  --help, -h                     Show this help");
        logger.info("  --version                      Show version");
        logger.info("");
        logger.info("EXAMPLES:");
        logger.info("  # Run all tests");
        logger.info("  java -cp ... PresentationCLI test");
        logger.info("");
        logger.info("  # Run specific test");
        logger.info("  java -cp ... PresentationCLI test --test-name=spid-allocation");
        logger.info("");
        logger.info("  # Interactive mode");
        logger.info("  java -cp ... PresentationCLI interactive");
    }
    
    private static void showInteractiveHelp() {
        logger.info("Interactive Commands:");
        logger.info("  load <file.pptx>    Load presentation");
        logger.info("  info                Show file info");
        logger.info("  test <name>         Run specific test");
        logger.info("  help                Show this help");
        logger.info("  exit, quit          Exit interactive mode");
    }
    
    private static void log(String message) {
        if (verboseMode) {
            logger.debug(message);
        }
    }
}