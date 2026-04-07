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
import com.excudo.core.model.ShapeGeometry;
import com.excudo.xml.writers.AnimationInjector;
import com.excudo.xml.writers.animations.SequentialGroupIdManager;
import com.excudo.core.utils.XMLConstants;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.util.Set;

/**
 * End-to-end test: uses AnimationInjector to inject animations into a complete
 * slide document, then validates the entire document against the ECMA-376
 * PresentationML XSD schema.
 *
 * This bridges the gap between unit-level factory tests and full PPTX validation
 * by testing the actual injection pipeline output against the formal spec.
 */
public class AnimationPPTXGenerationTest {

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
    public void testInjectorProducesValidSlideForEntranceAnimations() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (type.isEmphasis() || type.isMotionPath()) continue;

            String failure = validateInjection(type, "in");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (entrance): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Injector entrance animation XSD failures:" + failures);
        }
    }

    @Test
    public void testInjectorProducesValidSlideForExitAnimations() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (type.isEmphasis() || type.isMotionPath()) continue;

            String failure = validateInjection(type, "out");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (exit): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Injector exit animation XSD failures:" + failures);
        }
    }

    @Test
    public void testInjectorProducesValidSlideForEmphasisAnimations() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (!type.isEmphasis()) continue;

            String failure = validateInjection(type, "emphasis");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (emphasis): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Injector emphasis animation XSD failures:" + failures);
        }
    }

    @Test
    public void testInjectorProducesValidSlideForMotionPaths() throws Exception {
        Set<AnimationType> supported = registry.getSupportedAnimationTypes();
        StringBuilder failures = new StringBuilder();

        for (AnimationType type : supported) {
            if (!type.isMotionPath()) continue;

            String failure = validateInjection(type, "in");
            if (failure != null) {
                failures.append("\n").append(type.name()).append(" (motion): ").append(failure);
            }
        }

        if (failures.length() > 0) {
            fail("Injector motion path XSD failures:" + failures);
        }
    }

    @Test
    public void testMultipleAnimationsOnSameSlide() throws Exception {
        Document doc = createMinimalSlideDocument();
        XPath xp = createConfiguredXPath();
        SequentialGroupIdManager gidMgr = new SequentialGroupIdManager();
        AnimationInjector injector = new AnimationInjector(doc, xp, registry, gidMgr);

        ShapeGeometry geo = new ShapeGeometry(0, 0, 914400, 914400);
        // Inject three different entrance animations on click triggers 1, 2, 3
        injector.injectAnimation(buildBinding(3, "fade", "in", "500", "0", 1, "on-click"), geo);
        injector.injectAnimation(buildBinding(3, "wipe-left", "in", "750", "0", 2, "on-click"), geo);
        injector.injectAnimation(buildBinding(3, "appear", "in", "1", "0", 3, "on-click"), geo);

        OOXMLSchemaValidator.SchemaValidationResult result = OOXMLSchemaValidator.validate(doc);
        assertTrue("Multi-animation slide should be XSD-valid: " + result.getSummary(),
            result.isValid());
    }

    // ========== HELPERS ==========

    private static AnimationBinding buildBinding(int spid, String type, String transition,
        String duration, String delay, int clickTrigger, String animationGroup) {
      AnimationBinding.Builder b = AnimationBinding.builder()
          .target(spid).type(type)
          .duration(duration != null ? duration : "500")
          .delay(delay != null ? delay : "0")
          .clickTrigger(clickTrigger)
          .animationGroup(animationGroup);
      if ("in".equals(transition)) b.entrance();
      else if ("out".equals(transition)) b.exit();
      else b.emphasis();
      return b.build();
    }

    private String validateInjection(AnimationType type, String transition) {
        try {
            Document doc = createMinimalSlideDocument();
            XPath xp = createConfiguredXPath();
            SequentialGroupIdManager gidMgr = new SequentialGroupIdManager();
            AnimationInjector injector = new AnimationInjector(doc, xp, registry, gidMgr);

            injector.injectAnimation(
                buildBinding(3, type.getUserFriendlyName(), transition, "500", "0", 1, "on-click"),
                new ShapeGeometry(0, 0, 914400, 914400));

            OOXMLSchemaValidator.SchemaValidationResult result = OOXMLSchemaValidator.validate(doc);
            if (!result.isValid()) {
                return result.getSummary();
            }
            return null;
        } catch (Exception e) {
            return "Exception: " + e.getMessage();
        }
    }

    private XPath createConfiguredXPath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(XMLConstants.createNamespaceContext());
        return xpath;
    }

    private Document createMinimalSlideDocument() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element sld = doc.createElementNS(P_NS, "p:sld");
        sld.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", A_NS);
        sld.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:r", R_NS);
        doc.appendChild(sld);

        Element cSld = doc.createElementNS(P_NS, "p:cSld");
        sld.appendChild(cSld);

        Element spTree = doc.createElementNS(P_NS, "p:spTree");
        cSld.appendChild(spTree);

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

        Element grpSpPr = doc.createElementNS(A_NS, "a:grpSpPr");
        spTree.appendChild(grpSpPr);

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

        // Target shape (spid=3) with text body
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
