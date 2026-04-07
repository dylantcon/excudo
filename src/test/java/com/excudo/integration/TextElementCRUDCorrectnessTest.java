package com.excudo.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * CRUD correctness tests for textel-crud category.
 * Stub -- will be populated when text element pipeline output is available.
 */
public class TextElementCRUDCorrectnessTest extends CRUDCorrectnessBase {

    @Override
    protected String category() {
        return "textel-crud";
    }

    @Test
    public void placeholder() {
        Assumptions.assumeTrue(false, "textel-crud not yet populated");
    }
}
