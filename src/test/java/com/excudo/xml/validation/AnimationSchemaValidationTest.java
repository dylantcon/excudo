package com.excudo.xml.validation;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.excudo.core.animations.AnimationFactoryRegistry;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.xml.writers.animations.AbstractAnimationFactory;
import com.excudo.xml.writers.WriterTestFixtures;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;
import java.util.Set;

/**
 * Validates all registered animation types produce XSD-compliant OOXML
 * against the ECMA-376 PresentationML schema.
 *
 * Each animation type's output elements are wrapped in a minimal valid slide
 * skeleton (p:sld/p:cSld/p:spTree + p:timing) and validated against pml.xsd.
 *
 * Test failures indicate real schema compliance bugs in animation factories.
 */
public class AnimationSchemaValidationTest {

    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static AnimationFactoryRegistry registry;

    @BeforeClass
    public static void setUp() {
        Assume.assumeTrue("PresentationML XSD schema not available on classpath",
            OOXMLSchemaValidator.isSchemaAvailable());
        registry = new AnimationFactoryRegistry();
    }

    @Test
    public void testSchemaLoadsSuccessfully() {
        assertTrue("Schema should be available", OOXMLSchemaValidator.isSchemaAvailable());
        assertNull("No schema init error", OOXMLSchemaValidator.getSchemaInitError());
    }

    @Test
    public void testMinimalSlideSkeletonIsValid() throws Exception {
        Document doc = createMinimalSlideDocument();
        OOXMLSchemaValidator.SchemaValidationResult result = OOXMLSchemaValidator.validate(doc);
        assertTrue("Minimal slide skeleton should be XSD-valid: " + result.getSummary(),
            result.isValid());
    }

    @Test
    public void testAllEntranceAnimationsProduceValidXML() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (type.isEmphasis() || type.isMotionPath()) continue;

            String failure = validateAnimationType(type, "in");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (entrance): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Entrance animation XSD validation failures:" + failures);
        }
    }

    @Test
    public void testAllExitAnimationsProduceValidXML() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (type.isEmphasis() || type.isMotionPath()) continue;

            String failure = validateAnimationType(type, "out");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (exit): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Exit animation XSD validation failures:" + failures);
        }
    }

    @Test
    public void testAllEmphasisAnimationsProduceValidXML() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (!type.isEmphasis()) continue;

            String failure = validateAnimationType(type, "emphasis");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (emphasis): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Emphasis animation XSD validation failures:" + failures);
        }
    }

    @Test
    public void testAllMotionPathAnimationsProduceValidXML() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (!type.isMotionPath()) continue;

            String failure = validateAnimationType(type, "in");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (motion): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Motion path animation XSD validation failures:" + failures);
        }
    }

    @Test
    public void testAllSupportedTypesHaveFactories() {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        for (AnimationType type : supported) {
            assertNotNull("Registry should resolve factory for " + type.name(),
                registry.getFactory(type));
        }
    }

    // ========== HELPERS ==========

    /**
     * Validates a single animation type by creating a complete slide document
     * with the animation elements embedded in proper timing structure.
     *
     * @return null if valid, error description if invalid
     */
    private String validateAnimationType(AnimationType type, String transition) throws Exception {
        AbstractAnimationFactory factory = (AbstractAnimationFactory) registry.getFactory(type);
        if (factory == null) {
            return "No factory registered";
        }
        WriterTestFixtures.configureFactory(factory);

        Document doc = createMinimalSlideDocument();

        AnimationBinding binding = createBinding(type, transition);
        List<Element> animElements = factory.createAnimationElements(doc, binding, null);

        if (animElements == null || animElements.isEmpty()) {
            return "Factory produced no elements";
        }

        // Build the timing hierarchy: p:timing/p:tnLst/p:par/p:cTn/p:childTnLst/...
        Element timing = doc.createElementNS(P_NS, "p:timing");
        Element tnLst = doc.createElementNS(P_NS, "p:tnLst");
        timing.appendChild(tnLst);

        // Root par (tmRoot)
        Element rootPar = doc.createElementNS(P_NS, "p:par");
        tnLst.appendChild(rootPar);
        Element rootCTn = doc.createElementNS(P_NS, "p:cTn");
        rootCTn.setAttribute("id", "1");
        rootCTn.setAttribute("dur", "indefinite");
        rootCTn.setAttribute("restart", "never");
        rootCTn.setAttribute("nodeType", "tmRoot");
        rootPar.appendChild(rootCTn);

        // Main sequence
        Element rootChildTnLst = doc.createElementNS(P_NS, "p:childTnLst");
        rootCTn.appendChild(rootChildTnLst);

        Element seq = doc.createElementNS(P_NS, "p:seq");
        seq.setAttribute("concurrent", "1");
        seq.setAttribute("nextAc", "seek");
        rootChildTnLst.appendChild(seq);

        Element seqCTn = doc.createElementNS(P_NS, "p:cTn");
        seqCTn.setAttribute("id", "2");
        seqCTn.setAttribute("dur", "indefinite");
        seqCTn.setAttribute("nodeType", "mainSeq");
        seq.appendChild(seqCTn);

        // prev/next conditions on seq
        Element prevCondLst = doc.createElementNS(P_NS, "p:prevCondLst");
        Element prevCond = doc.createElementNS(P_NS, "p:cond");
        prevCond.setAttribute("evt", "onPrev");
        prevCond.setAttribute("delay", "0");
        Element prevTgtEl = doc.createElementNS(P_NS, "p:tgtEl");
        Element prevSldTgt = doc.createElementNS(P_NS, "p:sldTgt");
        prevTgtEl.appendChild(prevSldTgt);
        prevCond.appendChild(prevTgtEl);
        prevCondLst.appendChild(prevCond);
        seq.appendChild(prevCondLst);

        Element nextCondLst = doc.createElementNS(P_NS, "p:nextCondLst");
        Element nextCond = doc.createElementNS(P_NS, "p:cond");
        nextCond.setAttribute("evt", "onNext");
        nextCond.setAttribute("delay", "0");
        Element nextTgtEl = doc.createElementNS(P_NS, "p:tgtEl");
        Element nextSldTgt = doc.createElementNS(P_NS, "p:sldTgt");
        nextTgtEl.appendChild(nextSldTgt);
        nextCond.appendChild(nextTgtEl);
        nextCondLst.appendChild(nextCond);
        seq.appendChild(nextCondLst);

        // Click trigger par
        Element seqChildTnLst = doc.createElementNS(P_NS, "p:childTnLst");
        seqCTn.appendChild(seqChildTnLst);

        Element clickPar = doc.createElementNS(P_NS, "p:par");
        seqChildTnLst.appendChild(clickPar);
        Element clickCTn = doc.createElementNS(P_NS, "p:cTn");
        clickCTn.setAttribute("id", "3");
        clickCTn.setAttribute("fill", "hold");
        clickPar.appendChild(clickCTn);
        Element clickStCondLst = doc.createElementNS(P_NS, "p:stCondLst");
        Element clickCond = doc.createElementNS(P_NS, "p:cond");
        clickCond.setAttribute("delay", "indefinite");
        clickStCondLst.appendChild(clickCond);
        clickCTn.appendChild(clickStCondLst);

        // Delay par
        Element clickChildTnLst = doc.createElementNS(P_NS, "p:childTnLst");
        clickCTn.appendChild(clickChildTnLst);

        Element delayPar = doc.createElementNS(P_NS, "p:par");
        clickChildTnLst.appendChild(delayPar);
        Element delayCTn = doc.createElementNS(P_NS, "p:cTn");
        delayCTn.setAttribute("id", "4");
        delayCTn.setAttribute("fill", "hold");
        delayPar.appendChild(delayCTn);
        Element delayStCondLst = doc.createElementNS(P_NS, "p:stCondLst");
        Element delayCond = doc.createElementNS(P_NS, "p:cond");
        delayCond.setAttribute("delay", "0");
        delayStCondLst.appendChild(delayCond);
        delayCTn.appendChild(delayStCondLst);

        // Animation preset par containing the actual animation elements
        Element delayChildTnLst = doc.createElementNS(P_NS, "p:childTnLst");
        delayCTn.appendChild(delayChildTnLst);

        Element animPar = doc.createElementNS(P_NS, "p:par");
        delayChildTnLst.appendChild(animPar);
        Element animCTn = doc.createElementNS(P_NS, "p:cTn");
        animCTn.setAttribute("id", "5");
        animCTn.setAttribute("presetID", String.valueOf(type.getPresetId()));
        animCTn.setAttribute("presetClass", getPresetClass(transition));
        animCTn.setAttribute("presetSubtype", String.valueOf(type.getPresetSubtype()));
        animCTn.setAttribute("fill", "hold");
        animCTn.setAttribute("grpId", "0");
        animCTn.setAttribute("nodeType", "clickEffect");
        animPar.appendChild(animCTn);

        Element animStCondLst = doc.createElementNS(P_NS, "p:stCondLst");
        Element animCond = doc.createElementNS(P_NS, "p:cond");
        animCond.setAttribute("delay", "0");
        animStCondLst.appendChild(animCond);
        animCTn.appendChild(animStCondLst);

        // Insert actual animation elements into childTnLst
        Element animChildTnLst = doc.createElementNS(P_NS, "p:childTnLst");
        animCTn.appendChild(animChildTnLst);

        for (Element el : animElements) {
            Element imported = (Element) doc.importNode(el, true);
            animChildTnLst.appendChild(imported);
        }

        // Add timing to the slide (must come after cSld and clrMapOvr in schema order)
        doc.getDocumentElement().appendChild(timing);

        // Add build list (required for entrance/exit animations)
        if (!"emphasis".equals(transition)) {
            Element bldLst = doc.createElementNS(P_NS, "p:bldLst");
            Element bldP = doc.createElementNS(P_NS, "p:bldP");
            bldP.setAttribute("spid", "3");
            bldP.setAttribute("grpId", "0");
            bldLst.appendChild(bldP);
            timing.appendChild(bldLst);
        }

        // Validate
        OOXMLSchemaValidator.SchemaValidationResult result = OOXMLSchemaValidator.validate(doc);
        if (!result.isValid()) {
            return result.getSummary();
        }
        return null;
    }

    private AnimationBinding createBinding(AnimationType type, String transition) {
        AnimationBinding.Builder builder = AnimationBinding.builder()
            .type(type)
            .target(3)
            .duration("500")
            .delay("0")
            .animationGroup("on-click");

        switch (transition) {
            case "in" -> builder.entrance();
            case "out" -> builder.exit();
            case "emphasis" -> builder.emphasis();
        }

        if (type.isMotionPath()) {
            builder.motionPath("M 0 0 L 0.25 0.25 E");
        }

        return builder.build();
    }

    private String getPresetClass(String transition) {
        return switch (transition) {
            case "in" -> "entr";
            case "out" -> "exit";
            default -> "emph";
        };
    }

    /**
     * Creates a minimal valid PresentationML slide document.
     * Structure: p:sld > p:cSld > p:spTree > (nvGrpSpPr, grpSpPr, sp)
     * The shape (spid=3) serves as the animation target.
     */
    private Document createMinimalSlideDocument() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Root: p:sld
        Element sld = doc.createElementNS(P_NS, "p:sld");
        sld.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", A_NS);
        sld.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:r", R_NS);
        doc.appendChild(sld);

        // p:cSld
        Element cSld = doc.createElementNS(P_NS, "p:cSld");
        sld.appendChild(cSld);

        // p:spTree (CT_GroupShape)
        Element spTree = doc.createElementNS(P_NS, "p:spTree");
        cSld.appendChild(spTree);

        // nvGrpSpPr (required by CT_GroupShape)
        Element nvGrpSpPr = doc.createElementNS(P_NS, "p:nvGrpSpPr");
        spTree.appendChild(nvGrpSpPr);

        Element cNvPr = doc.createElementNS(P_NS, "p:cNvPr");
        cNvPr.setAttribute("id", "1");
        cNvPr.setAttribute("name", "");
        nvGrpSpPr.appendChild(cNvPr);

        Element cNvGrpSpPr = doc.createElementNS(P_NS, "p:cNvGrpSpPr");
        nvGrpSpPr.appendChild(cNvGrpSpPr);

        Element nvPr = doc.createElementNS(P_NS, "p:nvPr");
        nvGrpSpPr.appendChild(nvPr);

        // grpSpPr (required by CT_GroupShape)
        Element grpSpPr = doc.createElementNS(A_NS, "a:grpSpPr");
        spTree.appendChild(grpSpPr);

        // Add a minimal transform to grpSpPr
        Element xfrm = doc.createElementNS(A_NS, "a:xfrm");
        grpSpPr.appendChild(xfrm);
        Element off = doc.createElementNS(A_NS, "a:off");
        off.setAttribute("x", "0");
        off.setAttribute("y", "0");
        xfrm.appendChild(off);
        Element ext = doc.createElementNS(A_NS, "a:ext");
        ext.setAttribute("cx", "0");
        ext.setAttribute("cy", "0");
        xfrm.appendChild(ext);
        Element chOff = doc.createElementNS(A_NS, "a:chOff");
        chOff.setAttribute("x", "0");
        chOff.setAttribute("y", "0");
        xfrm.appendChild(chOff);
        Element chExt = doc.createElementNS(A_NS, "a:chExt");
        chExt.setAttribute("cx", "0");
        chExt.setAttribute("cy", "0");
        xfrm.appendChild(chExt);

        // Target shape (spid=3)
        Element sp = doc.createElementNS(P_NS, "p:sp");
        spTree.appendChild(sp);

        Element nvSpPr = doc.createElementNS(P_NS, "p:nvSpPr");
        sp.appendChild(nvSpPr);

        Element spCNvPr = doc.createElementNS(P_NS, "p:cNvPr");
        spCNvPr.setAttribute("id", "3");
        spCNvPr.setAttribute("name", "Content Placeholder 1");
        nvSpPr.appendChild(spCNvPr);

        Element cNvSpPr = doc.createElementNS(P_NS, "p:cNvSpPr");
        nvSpPr.appendChild(cNvSpPr);

        Element spNvPr = doc.createElementNS(P_NS, "p:nvPr");
        nvSpPr.appendChild(spNvPr);

        Element spPr = doc.createElementNS(P_NS, "p:spPr");
        sp.appendChild(spPr);

        Element txBody = doc.createElementNS(P_NS, "p:txBody");
        sp.appendChild(txBody);

        Element bodyPr = doc.createElementNS(A_NS, "a:bodyPr");
        txBody.appendChild(bodyPr);

        Element lstStyle = doc.createElementNS(A_NS, "a:lstStyle");
        txBody.appendChild(lstStyle);

        Element p = doc.createElementNS(A_NS, "a:p");
        txBody.appendChild(p);

        Element r = doc.createElementNS(A_NS, "a:r");
        p.appendChild(r);

        Element rPr = doc.createElementNS(A_NS, "a:rPr");
        rPr.setAttribute("lang", "en-US");
        r.appendChild(rPr);

        Element t = doc.createElementNS(A_NS, "a:t");
        t.setTextContent("Test Content");
        r.appendChild(t);

        return doc;
    }
}
