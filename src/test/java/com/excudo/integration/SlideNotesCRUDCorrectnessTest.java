package com.excudo.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * CRUD correctness tests for slidenotes-crud category.
 * Stub -- will be populated when slide notes pipeline output is available.
 */
public class SlideNotesCRUDCorrectnessTest extends CRUDCorrectnessBase {

    @Override
    protected String category() {
        return "slidenotes-crud";
    }

    @Test
    public void placeholder() {
        Assumptions.assumeTrue(false, "slidenotes-crud not yet populated");
    }
}
