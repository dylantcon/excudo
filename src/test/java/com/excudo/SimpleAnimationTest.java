package com.excudo;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.test.utils.FixtureDecks;
import com.excudo.xml.writers.SlideXMLWriter;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Entrance + exit animations injected into a real slide produce the timing
 * structure PowerPoint expects: visibility sets, a build-list entry for the
 * target, and one click-triggered effect per animation.
 */
public class SimpleAnimationTest {

    private static final String PNS = "http://schemas.openxmlformats.org/presentationml/2006/main";

    @Test
    public void testSimpleAnimationStructure() throws Exception {
        File slideFile = FixtureDecks.extracted("generalist_test_file").resolve("ppt/slides/slide2.xml").toFile();
        assertTrue("fixture slide present", slideFile.exists());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(slideFile);

        SlideXMLWriter writer = new SlideXMLWriter(doc);
        int targetSpid = 2;
        ShapeGeometry geo = new ShapeGeometry(0, 0, 914400, 914400);
        writer.injectAnimation(AnimationBinding.builder()
            .target(targetSpid).type("fade").entrance()
            .duration("500").delay("0").clickTrigger(1).animationGroup("on-click").build(), geo);
        writer.injectAnimation(AnimationBinding.builder()
            .target(targetSpid).type("fade").exit()
            .duration("500").delay("0").clickTrigger(2).animationGroup("on-click").build(), geo);

        File out = Files.createTempFile("simple-animation", ".xml").toFile();
        try {
            writer.writeXML(out);
            assertTrue("output written", out.length() > 0);
            Document written = builder.parse(out);

            int visibilitySets = 0;
            NodeList attrNames = written.getElementsByTagNameNS(PNS, "attrName");
            for (int i = 0; i < attrNames.getLength(); i++) {
                if ("style.visibility".equals(attrNames.item(i).getTextContent().trim())) visibilitySets++;
            }
            assertTrue("entrance and exit each toggle visibility", visibilitySets >= 2);

            boolean built = false;
            NodeList bldPs = written.getElementsByTagNameNS(PNS, "bldP");
            for (int i = 0; i < bldPs.getLength(); i++) {
                if (String.valueOf(targetSpid).equals(((Element) bldPs.item(i)).getAttribute("spid"))) built = true;
            }
            assertTrue("build list has an entry for the animated shape", built);

            int clickEffects = 0;
            NodeList cTns = written.getElementsByTagNameNS(PNS, "cTn");
            for (int i = 0; i < cTns.getLength(); i++) {
                if ("clickEffect".equals(((Element) cTns.item(i)).getAttribute("nodeType"))) clickEffects++;
            }
            assertTrue("two click-triggered effects (fade in, fade out); found " + clickEffects, clickEffects >= 2);
        } finally {
            out.delete();
        }
    }
}
