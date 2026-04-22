package com.excudo.view;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test that loads the root FXML end-to-end. After Tier 6.1 modularization
 * the root depends on five fx:include children; a broken sub-FXML or mis-named
 * auto-injected controller surfaces as a load-time failure. This test catches
 * those without needing a full GUI harness.
 */
public class FxmlIncludeSmokeTest {

    @BeforeAll
    static void initJfx() {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        try {
            new JFXPanel();
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "JavaFX unavailable in this environment: " + e.getMessage());
        }
    }

    @Test
    void loadsMainApplicationFxmlIncludingAllPanels() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = { null };
        Parent[] root = { null };
        MainController[] controller = { null };

        Platform.runLater(() -> {
            try {
                URL url = FxmlIncludeSmokeTest.class.getResource("/fxml/MainApplication.fxml");
                assertNotNull(url, "MainApplication.fxml missing from classpath");
                FXMLLoader loader = new FXMLLoader(url);
                root[0] = loader.load();
                controller[0] = loader.getController();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "FXML load timed out");
        if (error[0] != null) {
            error[0].printStackTrace();
            fail("FXML load failed: " + error[0].getMessage());
        }
        assertNotNull(root[0], "Root node not produced");
        assertNotNull(controller[0], "Root controller not produced");
    }
}
