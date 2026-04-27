package com.excudo.xml.parsers;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Pins {@link MarkupCompatibilityUnwrapper}: PowerPoint authors any
 * Office 2010+ feature (math, modern effects) inside {@code mc:Choice}
 * with a {@code mc:Fallback} sibling for older readers. Without the
 * unwrap, the entire shape that contains the Choice path is invisible
 * to our parser -- on /tmp/Uncertainty Quantification.pptx slide 10
 * this hid the body containing all the math content, not just the
 * math itself.
 */
public class MarkupCompatibilityUnwrapperTest {

    @Test
    public void unwrapsChoiceWhenRequiresIsSupported() throws Exception {
        Document doc = parse("""
            <root xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'
                  xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'>
              <mc:AlternateContent>
                <mc:Choice Requires='a14'>
                  <p:sp id='choice'/>
                </mc:Choice>
                <mc:Fallback>
                  <p:sp id='fallback'/>
                </mc:Fallback>
              </mc:AlternateContent>
            </root>
            """);

        MarkupCompatibilityUnwrapper.unwrap(doc);

        // The Choice's <p:sp> survives at the root; Fallback is dropped.
        NodeList sps = doc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/presentationml/2006/main", "sp");
        assertEquals("Exactly one <p:sp> survives", 1, sps.getLength());
        assertEquals("Choice branch wins", "choice",
            ((Element) sps.item(0)).getAttribute("id"));
        // mc:AlternateContent wrapper is gone.
        assertEquals("Wrapper removed", 0, doc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/markup-compatibility/2006",
            "AlternateContent").getLength());
    }

    @Test
    public void fallsBackWhenChoiceRequiresUnsupportedNamespace() throws Exception {
        Document doc = parse("""
            <root xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'
                  xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'>
              <mc:AlternateContent>
                <mc:Choice Requires='custom-vendor-ext'>
                  <p:sp id='choice'/>
                </mc:Choice>
                <mc:Fallback>
                  <p:sp id='fallback'/>
                </mc:Fallback>
              </mc:AlternateContent>
            </root>
            """);

        MarkupCompatibilityUnwrapper.unwrap(doc);

        Element sp = (Element) doc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/presentationml/2006/main", "sp").item(0);
        assertEquals("Fallback wins when Choice's Requires isn't supported",
            "fallback", sp.getAttribute("id"));
    }

    @Test
    public void preservesNonAlternateContentSiblings() throws Exception {
        Document doc = parse("""
            <root xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'
                  xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'>
              <p:sp id='before'/>
              <mc:AlternateContent>
                <mc:Choice Requires='a14'><p:sp id='choice'/></mc:Choice>
                <mc:Fallback><p:sp id='fallback'/></mc:Fallback>
              </mc:AlternateContent>
              <p:sp id='after'/>
            </root>
            """);

        MarkupCompatibilityUnwrapper.unwrap(doc);

        NodeList sps = doc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/presentationml/2006/main", "sp");
        assertEquals("Three sibling shapes survive", 3, sps.getLength());
        assertEquals("before", ((Element) sps.item(0)).getAttribute("id"));
        assertEquals("Unwrapped Choice keeps document order", "choice",
            ((Element) sps.item(1)).getAttribute("id"));
        assertEquals("after", ((Element) sps.item(2)).getAttribute("id"));
    }

    @Test
    public void handlesNestedAlternateContent() throws Exception {
        Document doc = parse("""
            <root xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'
                  xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'>
              <mc:AlternateContent>
                <mc:Choice Requires='a14'>
                  <p:sp id='outer-choice'>
                    <mc:AlternateContent>
                      <mc:Choice Requires='a14'><p:sp id='inner-choice'/></mc:Choice>
                      <mc:Fallback><p:sp id='inner-fallback'/></mc:Fallback>
                    </mc:AlternateContent>
                  </p:sp>
                </mc:Choice>
                <mc:Fallback><p:sp id='outer-fallback'/></mc:Fallback>
              </mc:AlternateContent>
            </root>
            """);

        MarkupCompatibilityUnwrapper.unwrap(doc);

        // Outer Choice and inner Choice both win; both wrappers removed.
        assertEquals("Two shapes (outer + inner choice)", 2, doc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/presentationml/2006/main", "sp").getLength());
        assertEquals("No AlternateContent left", 0, doc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/markup-compatibility/2006",
            "AlternateContent").getLength());
    }

    @Test
    public void dropsAlternateContentWithNeitherUsableBranch() throws Exception {
        Document doc = parse("""
            <root xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'>
              <mc:AlternateContent>
                <mc:Choice Requires='vendor-a'/>
                <mc:Choice Requires='vendor-b'/>
              </mc:AlternateContent>
            </root>
            """);

        MarkupCompatibilityUnwrapper.unwrap(doc);

        assertEquals("AlternateContent dropped when no branch usable", 0,
            doc.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/markup-compatibility/2006",
                "AlternateContent").getLength());
    }

    @Test
    public void nullDocumentIsNoOp() {
        // No exception even on null/empty input -- defensive against
        // callers passing partially-loaded documents.
        MarkupCompatibilityUnwrapper.unwrap(null);
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
