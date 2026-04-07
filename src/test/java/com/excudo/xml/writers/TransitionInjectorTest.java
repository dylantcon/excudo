package com.excudo.xml.writers;

import com.excudo.core.model.TransitionType;
import com.excudo.core.utils.XMLConstants;
import com.excudo.exceptions.XMLParsingException;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransitionInjector -- slide transition injection into PPTX slide XML.
 */
class TransitionInjectorTest {

    private DocumentBuilder documentBuilder;
    private XPath xpath;

    @BeforeEach
    void setUp() throws Exception {
        documentBuilder = WriterTestFixtures.createDocumentBuilder();
        xpath = WriterTestFixtures.createConfiguredXPath();
    }

    private Document createSlideDocument() throws Exception {
        return WriterTestFixtures.createMinimalSlideDocument(documentBuilder);
    }

    // ========== setTransition ==========

    @Test
    void setTransition_fadeCreatesCorrectElement() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.FADE, "med", null, null);

        Element transition = findTransitionElement(doc);
        assertNotNull(transition, "Transition element should exist");
        assertEquals("med", transition.getAttribute("spd"));

        NodeList children = transition.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "fade");
        assertEquals(1, children.getLength(), "Should have p:fade child");
    }

    @Test
    void setTransition_pushLeftHasDirectionAttribute() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.PUSH_LEFT, "fast", null, null);

        Element transition = findTransitionElement(doc);
        assertNotNull(transition);
        assertEquals("fast", transition.getAttribute("spd"));

        NodeList pushNodes = transition.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "push");
        assertEquals(1, pushNodes.getLength());
        Element push = (Element) pushNodes.item(0);
        assertEquals("l", push.getAttribute("dir"));
    }

    @Test
    void setTransition_wheelHasSpokesAttribute() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.WHEEL_4, null, null, null);

        Element transition = findTransitionElement(doc);
        assertNotNull(transition);
        assertEquals("med", transition.getAttribute("spd"), "Default speed should be med");

        NodeList wheelNodes = transition.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "wheel");
        assertEquals(1, wheelNodes.getLength());
        Element wheel = (Element) wheelNodes.item(0);
        assertEquals("4", wheel.getAttribute("spokes"));
    }

    @Test
    void setTransition_withAdvanceTime() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.FADE, "slow", 3000, null);

        Element transition = findTransitionElement(doc);
        assertNotNull(transition);
        assertEquals("slow", transition.getAttribute("spd"));
        assertEquals("3000", transition.getAttribute("advTm"));
    }

    @Test
    void setTransition_withDuration() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.DISSOLVE, "med", null, 1500);

        Element transition = findTransitionElement(doc);
        assertNotNull(transition);
        assertEquals("1500", transition.getAttribute("dur"));
    }

    @Test
    void setTransition_noneRemovesExisting() throws Exception {
        Document doc = createSlideDocument();

        // Set a transition first
        TransitionInjector.setTransition(doc, TransitionType.FADE, "med", null, null);
        assertTrue(TransitionInjector.hasTransition(doc));

        // Setting NONE should remove it
        TransitionInjector.setTransition(doc, TransitionType.NONE, null, null, null);
        assertFalse(TransitionInjector.hasTransition(doc));
    }

    @Test
    void setTransition_replacesExisting() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.FADE, "med", null, null);
        TransitionInjector.setTransition(doc, TransitionType.WIPE_LEFT, "fast", null, null);

        // Should have exactly one transition
        Element sld = doc.getDocumentElement();
        NodeList transitions = sld.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "transition");
        assertEquals(1, transitions.getLength(), "Should replace, not add second transition");

        Element transition = (Element) transitions.item(0);
        assertEquals("fast", transition.getAttribute("spd"));

        NodeList wipeNodes = transition.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "wipe");
        assertEquals(1, wipeNodes.getLength());
    }

    @Test
    void setTransition_insertedBeforeTiming() throws Exception {
        Document doc = createSlideDocument();

        // Add a timing element first
        Element sld = doc.getDocumentElement();
        Element timing = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:timing");
        sld.appendChild(timing);

        TransitionInjector.setTransition(doc, TransitionType.FADE, "med", null, null);

        // Verify transition comes before timing
        NodeList sldChildren = sld.getChildNodes();
        int transitionIdx = -1;
        int timingIdx = -1;
        for (int i = 0; i < sldChildren.getLength(); i++) {
            Node child = sldChildren.item(i);
            if (child instanceof Element) {
                String tag = ((Element) child).getTagName();
                if ("p:transition".equals(tag)) transitionIdx = i;
                if ("p:timing".equals(tag)) timingIdx = i;
            }
        }
        assertTrue(transitionIdx >= 0, "Transition should exist");
        assertTrue(timingIdx >= 0, "Timing should exist");
        assertTrue(transitionIdx < timingIdx, "Transition should come before timing");
    }

    // ========== removeTransition ==========

    @Test
    void removeTransition_removesExisting() throws Exception {
        Document doc = createSlideDocument();

        TransitionInjector.setTransition(doc, TransitionType.FADE, "med", null, null);
        assertTrue(TransitionInjector.hasTransition(doc));

        TransitionInjector.removeTransition(doc);
        assertFalse(TransitionInjector.hasTransition(doc));
    }

    @Test
    void removeTransition_noOpWhenNone() throws Exception {
        Document doc = createSlideDocument();
        assertFalse(TransitionInjector.hasTransition(doc));

        // Should not throw
        TransitionInjector.removeTransition(doc);
        assertFalse(TransitionInjector.hasTransition(doc));
    }

    // ========== hasTransition ==========

    @Test
    void hasTransition_falseForNewSlide() throws Exception {
        Document doc = createSlideDocument();
        assertFalse(TransitionInjector.hasTransition(doc));
    }

    @Test
    void hasTransition_trueAfterSet() throws Exception {
        Document doc = createSlideDocument();
        TransitionInjector.setTransition(doc, TransitionType.DIAMOND, null, null, null);
        assertTrue(TransitionInjector.hasTransition(doc));
    }

    // ========== TransitionType parsing ==========

    @Test
    void transitionType_parseKnownTypes() {
        assertEquals(TransitionType.FADE, TransitionType.parseType("fade"));
        assertEquals(TransitionType.PUSH_LEFT, TransitionType.parseType("push-left"));
        assertEquals(TransitionType.WIPE_DOWN, TransitionType.parseType("wipe-down"));
        assertEquals(TransitionType.NONE, TransitionType.parseType("none"));
        assertEquals(TransitionType.DISSOLVE, TransitionType.parseType("dissolve"));
    }

    @Test
    void transitionType_parseAliases() {
        assertEquals(TransitionType.PUSH_LEFT, TransitionType.parseType("push"));
        assertEquals(TransitionType.WIPE_LEFT, TransitionType.parseType("wipe"));
        assertEquals(TransitionType.WHEEL_4, TransitionType.parseType("wheel"));
        assertEquals(TransitionType.SPLIT_HORIZONTAL, TransitionType.parseType("split"));
        assertEquals(TransitionType.CHECKER_ACROSS, TransitionType.parseType("checkerboard"));
    }

    @Test
    void transitionType_parseCaseInsensitive() {
        assertEquals(TransitionType.FADE, TransitionType.parseType("FADE"));
        assertEquals(TransitionType.PUSH_LEFT, TransitionType.parseType("Push-Left"));
    }

    @Test
    void transitionType_parseUnderscores() {
        assertEquals(TransitionType.PUSH_LEFT, TransitionType.parseType("push_left"));
        assertEquals(TransitionType.FADE_THROUGH_BLACK, TransitionType.parseType("fade_through_black"));
    }

    @Test
    void transitionType_unknownDefaultsToFade() {
        assertEquals(TransitionType.FADE, TransitionType.parseType("nonexistent"));
        assertEquals(TransitionType.FADE, TransitionType.parseType(null));
        assertEquals(TransitionType.FADE, TransitionType.parseType(""));
    }

    @Test
    void transitionType_allTypesHaveUserFriendlyName() {
        for (TransitionType type : TransitionType.values()) {
            assertNotNull(type.getUserFriendlyName());
            assertNotNull(type.getDescription());
            if (type != TransitionType.NONE) {
                assertNotNull(type.getXmlElementName(), type + " should have XML element name");
            }
        }
    }

    @Test
    void transitionType_cutNoAttributes() throws Exception {
        Document doc = createSlideDocument();
        TransitionInjector.setTransition(doc, TransitionType.CUT, "med", null, null);

        Element transition = findTransitionElement(doc);
        NodeList cutNodes = transition.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "cut");
        assertEquals(1, cutNodes.getLength());
        Element cut = (Element) cutNodes.item(0);
        assertFalse(cut.hasAttribute("thruBlk"), "Plain cut should not have thruBlk");
    }

    @Test
    void transitionType_cutThroughBlack() throws Exception {
        Document doc = createSlideDocument();
        TransitionInjector.setTransition(doc, TransitionType.CUT_THROUGH_BLACK, "med", null, null);

        Element transition = findTransitionElement(doc);
        NodeList cutNodes = transition.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "cut");
        assertEquals(1, cutNodes.getLength());
        Element cut = (Element) cutNodes.item(0);
        assertEquals("1", cut.getAttribute("thruBlk"));
    }

    // ========== Helpers ==========

    private Element findTransitionElement(Document doc) {
        NodeList list = doc.getDocumentElement().getElementsByTagNameNS(
            XMLConstants.PRESENTATION_NS, "transition");
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }
}
