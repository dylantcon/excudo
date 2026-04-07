package com.excudo.xml.writers;

import org.w3c.dom.*;
import com.excudo.core.utils.XMLConstants;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.excudo.xml.writers.animations.AbstractAnimationFactory;
import com.excudo.xml.writers.animations.SequentialGroupIdManager;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared test infrastructure for xml/writers test classes.
 * Centralizes boilerplate: namespace-aware DOM creation, XPath setup,
 * minimal OOXML document builders, and mock PPTX directory scaffolding.
 */
public final class WriterTestFixtures {

  private WriterTestFixtures() {}

  /**
   * Configure an AbstractAnimationFactory with test-appropriate ID generators.
   * Wires both a SequentialGroupIdManager and a sequential TimingNodeIdGenerator
   * so factory tests don't need AnimationInjector.
   */
  public static <T extends AbstractAnimationFactory> T configureFactory(T factory) {
    factory.setGroupIdManager(new SequentialGroupIdManager());
    int[] counter = {1};
    factory.setTimingNodeIdGenerator(() -> counter[0]++);
    return factory;
  }

  /**
   * Creates a namespace-aware DocumentBuilder suitable for OOXML parsing.
   */
  public static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder();
  }

  /**
   * Creates an XPath instance configured with the PowerPoint namespace context.
   */
  public static XPath createConfiguredXPath() {
    XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(XMLConstants.createNamespaceContext());
    return xpath;
  }

  /**
   * Creates a minimal slide Document with a p:sld root and p:spTree containing group shape props.
   */
  public static Document createMinimalSlideDocument(DocumentBuilder db) throws Exception {
    String slideXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
               xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:cSld>
            <p:spTree>
              <p:nvGrpSpPr>
                <p:cNvPr id="1" name=""/>
                <p:cNvGrpSpPr/>
                <p:nvPr/>
              </p:nvGrpSpPr>
              <p:grpSpPr>
                <a:xfrm>
                  <a:off x="0" y="0"/>
                  <a:ext cx="0" cy="0"/>
                  <a:chOff x="0" y="0"/>
                  <a:chExt cx="0" cy="0"/>
                </a:xfrm>
              </p:grpSpPr>
            </p:spTree>
          </p:cSld>
        </p:sld>
        """;
    return db.parse(new ByteArrayInputStream(slideXml.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Creates a slide Document with known shapes having the given SPIDs.
   */
  public static Document createSlideWithShapes(DocumentBuilder db, int... spids) throws Exception {
    Document doc = createMinimalSlideDocument(db);
    Element spTree = (Element) doc.getElementsByTagNameNS(
        XMLConstants.PRESENTATION_NS, "spTree").item(0);

    for (int spid : spids) {
      Element sp = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");

      Element nvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
      sp.appendChild(nvSpPr);

      Element cNvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
      cNvPr.setAttribute("id", String.valueOf(spid));
      cNvPr.setAttribute("name", "Shape " + spid);
      nvSpPr.appendChild(cNvPr);

      Element cNvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
      nvSpPr.appendChild(cNvSpPr);

      Element nvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
      nvSpPr.appendChild(nvPr);

      Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
      sp.appendChild(spPr);

      Element xfrm = doc.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
      spPr.appendChild(xfrm);

      Element off = doc.createElementNS(XMLConstants.DRAWING_NS, "a:off");
      off.setAttribute("x", "0");
      off.setAttribute("y", "0");
      xfrm.appendChild(off);

      Element ext = doc.createElementNS(XMLConstants.DRAWING_NS, "a:ext");
      ext.setAttribute("cx", "914400");
      ext.setAttribute("cy", "914400");
      xfrm.appendChild(ext);

      Element txBody = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
      sp.appendChild(txBody);

      Element bodyPr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr");
      txBody.appendChild(bodyPr);

      Element lstStyle = doc.createElementNS(XMLConstants.DRAWING_NS, "a:lstStyle");
      txBody.appendChild(lstStyle);

      Element para = doc.createElementNS(XMLConstants.DRAWING_NS, "a:p");
      txBody.appendChild(para);

      Element run = doc.createElementNS(XMLConstants.DRAWING_NS, "a:r");
      para.appendChild(run);

      Element t = doc.createElementNS(XMLConstants.DRAWING_NS, "a:t");
      t.setTextContent("Text " + spid);
      run.appendChild(t);

      spTree.appendChild(sp);
    }
    return doc;
  }

  /**
   * Creates a standard mock PPTX directory tree inside the given tempDir.
   * Returns the root directory (same as tempDir).
   *
   * Structure created:
   *   ppt/slides/
   *   ppt/slides/_rels/
   *   ppt/slideLayouts/
   *   ppt/slideMasters/
   *   ppt/media/
   *   ppt/_rels/
   *   [Content_Types].xml
   *   ppt/presentation.xml
   *   ppt/_rels/presentation.xml.rels
   */
  public static File createMockPptxStructure(File tempDir) throws IOException {
    new File(tempDir, "ppt/slides/_rels").mkdirs();
    new File(tempDir, "ppt/slideLayouts").mkdirs();
    new File(tempDir, "ppt/slideMasters").mkdirs();
    new File(tempDir, "ppt/media").mkdirs();
    new File(tempDir, "ppt/_rels").mkdirs();

    createContentTypesXml(tempDir, 0);
    createPresentationXml(tempDir, 0);
    createPresentationRelsXml(tempDir, 0);

    return tempDir;
  }

  /**
   * Creates a presentation.xml with N slide references.
   */
  public static void createPresentationXml(File dir, int slideCount) throws IOException {
    StringBuilder sldIdLst = new StringBuilder();
    sldIdLst.append("    <p:sldIdLst>");
    for (int i = 1; i <= slideCount; i++) {
      sldIdLst.append(String.format(
          "\n      <p:sldId id=\"%d\" r:id=\"rId%d\"/>",
          XMLConstants.DEFAULT_SLIDE_ID_START + i - 1, i + 1));
    }
    sldIdLst.append("\n    </p:sldIdLst>");

    String xml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:sldMasterIdLst>
            <p:sldMasterId id="2147483648" r:id="rId1"/>
          </p:sldMasterIdLst>
        %s
          <p:sldSz cx="12192000" cy="6858000"/>
        </p:presentation>
        """.formatted(sldIdLst.toString());

    Files.writeString(new File(dir, "ppt/presentation.xml").toPath(), xml);
  }

  /**
   * Creates a presentation.xml.rels file with slide master + N slides.
   */
  public static void createPresentationRelsXml(File dir, int slideCount) throws IOException {
    StringBuilder rels = new StringBuilder();
    rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
    rels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
    rels.append("  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>\n");
    for (int i = 1; i <= slideCount; i++) {
      rels.append(String.format(
          "  <Relationship Id=\"rId%d\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide%d.xml\"/>\n",
          i + 1, i));
    }
    rels.append("</Relationships>");

    Files.writeString(new File(dir, "ppt/_rels/presentation.xml.rels").toPath(), rels.toString());
  }

  /**
   * Creates a [Content_Types].xml with N slide overrides.
   */
  public static void createContentTypesXml(File dir, int slideCount) throws IOException {
    StringBuilder overrides = new StringBuilder();
    for (int i = 1; i <= slideCount; i++) {
      overrides.append(String.format(
          "  <Override PartName=\"/ppt/slides/slide%d.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n",
          i));
    }

    String xml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="xml" ContentType="application/xml"/>
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="png" ContentType="image/png"/>
          <Default Extension="jpeg" ContentType="image/jpeg"/>
          <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
        %s</Types>
        """.formatted(overrides.toString());

    Files.writeString(new File(dir, "[Content_Types].xml").toPath(), xml);
  }

  /**
   * Creates a minimal slide XML file at the expected path.
   */
  public static void createSlideFile(File dir, int slideNumber) throws IOException {
    String slideXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
               xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:cSld>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
            </p:spTree>
          </p:cSld>
        </p:sld>
        """;
    File slidesDir = new File(dir, "ppt/slides");
    slidesDir.mkdirs();
    Files.writeString(new File(slidesDir, "slide" + slideNumber + ".xml").toPath(), slideXml);
  }

  /**
   * Creates a minimal slide .rels file.
   */
  public static void createSlideRelsFile(File dir, int slideNumber) throws IOException {
    String relsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
        </Relationships>
        """;
    File relsDir = new File(dir, "ppt/slides/_rels");
    relsDir.mkdirs();
    Files.writeString(new File(relsDir, "slide" + slideNumber + ".xml.rels").toPath(), relsXml);
  }

  /**
   * Serializes a DOM Document to a file.
   */
  public static void writeXmlDocument(Document doc, File outputFile) throws Exception {
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer transformer = tf.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    outputFile.getParentFile().mkdirs();
    transformer.transform(new DOMSource(doc), new StreamResult(outputFile));
  }

  /**
   * Creates a minimal slide layout XML file for LayoutManager tests.
   */
  public static void createSlideLayoutFile(File dir, String layoutName) throws IOException {
    String layoutXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     type="blank">
          <p:cSld name="%s">
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
            </p:spTree>
          </p:cSld>
        </p:sldLayout>
        """.formatted(layoutName);
    File layoutsDir = new File(dir, "ppt/slideLayouts");
    layoutsDir.mkdirs();
    Files.writeString(new File(layoutsDir, layoutName + ".xml").toPath(), layoutXml);
  }

  /**
   * Creates a slide layout XML file with title and body placeholders (Title, Content layout).
   * Body placeholder has both type="body" and idx="1", matching real OOXML output.
   */
  public static void createTitleContentLayoutFile(File dir, String layoutName) throws IOException {
    String layoutXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     type="obj">
          <p:cSld name="Title, Content">
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
              <p:sp>
                <p:nvSpPr>
                  <p:cNvPr id="2" name="Title 1"/>
                  <p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr>
                  <p:nvPr><p:ph type="title"/></p:nvPr>
                </p:nvSpPr>
                <p:spPr>
                  <a:xfrm>
                    <a:off x="838200" y="365125"/>
                    <a:ext cx="10515600" cy="1325563"/>
                  </a:xfrm>
                </p:spPr>
                <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="en-US"/></a:p></p:txBody>
              </p:sp>
              <p:sp>
                <p:nvSpPr>
                  <p:cNvPr id="3" name="Content Placeholder 2"/>
                  <p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr>
                  <p:nvPr><p:ph type="body" idx="1"/></p:nvPr>
                </p:nvSpPr>
                <p:spPr>
                  <a:xfrm>
                    <a:off x="838200" y="1825625"/>
                    <a:ext cx="10515600" cy="4351338"/>
                  </a:xfrm>
                </p:spPr>
                <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="en-US"/></a:p></p:txBody>
              </p:sp>
            </p:spTree>
          </p:cSld>
        </p:sldLayout>
        """.formatted();
    File layoutsDir = new File(dir, "ppt/slideLayouts");
    layoutsDir.mkdirs();
    Files.writeString(new File(layoutsDir, layoutName + ".xml").toPath(), layoutXml);
  }

  /**
   * Creates a Title Slide layout with ctrTitle and subTitle placeholders at non-overlapping positions.
   */
  public static void createTitleSlideLayoutFile(File dir, String layoutName) throws IOException {
    String layoutXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     type="title">
          <p:cSld name="Title Slide">
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
              <p:sp>
                <p:nvSpPr>
                  <p:cNvPr id="2" name="Title 1"/>
                  <p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr>
                  <p:nvPr><p:ph type="ctrTitle"/></p:nvPr>
                </p:nvSpPr>
                <p:spPr>
                  <a:xfrm>
                    <a:off x="1524000" y="1122363"/>
                    <a:ext cx="9144000" cy="2387600"/>
                  </a:xfrm>
                </p:spPr>
                <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="en-US"/></a:p></p:txBody>
              </p:sp>
              <p:sp>
                <p:nvSpPr>
                  <p:cNvPr id="3" name="Subtitle 2"/>
                  <p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr>
                  <p:nvPr><p:ph type="subTitle"/></p:nvPr>
                </p:nvSpPr>
                <p:spPr>
                  <a:xfrm>
                    <a:off x="1524000" y="3602038"/>
                    <a:ext cx="9144000" cy="1655762"/>
                  </a:xfrm>
                </p:spPr>
                <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="en-US"/></a:p></p:txBody>
              </p:sp>
            </p:spTree>
          </p:cSld>
        </p:sldLayout>
        """.formatted();
    File layoutsDir = new File(dir, "ppt/slideLayouts");
    layoutsDir.mkdirs();
    Files.writeString(new File(layoutsDir, layoutName + ".xml").toPath(), layoutXml);
  }

  /**
   * Creates a slide master XML file for completeness in PPTX structures.
   */
  public static void createSlideMasterFile(File dir) throws IOException {
    String masterXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <p:cSld>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
            </p:spTree>
          </p:cSld>
        </p:sldMaster>
        """;
    File mastersDir = new File(dir, "ppt/slideMasters");
    mastersDir.mkdirs();
    Files.writeString(new File(mastersDir, "slideMaster1.xml").toPath(), masterXml);
  }

  // ========== SEMANTIC ASSERTION HELPERS ==========

  /**
   * Asserts the timing hierarchy is structurally valid:
   * Root=tmRoot, mainSeq structure, stCondLst/prevCondLst/nextCondLst present.
   * Skips validation if no timing element exists (no animations = valid).
   */
  public static void assertValidTimingHierarchy(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    Element timing = (Element) xp.evaluate("//p:timing", doc, XPathConstants.NODE);
    if (timing == null) {
      return; // No animations -- nothing to validate
    }

    // Root par node with tmRoot
    Element tmRoot = (Element) xp.evaluate(
        "//p:timing/p:tnLst/p:par/p:cTn[@nodeType='tmRoot']", doc, XPathConstants.NODE);
    if (tmRoot == null) {
      fail("Timing hierarchy missing tmRoot node (p:tnLst/p:par/p:cTn[@nodeType='tmRoot'])");
    }

    // mainSeq inside tmRoot
    Element mainSeq = (Element) xp.evaluate(
        "//p:timing/p:tnLst/p:par/p:cTn[@nodeType='tmRoot']//p:cTn[@nodeType='mainSeq']",
        doc, XPathConstants.NODE);
    if (mainSeq == null) {
      fail("Timing hierarchy missing mainSeq node inside tmRoot");
    }

    // seq element with concurrent attribute
    Element seq = (Element) xp.evaluate(
        "//p:timing/p:tnLst/p:par/p:cTn[@nodeType='tmRoot']/p:childTnLst/p:seq[@concurrent='1']",
        doc, XPathConstants.NODE);
    if (seq == null) {
      fail("Timing hierarchy missing p:seq[@concurrent='1'] under tmRoot");
    }

    // prevCondLst and nextCondLst on seq
    Element prevCondLst = (Element) xp.evaluate("./p:prevCondLst", seq, XPathConstants.NODE);
    if (prevCondLst == null) {
      fail("Main sequence missing p:prevCondLst");
    }

    Element nextCondLst = (Element) xp.evaluate("./p:nextCondLst", seq, XPathConstants.NODE);
    if (nextCondLst == null) {
      fail("Main sequence missing p:nextCondLst");
    }
  }

  /**
   * Asserts all p:cTn/@id values are unique and positive integers.
   */
  public static void assertUniqueTimingNodeIds(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    NodeList cTnIds = (NodeList) xp.evaluate("//p:cTn/@id", doc, XPathConstants.NODESET);
    Set<Integer> seenIds = new HashSet<>();

    for (int i = 0; i < cTnIds.getLength(); i++) {
      String idStr = cTnIds.item(i).getTextContent();
      int id;
      try {
        id = Integer.parseInt(idStr);
      } catch (NumberFormatException e) {
        fail("Timing node ID is not a valid integer: '" + idStr + "'");
        return;
      }
      if (id <= 0) {
        fail("Timing node ID must be positive, found: " + id);
      }
      if (!seenIds.add(id)) {
        fail("Duplicate timing node ID: " + id);
      }
    }
  }

  /**
   * Asserts click trigger nodes have delay="indefinite" and stCondLst structure.
   */
  public static void assertClickTriggersValid(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    // Click triggers are direct children of mainSeq's childTnLst
    NodeList clickTriggers = (NodeList) xp.evaluate(
        "//p:seq[@concurrent='1']/p:cTn[@nodeType='mainSeq']/p:childTnLst/p:par",
        doc, XPathConstants.NODESET);

    for (int i = 0; i < clickTriggers.getLength(); i++) {
      Element clickPar = (Element) clickTriggers.item(i);
      Element cTn = (Element) xp.evaluate("./p:cTn", clickPar, XPathConstants.NODE);
      if (cTn == null) {
        fail("Click trigger par[" + i + "] missing p:cTn child");
      }

      Element stCondLst = (Element) xp.evaluate("./p:stCondLst", cTn, XPathConstants.NODE);
      if (stCondLst == null) {
        fail("Click trigger par[" + i + "] p:cTn missing p:stCondLst");
      }

      String delay = xp.evaluate("./p:stCondLst/p:cond/@delay", cTn);
      if (!"indefinite".equals(delay)) {
        fail("Click trigger par[" + i + "] expected delay='indefinite', found: '" + delay + "'");
      }
    }
  }

  /**
   * Asserts every animated SPID references a shape that exists in spTree.
   */
  public static void assertAnimationsTargetExistingShapes(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    // Collect all SPIDs from spTree
    Set<String> shapeSpids = new HashSet<>();
    NodeList shapeCNvPrs = (NodeList) xp.evaluate("//p:spTree//p:cNvPr/@id", doc, XPathConstants.NODESET);
    for (int i = 0; i < shapeCNvPrs.getLength(); i++) {
      shapeSpids.add(shapeCNvPrs.item(i).getTextContent());
    }

    // Collect all animated SPIDs from timing tree
    NodeList animatedSpids = (NodeList) xp.evaluate("//p:spTgt/@spid", doc, XPathConstants.NODESET);
    for (int i = 0; i < animatedSpids.getLength(); i++) {
      String spid = animatedSpids.item(i).getTextContent();
      if (!shapeSpids.contains(spid)) {
        fail("Animation targets SPID " + spid + " which does not exist in spTree. "
            + "Available SPIDs: " + shapeSpids);
      }
    }
  }

  /**
   * Asserts every entrance/exit animated SPID has a matching p:bldP entry.
   * Emphasis-only animations do not require build list entries (MS-OE376 4.6.16).
   */
  public static void assertBuildListComplete(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    Element timing = (Element) xp.evaluate("//p:timing", doc, XPathConstants.NODE);
    if (timing == null) {
      return;
    }

    // Only collect entrance/exit animated SPIDs -- emphasis animations omit bldP
    NodeList animatedSpids = (NodeList) xp.evaluate(
        "//p:cTn[@presetClass='entr' or @presetClass='exit']//p:spTgt/@spid",
        doc, XPathConstants.NODESET);
    Set<String> animatedSet = new HashSet<>();
    for (int i = 0; i < animatedSpids.getLength(); i++) {
      animatedSet.add(animatedSpids.item(i).getTextContent());
    }

    if (animatedSet.isEmpty()) {
      return;
    }

    // Collect build list SPIDs
    Element bldLst = (Element) xp.evaluate("//p:timing/p:bldLst", doc, XPathConstants.NODE);
    if (bldLst == null) {
      fail("Entrance/exit animations present for SPIDs " + animatedSet + " but p:bldLst is missing");
      return;
    }

    Set<String> buildSpids = new HashSet<>();
    NodeList bldEntries = (NodeList) xp.evaluate("./p:bldP/@spid", bldLst, XPathConstants.NODESET);
    for (int i = 0; i < bldEntries.getLength(); i++) {
      buildSpids.add(bldEntries.item(i).getTextContent());
    }

    for (String spid : animatedSet) {
      if (!buildSpids.contains(spid)) {
        fail("Shape SPID " + spid + " is animated but missing from build list. "
            + "Build list SPIDs: " + buildSpids);
      }
    }
  }

  /**
   * Asserts all p:bldP entries have non-empty spid and grpId attributes,
   * and grpId is a non-negative integer.
   */
  public static void assertBuildListAttributesValid(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    NodeList bldEntries = (NodeList) xp.evaluate("//p:bldLst/p:bldP", doc, XPathConstants.NODESET);
    for (int i = 0; i < bldEntries.getLength(); i++) {
      Element bldP = (Element) bldEntries.item(i);
      String spid = bldP.getAttribute("spid");
      String grpId = bldP.getAttribute("grpId");

      if (spid == null || spid.isEmpty()) {
        fail("Build list entry[" + i + "] missing spid attribute");
      }
      if (grpId == null || grpId.isEmpty()) {
        fail("Build list entry[" + i + "] missing grpId attribute");
      }

      try {
        int grpIdVal = Integer.parseInt(grpId);
        if (grpIdVal < 0) {
          fail("Build list entry[" + i + "] grpId must be non-negative, found: " + grpIdVal);
        }
      } catch (NumberFormatException e) {
        fail("Build list entry[" + i + "] grpId is not a valid integer: '" + grpId + "'");
      }
    }
  }

  /**
   * Asserts entrance animations (transition="in") have a sibling p:set with val="visible".
   */
  public static void assertEntranceVisibility(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    NodeList entranceAnims = (NodeList) xp.evaluate(
        "//p:animEffect[@transition='in']", doc, XPathConstants.NODESET);

    for (int i = 0; i < entranceAnims.getLength(); i++) {
      Element animEffect = (Element) entranceAnims.item(i);
      Element parent = (Element) animEffect.getParentNode();

      Element visibleSet = (Element) xp.evaluate(
          "./p:set[.//p:strVal/@val='visible']", parent, XPathConstants.NODE);
      if (visibleSet == null) {
        // Check one level up -- the container's childTnLst
        Element grandparent = (Element) parent.getParentNode();
        if (grandparent != null) {
          visibleSet = (Element) xp.evaluate(
              ".//p:set[.//p:strVal/@val='visible']", grandparent, XPathConstants.NODE);
        }
      }
      if (visibleSet == null) {
        fail("Entrance animation[" + i + "] missing visibility set to 'visible'");
      }
    }
  }

  /**
   * Asserts exit animations (transition="out") have a sibling p:set with val="hidden".
   */
  public static void assertExitVisibility(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    NodeList exitAnims = (NodeList) xp.evaluate(
        "//p:animEffect[@transition='out']", doc, XPathConstants.NODESET);

    for (int i = 0; i < exitAnims.getLength(); i++) {
      Element animEffect = (Element) exitAnims.item(i);
      Element parent = (Element) animEffect.getParentNode();

      Element hiddenSet = (Element) xp.evaluate(
          "./p:set[.//p:strVal/@val='hidden']", parent, XPathConstants.NODE);
      if (hiddenSet == null) {
        Element grandparent = (Element) parent.getParentNode();
        if (grandparent != null) {
          hiddenSet = (Element) xp.evaluate(
              ".//p:set[.//p:strVal/@val='hidden']", grandparent, XPathConstants.NODE);
        }
      }
      if (hiddenSet == null) {
        fail("Exit animation[" + i + "] missing visibility set to 'hidden'");
      }
    }
  }

  /**
   * Asserts all animation group nodeType values are valid
   * (clickEffect, withEffect, or afterEffect).
   */
  public static void assertAnimationGroupTypes(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();
    Set<String> validNodeTypes = Set.of("clickEffect", "withEffect", "afterEffect");

    // Animation group nodes are cTn elements with a nodeType that indicates animation grouping
    NodeList groupCTns = (NodeList) xp.evaluate(
        "//p:cTn[@nodeType and @presetClass]", doc, XPathConstants.NODESET);

    for (int i = 0; i < groupCTns.getLength(); i++) {
      Element cTn = (Element) groupCTns.item(i);
      String nodeType = cTn.getAttribute("nodeType");
      if (!validNodeTypes.contains(nodeType)) {
        fail("Animation group cTn[" + i + "] has invalid nodeType: '" + nodeType
            + "'. Valid values: " + validNodeTypes);
      }
    }
  }

  /**
   * Asserts that withEffect/afterEffect are never the first animation in a click trigger group.
   * The first animation under each click trigger must be clickEffect.
   */
  public static void assertNoLeadingWithOrAfter(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    // Each click trigger is a p:par under mainSeq's childTnLst
    NodeList clickTriggers = (NodeList) xp.evaluate(
        "//p:seq[@concurrent='1']/p:cTn[@nodeType='mainSeq']/p:childTnLst/p:par",
        doc, XPathConstants.NODESET);

    for (int i = 0; i < clickTriggers.getLength(); i++) {
      Element clickPar = (Element) clickTriggers.item(i);

      // Find all animation group containers within this click trigger
      NodeList animGroupCTns = (NodeList) xp.evaluate(
          ".//p:cTn[@nodeType and @presetClass]", clickPar, XPathConstants.NODESET);

      if (animGroupCTns.getLength() > 0) {
        Element firstAnim = (Element) animGroupCTns.item(0);
        String firstNodeType = firstAnim.getAttribute("nodeType");
        if ("withEffect".equals(firstNodeType) || "afterEffect".equals(firstNodeType)) {
          fail("Click trigger[" + i + "] first animation is '" + firstNodeType
              + "' -- must be 'clickEffect'");
        }
      }
    }
  }

  /**
   * Asserts bidirectional grpId<->bldLst linkage (MS-OI29500 cTn section h):
   * - Every cTn with grpId must have a matching bldP with same (spid, grpId)
   * - Every bldP must be referenced by a cTn with matching (spid, grpId)
   */
  public static void assertGrpIdBldLstLinkage(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    Element timing = (Element) xp.evaluate("//p:timing", doc, XPathConstants.NODE);
    if (timing == null) {
      return;
    }

    // Collect (spid, grpId) pairs from timing tree cTn nodes
    Set<String> timingPairs = new HashSet<>();
    NodeList grpIdCTns = (NodeList) xp.evaluate(
        "//p:cTn[@grpId and @presetClass]", doc, XPathConstants.NODESET);
    for (int i = 0; i < grpIdCTns.getLength(); i++) {
      Element cTn = (Element) grpIdCTns.item(i);
      String grpId = cTn.getAttribute("grpId");
      // Find the spid targeted by this animation
      Element spTgt = (Element) xp.evaluate(
          ".//p:spTgt", cTn, XPathConstants.NODE);
      if (spTgt != null) {
        String spid = spTgt.getAttribute("spid");
        timingPairs.add(spid + "/" + grpId);
      }
    }

    // Collect (spid, grpId) pairs from bldLst
    Set<String> buildPairs = new HashSet<>();
    NodeList bldEntries = (NodeList) xp.evaluate(
        "//p:bldLst/p:bldP", doc, XPathConstants.NODESET);
    for (int i = 0; i < bldEntries.getLength(); i++) {
      Element bldP = (Element) bldEntries.item(i);
      String spid = bldP.getAttribute("spid");
      String grpId = bldP.getAttribute("grpId");
      buildPairs.add(spid + "/" + grpId);
    }

    // Forward: every timing grpId must have a bldP match
    for (String pair : timingPairs) {
      if (!buildPairs.contains(pair)) {
        fail("cTn has grpId for " + pair + " but no matching bldP in bldLst. "
            + "bldLst entries: " + buildPairs);
      }
    }

    // Reverse: every bldP must be referenced by a timing cTn
    for (String pair : buildPairs) {
      if (!timingPairs.contains(pair)) {
        fail("bldP entry " + pair + " has no matching cTn with grpId in timing tree. "
            + "Timing pairs: " + timingPairs);
      }
    }
  }

  /**
   * Asserts all SPIDs within a slide's spTree are unique (no duplicates).
   */
  public static void assertUniqueShapeSpids(Document doc) throws XPathExpressionException {
    XPath xp = createConfiguredXPath();

    NodeList allIds = (NodeList) xp.evaluate("//p:spTree//p:cNvPr/@id", doc, XPathConstants.NODESET);
    Set<String> seenIds = new HashSet<>();

    for (int i = 0; i < allIds.getLength(); i++) {
      String id = allIds.item(i).getTextContent();
      if (!seenIds.add(id)) {
        fail("Duplicate shape SPID in spTree: " + id);
      }
    }
  }

  /**
   * Asserts the slide document root has required OOXML namespaces (p, a, r).
   */
  public static void assertRequiredNamespaces(Document doc) {
    Element root = doc.getDocumentElement();
    String pNs = root.getAttributeNS("http://www.w3.org/2000/xmlns/", "p");
    String aNs = root.getAttributeNS("http://www.w3.org/2000/xmlns/", "a");
    String rNs = root.getAttributeNS("http://www.w3.org/2000/xmlns/", "r");

    if (pNs == null || pNs.isEmpty()) {
      fail("Root element missing 'p' namespace declaration (presentation namespace)");
    }
    if (aNs == null || aNs.isEmpty()) {
      fail("Root element missing 'a' namespace declaration (drawing namespace)");
    }
    if (rNs == null || rNs.isEmpty()) {
      fail("Root element missing 'r' namespace declaration (relationships namespace)");
    }

    if (!XMLConstants.PRESENTATION_NS.equals(pNs)) {
      fail("Expected p namespace '" + XMLConstants.PRESENTATION_NS + "', found: '" + pNs + "'");
    }
    if (!XMLConstants.DRAWING_NS.equals(aNs)) {
      fail("Expected a namespace '" + XMLConstants.DRAWING_NS + "', found: '" + aNs + "'");
    }
    if (!XMLConstants.RELATIONSHIPS_NS.equals(rNs)) {
      fail("Expected r namespace '" + XMLConstants.RELATIONSHIPS_NS + "', found: '" + rNs + "'");
    }
  }
}
