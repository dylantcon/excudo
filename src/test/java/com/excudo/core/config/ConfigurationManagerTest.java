package com.excudo.core.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ConfigurationManager basic accessors and status reporting.
 * Note: ConfigurationManager reads from env vars and persisted config,
 * so tests focus on accessors and status output rather than mutation.
 */
public class ConfigurationManagerTest {

    @Test
    public void constructorDoesNotThrow() {
        ConfigurationManager config = new ConfigurationManager();
        assertNotNull(config);
    }

    @Test
    public void getProviderNameReturnsNonNull() {
        ConfigurationManager config = new ConfigurationManager();
        // Should return configured provider or a default
        String provider = config.getProviderName();
        assertNotNull(provider);
    }

    @Test
    public void getConfigurationStatusReturnsNonEmpty() {
        ConfigurationManager config = new ConfigurationManager();
        String status = config.getConfigurationStatus();
        assertNotNull(status);
        assertTrue(status.length() > 0);
    }

    @Test
    public void getConfigurationInstructionsReturnsNonEmpty() {
        ConfigurationManager config = new ConfigurationManager();
        String instructions = config.getConfigurationInstructions();
        assertNotNull(instructions);
        assertTrue("Instructions should contain provider info", instructions.length() > 10);
    }

    @Test
    public void setAndGetModelRoundTrips() {
        ConfigurationManager config = new ConfigurationManager();
        String original = config.getModel();

        config.setModel("test-model-xyz");
        assertEquals("test-model-xyz", config.getModel());

        // Restore
        if (original != null) config.setModel(original);
    }

    @Test
    public void setProviderNameRoundTrips() {
        ConfigurationManager config = new ConfigurationManager();
        String original = config.getProviderName();

        config.setProviderName("test-provider");
        assertEquals("test-provider", config.getProviderName());

        // Restore
        if (original != null) config.setProviderName(original);
    }
}
