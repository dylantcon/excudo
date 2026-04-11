package com.excudo.view.rendering;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.utils.XMLFactoryProvider;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.*;

/**
 * Tests ShapeStyleExtractor fill/line/color resolution.
 * Validates the OOXML style hierarchy: spPr solidFill > p:style fillRef > throw.
 */
public class ShapeStyleExtractorTest {

    /**
     * Shape with explicit solidFill in spPr should resolve to that color,
     * ignoring any nested solidFill elements deeper in the DOM.
     */
    @Test
    public void resolveFillColor_explicitSolidFill() throws Exception {
        String xml = """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:nvSpPr>
                    <p:cNvPr id="4" name="Rect"/>
                    <p:cNvSpPr/>
                    <p:nvPr/>
                </p:nvSpPr>
                <p:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm>
                    <a:solidFill><a:srgbClr val="FF0000"/></a:solidFill>
                </p:spPr>
            </p:sp>
            """;

        SlideShape shape = parseShape(xml, SlideShape.ShapeType.RECTANGLE);
        Paint fill = ShapeStyleExtractor.resolveFillColor(shape, null);

        assertEquals(Color.web("#FF0000"), fill);
    }

    /**
     * Shape with noFill in spPr should resolve to transparent.
     */
    @Test
    public void resolveFillColor_noFill() throws Exception {
        String xml = """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:nvSpPr>
                    <p:cNvPr id="4" name="Rect"/>
                    <p:cNvSpPr/>
                    <p:nvPr/>
                </p:nvSpPr>
                <p:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm>
                    <a:noFill/>
                </p:spPr>
            </p:sp>
            """;

        SlideShape shape = parseShape(xml, SlideShape.ShapeType.RECTANGLE);
        Paint fill = ShapeStyleExtractor.resolveFillColor(shape, null);

        assertEquals(Color.TRANSPARENT, fill);
    }

    /**
     * Shape with p:style fillRef schemeClr should resolve through the theme.
     * Requires ThemeManager to be loaded.
     */
    @Test
    public void resolveFillColor_fillRefSchemeClr() throws Exception {
        // Load the generalist test file's theme so ThemeManager has accent1
        java.io.File testFile = new java.io.File("test-pptx-samples/generalist_test_file.pptx");
        if (!testFile.exists()) {
            // Running from worktree — adjust path
            testFile = new java.io.File("../../../test-pptx-samples/generalist_test_file.pptx");
        }
        if (!testFile.exists()) return; // skip if test file unavailable

        com.excudo.core.model.PPTXDocument doc =
            com.excudo.core.model.PPTXDocument.loadFromZip(testFile);
        ThemeManager.initialize(doc);

        // clrMap for this file: accent1 maps to itself
        java.util.Map<String, String> clrMap = java.util.Map.of(
            "accent1", "accent1", "tx1", "lt1", "bg1", "dk1");
        SlideRenderContext ctx = new SlideRenderContext(
            null, null, doc, 1, clrMap, null);

        String xml = """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:nvSpPr>
                    <p:cNvPr id="4" name="Rect"/>
                    <p:cNvSpPr/>
                    <p:nvPr/>
                </p:nvSpPr>
                <p:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm>
                </p:spPr>
                <p:style>
                    <a:fillRef idx="1"><a:schemeClr val="accent1"/></a:fillRef>
                </p:style>
            </p:sp>
            """;

        SlideShape shape = parseShape(xml, SlideShape.ShapeType.RECTANGLE);
        Paint fill = ShapeStyleExtractor.resolveFillColor(shape, ctx);

        // accent1 in generalist_test_file.pptx is #569CD6
        assertTrue("Fill should be a Color", fill instanceof Color);
        Color c = (Color) fill;
        assertEquals("accent1 should be #569CD6",
            Color.web("#569CD6").toString(), c.toString());
    }

    /**
     * getChild must match direct children only — not descendants.
     * A shape with solidFill nested inside a gradient stop should NOT
     * have that inner solidFill picked up as the shape's fill.
     */
    @Test
    public void resolveFillColor_gradFillNotConfusedWithNestedSolidFill() throws Exception {
        String xml = """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:nvSpPr>
                    <p:cNvPr id="4" name="Rect"/>
                    <p:cNvSpPr/>
                    <p:nvPr/>
                </p:nvSpPr>
                <p:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm>
                    <a:gradFill>
                        <a:gsLst>
                            <a:gs pos="0"><a:srgbClr val="FF0000"/></a:gs>
                            <a:gs pos="100000"><a:srgbClr val="0000FF"/></a:gs>
                        </a:gsLst>
                    </a:gradFill>
                </p:spPr>
            </p:sp>
            """;

        SlideShape shape = parseShape(xml, SlideShape.ShapeType.RECTANGLE);
        Paint fill = ShapeStyleExtractor.resolveFillColor(shape, null);

        // Should be a gradient, NOT a solid red (which would happen if
        // getChild searched descendants and found the srgbClr inside a:gs)
        assertTrue("gradFill should produce a LinearGradient, not " + fill.getClass().getSimpleName(),
            fill instanceof javafx.scene.paint.LinearGradient);
    }

    /**
     * Placeholder shapes should resolve to transparent (inherit from layout/master).
     */
    @Test
    public void resolveFillColor_placeholderIsTransparent() throws Exception {
        String xml = """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:nvSpPr>
                    <p:cNvPr id="2" name="Title"/>
                    <p:cNvSpPr/>
                    <p:nvPr><p:ph type="title"/></p:nvPr>
                </p:nvSpPr>
                <p:spPr/>
            </p:sp>
            """;

        SlideShape shape = parseShape(xml, SlideShape.ShapeType.PLACEHOLDER);
        Paint fill = ShapeStyleExtractor.resolveFillColor(shape, null);

        assertEquals(Color.TRANSPARENT, fill);
    }

    /**
     * Shape with solidFill + alpha child should apply opacity.
     */
    @Test
    public void resolveFillColor_solidFillWithAlpha() throws Exception {
        String xml = """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:nvSpPr>
                    <p:cNvPr id="4" name="Rect"/>
                    <p:cNvSpPr/>
                    <p:nvPr/>
                </p:nvSpPr>
                <p:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm>
                    <a:solidFill>
                        <a:srgbClr val="00FF00">
                            <a:alpha val="50000"/>
                        </a:srgbClr>
                    </a:solidFill>
                </p:spPr>
            </p:sp>
            """;

        SlideShape shape = parseShape(xml, SlideShape.ShapeType.RECTANGLE);
        Paint fill = ShapeStyleExtractor.resolveFillColor(shape, null);

        assertTrue(fill instanceof Color);
        Color c = (Color) fill;
        assertEquals(0.0, c.getRed(), 0.01);
        assertEquals(1.0, c.getGreen(), 0.01);
        assertEquals(0.0, c.getBlue(), 0.01);
        assertEquals(0.5, c.getOpacity(), 0.01);
    }

    // ========== HELPERS ==========

    private SlideShape parseShape(String xml, SlideShape.ShapeType type) throws Exception {
        Document doc = XMLFactoryProvider.parseDocument(xml);
        Element el = doc.getDocumentElement();
        return new SlideShape(99, "TestShape", type, null,
            new ShapeGeometry(0, 0, 100, 100), el, null);
    }
}
