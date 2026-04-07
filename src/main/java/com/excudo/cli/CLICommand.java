package com.excudo.cli;

/**
 * Represents a parsed CLI command with all its parameters.
 */
public class CLICommand {
    private final String action;
    private final String inputFile;
    private final String outputFile;
    private final String llmCommand;
    private final String testName;
    private final boolean headless;
    private final boolean interactive;
    
    private CLICommand(Builder builder) {
        this.action = builder.action;
        this.inputFile = builder.inputFile;
        this.outputFile = builder.outputFile;
        this.llmCommand = builder.llmCommand;
        this.testName = builder.testName;
        this.headless = builder.headless;
        this.interactive = builder.interactive;
    }
    
    // Getters
    public String getAction() { return action; }
    public String getInputFile() { return inputFile; }
    public String getOutputFile() { return outputFile; }
    public String getLlmCommand() { return llmCommand; }
    public String getTestName() { return testName; }
    public boolean isHeadless() { return headless; }
    public boolean isInteractive() { return interactive; }
    
    public static class Builder {
        private String action;
        private String inputFile;
        private String outputFile;
        private String llmCommand;
        private String testName;
        private boolean headless = false;
        private boolean interactive = false;
        
        public Builder action(String action) {
            this.action = action;
            return this;
        }
        
        public Builder inputFile(String inputFile) {
            this.inputFile = inputFile;
            return this;
        }
        
        public Builder outputFile(String outputFile) {
            this.outputFile = outputFile;
            return this;
        }
        
        public Builder llmCommand(String llmCommand) {
            this.llmCommand = llmCommand;
            return this;
        }
        
        public Builder testName(String testName) {
            this.testName = testName;
            return this;
        }
        
        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }
        
        public Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }
        
        // Getters for builder state (needed for parsing)
        public String getInputFile() { return inputFile; }
        public String getOutputFile() { return outputFile; }
        
        public CLICommand build() {
            if (action == null || action.trim().isEmpty()) {
                throw new IllegalArgumentException("Action is required");
            }
            return new CLICommand(this);
        }
    }
    
    @Override
    public String toString() {
        return "CLICommand{" +
                "action='" + action + '\'' +
                ", inputFile='" + inputFile + '\'' +
                ", outputFile='" + outputFile + '\'' +
                ", llmCommand='" + llmCommand + '\'' +
                ", testName='" + testName + '\'' +
                ", headless=" + headless +
                ", interactive=" + interactive +
                '}';
    }
}