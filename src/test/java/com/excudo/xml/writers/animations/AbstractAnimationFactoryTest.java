package com.excudo.xml.writers.animations;

import org.junit.Test;
import static org.junit.Assert.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.xml.writers.WriterTestFixtures;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;
import java.util.Arrays;

/**
 * Unit tests for AbstractAnimationFactory.
 * Tests the template method pattern implementation and common factory behaviors.
 */
public class AbstractAnimationFactoryTest {
    
    @Test
    public void testCreateTimingContainer() throws Exception {
        // Test the template method for timing container creation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        AnimationBinding binding = new AnimationBinding(
            42, AnimationType.FADE, "in", "500", "0"
        );
        
        Element timingContainer = factory.createTimingContainer(document, binding);
        
        assertNotNull("Timing container should be created", timingContainer);
        assertEquals("Should be par element", "par", timingContainer.getLocalName());
        
        // Check for p:cTn child
        NodeList cTnNodes = timingContainer.getElementsByTagNameNS("*", "cTn");
        assertTrue("Should contain p:cTn element", cTnNodes.getLength() > 0);
        
        Element cTn = (Element) cTnNodes.item(0);
        assertTrue("Should have timing node ID", cTn.hasAttribute("id"));
        assertEquals("Should have correct preset class", "entr", cTn.getAttribute("presetClass"));
        assertEquals("Should have correct group ID", "0", cTn.getAttribute("grpId"));
        assertEquals("Should have fill=hold", "hold", cTn.getAttribute("fill"));
        
        // Check for start conditions
        NodeList stCondNodes = cTn.getElementsByTagNameNS("*", "stCondLst");
        assertTrue("Should contain start conditions", stCondNodes.getLength() > 0);
        
        // Check for child timing list
        NodeList childTnNodes = cTn.getElementsByTagNameNS("*", "childTnLst");
        assertTrue("Should contain child timing list", childTnNodes.getLength() > 0);
    }
    
    @Test
    public void testCreateTargetElement() throws Exception {
        // Test target element creation for shape-level animation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding shapeLevelBinding = new AnimationBinding(
            123, AnimationType.FADE, "in", "500", "0"
        );

        Element targetElement = factory.createTargetElement(document, shapeLevelBinding);

        assertNotNull("Target element should be created", targetElement);
        assertEquals("Should be tgtEl element", "tgtEl", targetElement.getLocalName());

        // Check for shape targeting
        NodeList spTgtNodes = targetElement.getElementsByTagNameNS("*", "spTgt");
        assertTrue("Should contain p:spTgt element", spTgtNodes.getLength() > 0);

        Element spTgt = (Element) spTgtNodes.item(0);
        assertEquals("Should target correct shape", "123", spTgt.getAttribute("spid"));

        // Shape-level: bare spTgt with no txEl/pRg children (MS-OI29500 19.5.16(c))
        NodeList txElNodes = spTgt.getElementsByTagNameNS("*", "txEl");
        assertEquals("Shape-level should have no txEl children", 0, txElNodes.getLength());
    }
    
    @Test
    public void testCreateTargetElementParagraphLevel() throws Exception {
        // Test target element creation for paragraph-level animation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        AnimationBinding paragraphBinding = new AnimationBinding(
            456, AnimationType.WIPE_LEFT, "in", "500", "0", 2, 4
        );
        
        Element targetElement = factory.createTargetElement(document, paragraphBinding);
        
        // Check paragraph range for paragraph-level animation
        NodeList pRgNodes = targetElement.getElementsByTagNameNS("*", "pRg");
        assertTrue("Should contain paragraph range", pRgNodes.getLength() > 0);
        
        Element pRg = (Element) pRgNodes.item(0);
        assertEquals("Should use specified paragraph start", "2", pRg.getAttribute("st"));
        assertEquals("Should use specified paragraph end", "4", pRg.getAttribute("end"));
    }
    
    @Test
    public void testCreateBuildListEntry() throws Exception {
        // Test build list entry creation for shape-level animation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding binding = new AnimationBinding(
            789, AnimationType.FADE, "out", "750", "0"
        );

        Element buildEntry = factory.createBuildListEntry(document, binding);

        assertNotNull("Build entry should be created", buildEntry);
        assertEquals("Should be bldP element", "bldP", buildEntry.getLocalName());
        // Shape-level: no build="p" attribute (MS-OI29500 19.5.16(c))
        assertEquals("Shape-level should not have build attribute", "", buildEntry.getAttribute("build"));
        assertEquals("Should have correct group ID for exit", "0", buildEntry.getAttribute("grpId"));
        assertEquals("Should target correct shape", "789", buildEntry.getAttribute("spid"));
    }

    @Test
    public void testCreateBuildListEntryParagraphLevel() throws Exception {
        // Test build list entry creation for paragraph-level animation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding paragraphBinding = new AnimationBinding(
            789, AnimationType.FADE, "out", "750", "0", 0, 2
        );

        Element buildEntry = factory.createBuildListEntry(document, paragraphBinding);

        assertNotNull("Build entry should be created", buildEntry);
        assertEquals("Should be bldP element", "bldP", buildEntry.getLocalName());
        assertEquals("Paragraph-level should have build=p", "p", buildEntry.getAttribute("build"));
        assertEquals("Should target correct shape", "789", buildEntry.getAttribute("spid"));
    }
    
    @Test
    public void testCreateCommonBehavior() throws Exception {
        // Test common behavior element creation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        AnimationBinding binding = new AnimationBinding(
            111, AnimationType.FADE, "in", "500", "250"
        );
        
        Element behavior = factory.createCommonBehavior(document, binding, "250", "500");
        
        assertNotNull("Behavior should be created", behavior);
        assertEquals("Should be cBhvr element", "cBhvr", behavior.getLocalName());
        
        // Check timing node
        NodeList cTnNodes = behavior.getElementsByTagNameNS("*", "cTn");
        assertTrue("Should contain timing node", cTnNodes.getLength() > 0);
        
        Element cTn = (Element) cTnNodes.item(0);
        assertEquals("Should have correct duration", "500", cTn.getAttribute("dur"));
        assertEquals("Should have fill=hold", "hold", cTn.getAttribute("fill"));
        
        // Check start conditions for non-zero delay
        NodeList stCondNodes = cTn.getElementsByTagNameNS("*", "stCondLst");
        assertTrue("Should contain start conditions for delay", stCondNodes.getLength() > 0);
        
        // Check target element
        NodeList tgtNodes = behavior.getElementsByTagNameNS("*", "tgtEl");
        assertTrue("Should contain target element", tgtNodes.getLength() > 0);
    }
    
    @Test
    public void testCreateCommonBehaviorNoDelay() throws Exception {
        // Test common behavior with no delay
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        AnimationBinding binding = new AnimationBinding(
            222, AnimationType.FADE, "in", "500", "0"
        );
        
        Element behavior = factory.createCommonBehavior(document, binding, "0", "500");
        
        // Check that no start conditions are added for zero delay
        Element cTn = (Element) behavior.getElementsByTagNameNS("*", "cTn").item(0);
        NodeList stCondNodes = cTn.getElementsByTagNameNS("*", "stCondLst");
        assertEquals("Should not contain start conditions for zero delay", 0, stCondNodes.getLength());
    }
    
    @Test
    public void testCreateTimingValue() throws Exception {
        // Test timing value creation
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        Element timingValue = factory.createTimingValue(document, "0", "100");
        
        assertNotNull("Timing value should be created", timingValue);
        assertEquals("Should be tav element", "tav", timingValue.getLocalName());
        assertEquals("Should have correct time", "0", timingValue.getAttribute("tm"));
        
        // Check value structure
        NodeList valNodes = timingValue.getElementsByTagNameNS("*", "val");
        assertTrue("Should contain value element", valNodes.getLength() > 0);
        
        Element val = (Element) valNodes.item(0);
        NodeList strValNodes = val.getElementsByTagNameNS("*", "strVal");
        assertTrue("Should contain string value", strValNodes.getLength() > 0);
        
        Element strVal = (Element) strValNodes.item(0);
        assertEquals("Should have correct value", "100", strVal.getAttribute("val"));
    }
    
    @Test
    public void testSupportsAnimationType() {
        // Test animation type support checking
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        
        assertTrue("Should support FADE", factory.supportsAnimationType(AnimationType.FADE));
        assertTrue("Should support APPEAR", factory.supportsAnimationType(AnimationType.APPEAR));
        assertFalse("Should not support PULSE", factory.supportsAnimationType(AnimationType.PULSE));
    }
    
    @Test
    public void testAnimationGroupTypes() throws Exception {
        // Test different animation group types affect node type
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        // Test on-click (default)
        AnimationBinding onClickBinding = AnimationBinding.builder()
            .target(1)
            .type(AnimationType.FADE)
            .entrance()
            .duration("500")
            .delay("0")
            .animationGroup("on-click")
            .build();
        
        Element onClickContainer = factory.createTimingContainer(document, onClickBinding);
        Element cTn1 = (Element) onClickContainer.getElementsByTagNameNS("*", "cTn").item(0);
        assertEquals("On-click should use clickEffect", "clickEffect", cTn1.getAttribute("nodeType"));
        
        // Test with-previous
        AnimationBinding withPrevBinding = AnimationBinding.builder()
            .target(2)
            .type(AnimationType.FADE)
            .entrance()
            .duration("500")
            .delay("0")
            .withPrevious()
            .build();
        
        Element withPrevContainer = factory.createTimingContainer(document, withPrevBinding);
        Element cTn2 = (Element) withPrevContainer.getElementsByTagNameNS("*", "cTn").item(0);
        assertEquals("With-previous should use withEffect", "withEffect", cTn2.getAttribute("nodeType"));
        
        // Test after-previous
        AnimationBinding afterPrevBinding = AnimationBinding.builder()
            .target(3)
            .type(AnimationType.FADE)
            .entrance()
            .duration("500")
            .delay("0")
            .afterPrevious()
            .build();
        
        Element afterPrevContainer = factory.createTimingContainer(document, afterPrevBinding);
        Element cTn3 = (Element) afterPrevContainer.getElementsByTagNameNS("*", "cTn").item(0);
        assertEquals("After-previous should use afterEffect", "afterEffect", cTn3.getAttribute("nodeType"));
    }
    
    @Test
    public void testPresetClassMapping() throws Exception {
        // Test preset class mapping for different transitions
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();
        
        // Test entrance
        AnimationBinding entranceBinding = new AnimationBinding(
            1, AnimationType.FADE, "in", "500", "0"
        );
        Element entranceContainer = factory.createTimingContainer(document, entranceBinding);
        Element cTn1 = (Element) entranceContainer.getElementsByTagNameNS("*", "cTn").item(0);
        assertEquals("Entrance should use entr preset class", "entr", cTn1.getAttribute("presetClass"));
        
        // Test exit
        AnimationBinding exitBinding = new AnimationBinding(
            2, AnimationType.FADE, "out", "500", "0"
        );
        Element exitContainer = factory.createTimingContainer(document, exitBinding);
        Element cTn2 = (Element) exitContainer.getElementsByTagNameNS("*", "cTn").item(0);
        assertEquals("Exit should use exit preset class", "exit", cTn2.getAttribute("presetClass"));
        
        // Test emphasis
        AnimationBinding emphasisBinding = new AnimationBinding(
            3, AnimationType.FADE, "emphasis", "500", "0"
        );
        Element emphasisContainer = factory.createTimingContainer(document, emphasisBinding);
        Element cTn3 = (Element) emphasisContainer.getElementsByTagNameNS("*", "cTn").item(0);
        assertEquals("Emphasis should use emph preset class", "emph", cTn3.getAttribute("presetClass"));
    }
    
    @Test
    public void testCreateTimingContainerParagraphLevel_omitsGrpId() throws Exception {
        // MS-OI29500 cTn(h): grpId requires matching bldLst entry.
        // Explicit per-paragraph animations have no bldP, so cTn must omit grpId.
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding paragraphBinding = new AnimationBinding(
            42, AnimationType.FADE, "in", "500", "0", 0, 0
        );

        Element container = factory.createTimingContainer(document, paragraphBinding);
        Element cTn = (Element) container.getElementsByTagNameNS("*", "cTn").item(0);

        assertFalse("Paragraph-level cTn must NOT have grpId", cTn.hasAttribute("grpId"));
        assertEquals("Should still have presetClass", "entr", cTn.getAttribute("presetClass"));
        assertEquals("Should still have nodeType", "clickEffect", cTn.getAttribute("nodeType"));
    }

    @Test
    public void testCreateTimingContainerEmphasis_hasGrpId() throws Exception {
        // Oracle (slides 89, 90, 115-118): emphasis animations DO have grpId + bldLst
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding emphasisBinding = new AnimationBinding(
            42, AnimationType.FADE, "emphasis", "500", "0"
        );

        Element container = factory.createTimingContainer(document, emphasisBinding);
        Element cTn = (Element) container.getElementsByTagNameNS("*", "cTn").item(0);

        assertTrue("Emphasis cTn must have grpId per oracle", cTn.hasAttribute("grpId"));
    }

    @Test
    public void testCreateTimingContainerEmphasis_fillRemoveForColorPulse() throws Exception {
        // PowerPoint rejects emphasis animations with fill="hold" on the outer cTn.
        // Oracle pattern: COLOR_PULSE (presetID=27) outer cTn has fill="remove".
        // PowerPoint deleted the entire timing tree when we used fill="hold".
        ColorAnimationFactory factory = WriterTestFixtures.configureFactory(new ColorAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding colorPulseBinding = AnimationBinding.builder()
            .target(3)
            .type(AnimationType.COLOR_PULSE)
            .emphasis()
            .duration("500")
            .delay("0")
            .build();

        Element container = factory.createTimingContainer(document, colorPulseBinding);
        Element cTn = (Element) container.getElementsByTagNameNS("*", "cTn").item(0);

        assertEquals("COLOR_PULSE emphasis outer cTn must have fill=remove (oracle pattern)",
                     "remove", cTn.getAttribute("fill"));
    }

    @Test
    public void testCreateTimingContainerEmphasis_fillRemoveForTeeter() throws Exception {
        // Oracle slide 1: TEETER (presetID=32) outer cTn has fill="hold".
        // This is the default behavior, so TEETER should keep fill="hold".
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding teeterBinding = AnimationBinding.builder()
            .target(3)
            .type(AnimationType.TEETER)
            .emphasis()
            .duration("500")
            .delay("0")
            .build();

        Element container = factory.createTimingContainer(document, teeterBinding);
        Element cTn = (Element) container.getElementsByTagNameNS("*", "cTn").item(0);

        assertEquals("TEETER emphasis outer cTn should have fill=hold (oracle pattern)",
                     "hold", cTn.getAttribute("fill"));
    }

    @Test
    public void testCreateTimingContainerEntrance_fillHoldPreserved() throws Exception {
        // Entrance animations must keep fill="hold" (unchanged behavior)
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding entranceBinding = AnimationBinding.builder()
            .target(3)
            .type(AnimationType.FADE)
            .entrance()
            .duration("500")
            .delay("0")
            .build();

        Element container = factory.createTimingContainer(document, entranceBinding);
        Element cTn = (Element) container.getElementsByTagNameNS("*", "cTn").item(0);

        assertEquals("Entrance outer cTn must keep fill=hold",
                     "hold", cTn.getAttribute("fill"));
    }

    @Test
    public void testCreateTimingContainerShapeLevel_hasGrpId() throws Exception {
        // Shape-level entrance/exit must have grpId for bldLst linkage
        TestAnimationFactory factory = WriterTestFixtures.configureFactory(new TestAnimationFactory());
        Document document = createTestDocument();

        AnimationBinding shapeBinding = new AnimationBinding(
            42, AnimationType.FADE, "in", "500", "0"
        );

        Element container = factory.createTimingContainer(document, shapeBinding);
        Element cTn = (Element) container.getElementsByTagNameNS("*", "cTn").item(0);

        assertTrue("Shape-level cTn must have grpId", cTn.hasAttribute("grpId"));
    }

    /**
     * Helper method to create a test XML document
     */
    private Document createTestDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
    
    /**
     * Concrete test implementation of AbstractAnimationFactory for testing
     */
    private static class TestAnimationFactory extends AbstractAnimationFactory {
        
        @Override
        public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
            // Simple test implementation - create a basic fade effect
            Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
            animEffect.setAttribute("transition", binding.getTransition());
            animEffect.setAttribute("filter", "fade");
            
            Element cBhvr = createCommonBehavior(document, binding, binding.getDelay(), binding.getDuration());
            animEffect.appendChild(cBhvr);
            
            return Arrays.asList(animEffect);
        }
        
        @Override
        public AnimationType[] getSupportedAnimationTypes() {
            return new AnimationType[]{AnimationType.FADE, AnimationType.APPEAR};
        }
        
        @Override
        public String getOoxmlPattern() {
            return "Test OOXML pattern: p:animEffect with fade filter";
        }
    }
}