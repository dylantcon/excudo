package com.excudo.core.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests ConfigurationManager basic accessors and status reporting.
 * Every test uses a temp config file -- never the developer's real
 * ~/.choreographer.properties.
 */
public class ConfigurationManagerTest {

    private File configFile;

    @Before
    public void setUp() throws IOException {
        configFile = File.createTempFile("excudo-config-test", ".properties");
        configFile.delete(); // start with no file, like a fresh install
    }

    @After
    public void tearDown() {
        configFile.delete();
    }

    private ConfigurationManager newConfig() {
        return new ConfigurationManager(configFile);
    }

    @Test
    public void constructorDoesNotThrow() {
        assertNotNull(newConfig());
    }

    @Test
    public void getProviderNameReturnsNonNull() {
        assertNotNull(newConfig().getProviderName());
    }

    @Test
    public void getConfigurationStatusReturnsNonEmpty() {
        String status = newConfig().getConfigurationStatus();
        assertNotNull(status);
        assertTrue(status.length() > 0);
    }

    @Test
    public void getConfigurationInstructionsReturnsNonEmpty() {
        String instructions = newConfig().getConfigurationInstructions();
        assertNotNull(instructions);
        assertTrue("Instructions should contain provider info", instructions.length() > 10);
    }

    @Test
    public void setAndGetModelRoundTrips() {
        newConfig().setModel("test-model-xyz");
        assertEquals("test-model-xyz", newConfig().getModel());
    }

    @Test
    public void setProviderNameRoundTrips() {
        newConfig().setProviderName("test-provider");
        assertEquals("test-provider", newConfig().getProviderName());
    }
}
