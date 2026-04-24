package com.excudo.view.rendering;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Exercises {@link SlideRenderContext#resolveInheritedBodyPrAnchor} against
 * synthetic layout and master parts. Regression guard for the Uncertainty
 * Quantification deck bug where title placeholders with negative-Y offsets
 * rendered clipped because the master's {@code anchor="ctr"} wasn't
 * inherited when the slide's bodyPr omitted the attribute.
 */
public class SlideRenderContextAnchorInheritanceTest {

    private PPTXDocument document;

    @Before
    public void setup() throws Exception {
        document = PPTXDocument.createEmpty();

        // Minimal master with a title placeholder carrying anchor="ctr"
        String masterXml = """
            <p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                         xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld><p:spTree>
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/>
                    <p:nvPr><p:ph type="title"/></p:nvPr>
                  </p:nvSpPr>
                  <p:spPr/>
                  <p:txBody>
                    <a:bodyPr anchor="ctr"/>
                    <a:lstStyle/>
                    <a:p><a:r><a:t/></a:r></a:p>
                  </p:txBody>
                </p:sp>
              </p:spTree></p:cSld>
            </p:sldMaster>
            """;

        // Minimal layout whose title ph has no bodyPr anchor -- inheritance
        // must fall through to the master's value.
        String layoutXml = """
            <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                         xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld><p:spTree>
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/>
                    <p:nvPr><p:ph type="title"/></p:nvPr>
                  </p:nvSpPr>
                  <p:spPr/>
                  <p:txBody>
                    <a:bodyPr/>
                    <a:lstStyle/>
                    <a:p><a:r><a:t/></a:r></a:p>
                  </p:txBody>
                </p:sp>
              </p:spTree></p:cSld>
            </p:sldLayout>
            """;

        // Layout's .rels points at the master
        String layoutRelsXml = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster"
                Target="../slideMasters/slideMaster1.xml"/>
            </Relationships>
            """;

        document.putXmlPart("ppt/slideMasters/slideMaster1.xml", parse(masterXml));
        document.putXmlPart("ppt/slideLayouts/slideLayout1.xml", parse(layoutXml));
        document.putXmlPart("ppt/slideLayouts/_rels/slideLayout1.xml.rels", parse(layoutRelsXml));
    }

    @Test
    public void inheritsAnchorFromMaster_whenLayoutAndSlideOmitIt() {
        LayoutInfo layout = new LayoutInfo("slideLayout1", "Title Slide",
            "slideLayouts/slideLayout1.xml", true, false, false, 0,
            "Title slide", Collections.emptyList(), "title");
        SlideRenderContext ctx = new SlideRenderContext(null, layout, document, 1,
            Collections.emptyMap(), "#FFFFFF");

        String anchor = ctx.resolveInheritedBodyPrAnchor("title", null);
        assertEquals("ctr", anchor);
    }

    @Test
    public void returnsNullWhenNoAncestorSpecifiesAnchor() throws Exception {
        // Swap in a master whose title ph also omits anchor
        String masterNoAnchor = """
            <p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                         xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld><p:spTree>
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/>
                    <p:nvPr><p:ph type="title"/></p:nvPr>
                  </p:nvSpPr>
                  <p:spPr/>
                  <p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody>
                </p:sp>
              </p:spTree></p:cSld>
            </p:sldMaster>
            """;
        document.putXmlPart("ppt/slideMasters/slideMaster1.xml", parse(masterNoAnchor));

        LayoutInfo layout = new LayoutInfo("slideLayout1", "Title Slide",
            "slideLayouts/slideLayout1.xml", true, false, false, 0,
            "Title slide", Collections.emptyList(), "title");
        SlideRenderContext ctx = new SlideRenderContext(null, layout, document, 1,
            Collections.emptyMap(), "#FFFFFF");

        String anchor = ctx.resolveInheritedBodyPrAnchor("title", null);
        assertNull(anchor);
    }

    @Test
    public void layoutAnchorBeatsMasterAnchor() throws Exception {
        // Layout specifies anchor="t" explicitly; master has "ctr". The
        // inheritance walk stops at the layout.
        String layoutWithAnchor = """
            <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                         xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld><p:spTree>
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/>
                    <p:nvPr><p:ph type="title"/></p:nvPr>
                  </p:nvSpPr>
                  <p:spPr/>
                  <p:txBody><a:bodyPr anchor="t"/><a:lstStyle/><a:p/></p:txBody>
                </p:sp>
              </p:spTree></p:cSld>
            </p:sldLayout>
            """;
        document.putXmlPart("ppt/slideLayouts/slideLayout1.xml", parse(layoutWithAnchor));

        LayoutInfo layout = new LayoutInfo("slideLayout1", "Title Slide",
            "slideLayouts/slideLayout1.xml", true, false, false, 0,
            "Title slide", Collections.emptyList(), "title");
        SlideRenderContext ctx = new SlideRenderContext(null, layout, document, 1,
            Collections.emptyMap(), "#FFFFFF");

        String anchor = ctx.resolveInheritedBodyPrAnchor("title", null);
        assertEquals("t", anchor);
    }

    private static Document parse(String xml) throws Exception {
        return XMLFactoryProvider.parseDocument(xml);
    }
}
