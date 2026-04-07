package com.excudo.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * CRUD correctness tests for theme-crud category.
 * Stub -- will be populated when theme/layout pipeline output is available.
 */
public class ThemeCRUDCorrectnessTest extends CRUDCorrectnessBase {

    @Override
    protected String category() {
        return "theme-crud";
    }

    @Test
    public void placeholder() {
        Assumptions.assumeTrue(false, "theme-crud not yet populated");
    }
}
