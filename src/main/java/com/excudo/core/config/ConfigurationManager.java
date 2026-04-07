package com.excudo.core.config;

import com.excudo.core.utils.EnvLoader;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import java.io.*;
import java.util.Properties;

/**
 * Configuration manager for API keys, LLM provider selection, and user settings.
 * Integrates with EnvLoader for environment variables and provides persistent storage.
 *
 * Build-order constraint: this class compiles before core/llm/ and must NOT
 * import any core.llm types. Provider names are handled as raw strings here;
 * LLMClient.fromConfiguration() resolves them to LLMProvider enums.
 */
public class ConfigurationManager {

    private static final ComponentLogger logger = Logger.getLogger(ConfigurationManager.class);
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final String DEFAULT_PROVIDER = "anthropic";
    private static final String API_KEY_ENV = "ANTHROPIC_API_KEY";
    private static final String MODEL_ENV = "ANTHROPIC_MODEL";
    private static final String CONFIG_FILE = ".choreographer.properties";

    private Properties properties;
    private File configFile;

    public ConfigurationManager() {
        this.configFile = new File(System.getProperty("user.home"), CONFIG_FILE);
        this.properties = new Properties();
        loadConfiguration();
    }

    private void loadConfiguration() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                logger.warn("Could not load configuration file: {}", e.getMessage());
            }
        }
    }

    private void saveConfiguration() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "Excudo Configuration");
        } catch (IOException e) {
            logger.error("Could not save configuration file: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Provider-agnostic accessors (used by LLMClient.fromConfiguration)
    // ------------------------------------------------------------------

    /**
     * Get the active LLM provider name.
     * Checks: properties file -> LLM_PROVIDER env var -> default "anthropic".
     */
    public String getProviderName() {
        String provider = properties.getProperty("llm.provider");
        if (provider != null && !provider.isEmpty()) {
            return provider.toLowerCase();
        }

        String envProvider = EnvLoader.get("LLM_PROVIDER");
        if (envProvider != null && !envProvider.trim().isEmpty()) {
            return envProvider.trim().toLowerCase();
        }

        logger.info("No LLM provider configured -- defaulting to '{}'", DEFAULT_PROVIDER);
        return DEFAULT_PROVIDER;
    }

    /**
     * Set and persist the active LLM provider name.
     */
    public void setProviderName(String provider) {
        if (provider == null || provider.isEmpty()) {
            properties.remove("llm.provider");
        } else {
            properties.setProperty("llm.provider", provider.toLowerCase());
        }
        saveConfiguration();
    }

    /**
     * Get the API key for the currently active provider.
     * Checks: properties file ({provider}.api.key) -> provider-specific env var.
     */
    public String getProviderApiKey() {
        String provider = getProviderName();
        String propKey = provider + ".api.key";

        String apiKey = properties.getProperty(propKey);
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        return EnvLoader.get(getApiKeyEnvVar(provider));
    }

    /**
     * Get the model for the currently active provider.
     * Checks: properties file ({provider}.model) -> provider-specific env var -> hardcoded default.
     */
    public String getProviderModel() {
        String provider = getProviderName();
        String propKey = provider + ".model";

        String model = properties.getProperty(propKey);
        if (model != null && !model.isEmpty()) {
            return model;
        }

        String envModel = EnvLoader.get(getModelEnvVar(provider));
        if (envModel != null && !envModel.trim().isEmpty()) {
            return envModel;
        }

        return getDefaultModel(provider);
    }

    /**
     * Map provider name to its API key environment variable.
     * Hardcoded here (no core/llm imports allowed due to build order).
     */
    private String getApiKeyEnvVar(String provider) {
        return switch (provider) {
            case "anthropic" -> "ANTHROPIC_API_KEY";
            case "ollama"    -> "OLLAMA_API_KEY";
            case "openai"    -> "OPENAI_API_KEY";
            case "gemini"    -> "GEMINI_API_KEY";
            default          -> provider.toUpperCase() + "_API_KEY";
        };
    }

    /**
     * Map provider name to its model environment variable.
     */
    private String getModelEnvVar(String provider) {
        return switch (provider) {
            case "anthropic" -> "ANTHROPIC_MODEL";
            case "ollama"    -> "OLLAMA_MODEL";
            case "openai"    -> "OPENAI_MODEL";
            case "gemini"    -> "GEMINI_MODEL";
            default          -> provider.toUpperCase() + "_MODEL";
        };
    }

    /**
     * Default model per provider when nothing is configured.
     */
    private String getDefaultModel(String provider) {
        return switch (provider) {
            case "anthropic" -> DEFAULT_MODEL;
            case "openai"    -> "gpt-4o";
            case "gemini"    -> "gemini-pro";
            case "ollama"    -> "llama3";
            default          -> "default";
        };
    }

    // ------------------------------------------------------------------
    // Legacy Anthropic-specific accessors (backward compatibility)
    // ------------------------------------------------------------------

    /**
     * Get Anthropic API key. Retained for backward compatibility.
     */
    public String getApiKey() {
        String apiKey = properties.getProperty("anthropic.api.key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        return EnvLoader.get(API_KEY_ENV);
    }

    /**
     * Get Anthropic model name. Retained for backward compatibility.
     */
    public String getModel() {
        String model = properties.getProperty("anthropic.model");
        if (model != null && !model.isEmpty()) {
            return model;
        }
        String envModel = EnvLoader.get(MODEL_ENV);
        return envModel != null ? envModel : DEFAULT_MODEL;
    }

    /**
     * Check if an Anthropic API key is available. Retained for backward compatibility.
     */
    public boolean hasApiKey() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    public void setApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            properties.remove("anthropic.api.key");
        } else {
            properties.setProperty("anthropic.api.key", apiKey);
        }
        saveConfiguration();
    }

    public void setModel(String model) {
        if (model == null || model.isEmpty()) {
            properties.remove("anthropic.model");
        } else {
            properties.setProperty("anthropic.model", model);
        }
        saveConfiguration();
    }

    public void clearApiKey() {
        properties.remove("anthropic.api.key");
        saveConfiguration();
    }

    public void clearConfiguration() {
        properties.clear();
        saveConfiguration();
    }

    public String getConfigurationInstructions() {
        String provider = getProviderName();
        String envVar = getApiKeyEnvVar(provider);
        String modelVar = getModelEnvVar(provider);

        StringBuilder sb = new StringBuilder();
        sb.append("Active Provider: ").append(provider).append("\n\n");
        sb.append("API Key Configuration:\n");
        sb.append("1. Set via command: llm config set <api-key>\n");
        sb.append("2. Set environment variable: ").append(envVar).append("=<api-key>\n");
        sb.append("3. Add to .env file: ").append(envVar).append("=<api-key>\n");
        sb.append("\nModel Configuration:\n");
        sb.append("1. Set via command: llm model <model-name>\n");
        sb.append("2. Set environment variable: ").append(modelVar).append("=<model-name>\n");
        sb.append("3. Add to .env file: ").append(modelVar).append("=<model-name>\n");
        sb.append("\nProvider Selection:\n");
        sb.append("1. Set via command: llm config provider <name>\n");
        sb.append("2. Set environment variable: LLM_PROVIDER=<name>\n");
        sb.append("   Supported: anthropic, ollama, openai, gemini\n");
        sb.append("\nDefault model: ").append(getDefaultModel(provider));
        sb.append("\nConfiguration file: ").append(configFile.getAbsolutePath());
        return sb.toString();
    }

    public String getConfigurationStatus() {
        String provider = getProviderName();
        String providerKey = getProviderApiKey();
        boolean hasKey = providerKey != null && !providerKey.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Status:\n");
        sb.append("Provider: ").append(provider).append("\n");
        sb.append("API Key: ").append(hasKey ? "[OK] Configured" : "[FAIL] Not configured").append("\n");
        sb.append("Model: ").append(getProviderModel()).append("\n");

        if (hasKey) {
            String propKey = provider + ".api.key";
            String source = properties.containsKey(propKey) ? "configuration file" :
                           properties.containsKey("anthropic.api.key") ? "configuration file (legacy)" :
                           EnvLoader.has(getApiKeyEnvVar(provider)) ? "environment/file" : "unknown";
            sb.append("API Key source: ").append(source).append("\n");
        }

        return sb.toString();
    }
}
