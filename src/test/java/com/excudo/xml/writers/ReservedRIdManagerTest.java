package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReservedRIdManager - the PowerPoint rId allocation pipeline.
 *
 * Tests cover:
 * - Initial rId array setup via PPTXDocument (in-memory)
 * - Single operation queuing
 * - Batch operation queuing
 * - Pipeline execution
 * - rId retrieval methods
 * - Edge cases and error handling
 */
class ReservedRIdManagerTest {

    private static final String MINIMAL_RELS_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/customXml" Target="../customXml/item1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/customXml" Target="../customXml/item2.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/customXml" Target="../customXml/item3.xml"/>
          <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
          <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
          <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
          <Relationship Id="rId7" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
          <Relationship Id="rId8" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster" Target="notesMasters/notesMaster1.xml"/>
        </Relationships>
        """;

    private ReservedRIdManager ridManager;
    private PPTXDocument pptxDocument;

    @BeforeEach
    void setUp() throws Exception {
        pptxDocument = buildPptxDocument(MINIMAL_RELS_XML);
        ridManager = new ReservedRIdManager(3, pptxDocument);
    }

    private PPTXDocument buildPptxDocument(String relsXml) throws Exception {
        DocumentBuilder db = XMLFactoryProvider.createDocumentBuilder();
        Document relsDoc = db.parse(new InputSource(new StringReader(relsXml)));
        PPTXDocument doc = PPTXDocument.createEmpty();
        doc.putXmlPart("ppt/_rels/presentation.xml.rels", relsDoc);
        return doc;
    }

    // ========== INITIALIZATION TESTS ==========

    @Test
    @DisplayName("Initialize with valid slide count")
    void testInitializeWithValidSlideCount() throws Exception {
        int initialSlideCount = 5;
        ReservedRIdManager manager = new ReservedRIdManager(initialSlideCount, pptxDocument);

        assertEquals(initialSlideCount, manager.getCurrentSlideCount(), "Should have correct initial slide count");
        assertNotNull(manager.getReservedRIdArray(), "Should have initialized rId array");
        assertTrue(manager.getReservedRIdArray().length > initialSlideCount, "Array should be larger than slide count");
    }

    @Test
    @DisplayName("Initialize with zero slides")
    void testInitializeWithZeroSlides() {
        assertDoesNotThrow(() -> {
            ReservedRIdManager manager = new ReservedRIdManager(0, pptxDocument);
            assertEquals(0, manager.getCurrentSlideCount(), "Should handle zero slides");
        }, "Should not throw exception for zero slides");
    }

    @Test
    @DisplayName("Get rId for valid slide position")
    void testGetRIdForValidSlidePosition() {
        String rId = ridManager.getRIdForSlidePosition(1);

        assertNotNull(rId, "Should return rId for valid position");
        assertTrue(rId.startsWith("rId"), "Should be valid rId format");
    }

    @Test
    @DisplayName("Get rId for multiple slide positions - sequential")
    void testGetRIdForMultipleSlidePositions() {
        String rId1 = ridManager.getRIdForSlidePosition(1);
        String rId2 = ridManager.getRIdForSlidePosition(2);
        String rId3 = ridManager.getRIdForSlidePosition(3);

        assertNotNull(rId1, "First slide should have rId");
        assertNotNull(rId2, "Second slide should have rId");
        assertNotNull(rId3, "Third slide should have rId");

        int rId1Num = Integer.parseInt(rId1.substring(3));
        int rId2Num = Integer.parseInt(rId2.substring(3));
        int rId3Num = Integer.parseInt(rId3.substring(3));

        assertEquals(rId1Num + 1, rId2Num, "rIds should be sequential");
        assertEquals(rId2Num + 1, rId3Num, "rIds should be sequential");
    }

    // ========== OPERATION QUEUING TESTS ==========

    @Test
    @DisplayName("Queue single operation")
    void testQueueSingleOperation() {
        SlideAction operation = new SlideAction("op-001", 2, "BLANK", "Test Slide");
        ridManager.queueSingleOperation(operation);

        assertTrue(ridManager.hasPendingOperations(), "Should have pending operations");
        assertEquals(1, ridManager.getPendingBatchCount(), "Should have one batch");
    }

    @Test
    @DisplayName("Queue multiple single operations")
    void testQueueMultipleSingleOperations() {
        SlideAction op1 = new SlideAction("op-001", 1, "BLANK");
        SlideAction op2 = new SlideAction("op-002", 3, "TITLE_CONTENT");

        ridManager.queueSingleOperation(op1);
        ridManager.queueSingleOperation(op2);

        assertTrue(ridManager.hasPendingOperations(), "Should have pending operations");
        assertEquals(2, ridManager.getPendingBatchCount(), "Should have two batches");
    }

    @Test
    @DisplayName("Queue batch operation")
    void testQueueBatchOperation() {
        SlideAction[] batch = {
            new SlideAction("op-001", 1, "BLANK", "Slide 1"),
            new SlideAction("op-002", 2, "BLANK", "Slide 2"),
            new SlideAction("op-003", 3, "TITLE_CONTENT", "Slide 3")
        };
        ridManager.queueBatchOperation(batch);

        assertTrue(ridManager.hasPendingOperations(), "Should have pending operations");
        assertEquals(1, ridManager.getPendingBatchCount(), "Should have one batch");
    }

    @Test
    @DisplayName("Queue empty batch operation throws IllegalArgumentException")
    void testQueueEmptyBatchOperation() {
        SlideAction[] emptyBatch = {};
        assertThrows(IllegalArgumentException.class, () -> {
            ridManager.queueBatchOperation(emptyBatch);
        }, "Should throw exception for empty batch");
    }

    @Test
    @DisplayName("Queue null batch operation throws IllegalArgumentException")
    void testQueueNullBatchOperation() {
        assertThrows(IllegalArgumentException.class, () -> {
            ridManager.queueBatchOperation(null);
        }, "Should throw exception for null batch");
    }

    // ========== PIPELINE EXECUTION TESTS ==========

    @Test
    @DisplayName("Execute pipeline with single operation")
    void testExecutePipelineWithSingleOperation() {
        SlideAction operation = new SlideAction("op-001", 1, "BLANK", "Test Slide");
        ridManager.queueSingleOperation(operation);

        ridManager.executeOperationPipeline();

        assertFalse(ridManager.hasPendingOperations(), "Should have no pending operations after execution");
        assertEquals(0, ridManager.getPendingBatchCount(), "Should have no pending batches");
    }

    @Test
    @DisplayName("Execute pipeline with batch operation")
    void testExecutePipelineWithBatchOperation() {
        SlideAction[] batch = {
            new SlideAction("op-001", 1, "BLANK"),
            new SlideAction("op-002", 2, "TITLE_CONTENT")
        };
        ridManager.queueBatchOperation(batch);

        ridManager.executeOperationPipeline();

        assertFalse(ridManager.hasPendingOperations(), "Should have no pending operations after execution");
        assertEquals(0, ridManager.getPendingBatchCount(), "Should have no pending batches");
    }

    @Test
    @DisplayName("Execute empty pipeline gracefully")
    void testExecuteEmptyPipeline() {
        assertDoesNotThrow(() -> {
            ridManager.executeOperationPipeline();
            assertFalse(ridManager.hasPendingOperations(), "Should have no pending operations");
        }, "Should handle empty pipeline execution gracefully");
    }

    // ========== RID ARRAY TESTS ==========

    @Test
    @DisplayName("Get reserved rId array is non-empty")
    void testGetReservedRIdArray() {
        String[] rIdArray = ridManager.getReservedRIdArray();

        assertNotNull(rIdArray, "Should return rId array");
        assertTrue(rIdArray.length > 0, "Array should not be empty");
    }

    @Test
    @DisplayName("rId array entries are valid relationship targets")
    void testRIdArrayStructure() {
        String[] rIdArray = ridManager.getReservedRIdArray();

        for (int i = 0; i < rIdArray.length; i++) {
            if (rIdArray[i] != null) {
                assertTrue(rIdArray[i].contains(".xml"),
                    String.format("Array[%d] should be a relationship target ending in .xml, was: %s", i, rIdArray[i]));
            }
        }
    }

    // ========== EDGE CASES AND ERROR HANDLING ==========

    @Test
    @DisplayName("Handle invalid slide position 0 throws IllegalArgumentException")
    void testInvalidSlidePosition_zero() {
        assertThrows(IllegalArgumentException.class, () -> {
            ridManager.getRIdForSlidePosition(0);
        }, "Should throw exception for invalid position 0");
    }

    @Test
    @DisplayName("Handle invalid slide position -1 throws IllegalArgumentException")
    void testInvalidSlidePosition_negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            ridManager.getRIdForSlidePosition(-1);
        }, "Should throw exception for negative position");
    }

    @Test
    @DisplayName("Handle large out-of-bounds slide position without exception")
    void testOutOfBoundsSlidePosition() {
        assertDoesNotThrow(() -> {
            String rId = ridManager.getRIdForSlidePosition(1000);
            assertNotNull(rId);
        }, "Should handle large position");
    }

    @Test
    @DisplayName("Handle null slide operation -- executeOperationPipeline throws NullPointerException")
    void testNullSlideAction() {
        ridManager.queueSingleOperation(null);

        assertThrows(NullPointerException.class, () -> {
            ridManager.executeOperationPipeline();
        }, "Should throw NullPointerException for null operation");
    }

    @Test
    @DisplayName("Slide count does not change until pipeline executes")
    void testCurrentSlideCountTracking() {
        int initialCount = ridManager.getCurrentSlideCount();

        ridManager.queueSingleOperation(new SlideAction("op-001", 1, "BLANK"));
        ridManager.queueSingleOperation(new SlideAction("op-002", 4, "BLANK"));

        assertEquals(initialCount, ridManager.getCurrentSlideCount(),
            "Slide count should not change until pipeline execution");
    }
}
