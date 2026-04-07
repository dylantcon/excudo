package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.exceptions.*;
import com.excudo.xml.builders.ContentTypesXMLBuilder;
import com.excudo.utils.NotesSlideRegistry;
import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Handles reading and writing of slide notes in PowerPoint OOXML format
 * Manages the notesSlide XML files and their relationships
 */
public class SlideNotesWriter {

    private static final ComponentLogger logger = Logger.getLogger(SlideNotesWriter.class);

    private final String presentationPath;
    private final SlideXMLParser xmlParser;

    public SlideNotesWriter(String presentationPath) throws XMLParsingException {
        this.presentationPath = presentationPath;
        this.xmlParser = new SlideXMLParser();
    }
    
    /**
     * Get the next sequential number for a notes slide by counting existing notes slides.
     * 
     * MICROSOFT'S TERRIBLE DESIGN: PowerPoint numbers notes slides sequentially (1, 2, 3...) 
     * regardless of which slide they belong to. This means:
     * - notesSlide1.xml might belong to slide 2 (if slide 2 is the first slide with notes)
     * - notesSlide2.xml might belong to slide 5 (if slide 5 is the second slide with notes)
     * - Adding notes to slide 1 later would require renaming ALL existing notes files!
     * 
     * The OOXML spec doesn't explain WHY this insane design exists. We're forced to 
     * implement this madness for compatibility.
     */
    private int getNextNotesSlideNumber(Path notesDir) throws IOException {
        if (!Files.exists(notesDir)) {
            return 1; // First notes slide
        }
        
        // Find the highest numbered notes slide
        int maxNumber = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir, "notesSlide*.xml")) {
            for (Path entry : stream) {
                String filename = entry.getFileName().toString();
                if (filename.startsWith("notesSlide") && filename.endsWith(".xml")) {
                    String numberStr = filename.substring(10, filename.length() - 4); // Extract number between "notesSlide" and ".xml"
                    try {
                        int number = Integer.parseInt(numberStr);
                        maxNumber = Math.max(maxNumber, number);
                    } catch (NumberFormatException e) {
                        // Ignore malformed filenames
                    }
                }
            }
        }
        
        return maxNumber + 1;
    }
    
    /**
     * Append text to the notes of a specific slide
     * Creates notes slide if it doesn't exist
     */
    public void appendToSlideNotes(int slideNumber, String textToAppend) throws XMLParsingException {
        try {
            Path tempDir = Files.createTempDirectory("pptx_notes_");
            
            // Extract the presentation
            extractPptx(presentationPath, tempDir);
            
            // Check if notes slide exists
            Path notesPath = tempDir.resolve("ppt/notesSlides/notesSlide" + slideNumber + ".xml");
            Path notesDir = tempDir.resolve("ppt/notesSlides");
            Path slideRelPath = tempDir.resolve("ppt/slides/_rels/slide" + slideNumber + ".xml.rels");
            
            if (!Files.exists(notesPath)) {
                // Create notes slide structure
                createNotesSlide(tempDir, slideNumber);
            }
            
            // Update the notes content
            updateNotesContent(notesPath, textToAppend);
            
            // Repackage the presentation
            repackagePptx(tempDir, presentationPath);
            
            // Cleanup
            deleteDirectory(tempDir);
            
        } catch (Exception e) {
            throw new XMLParsingException("Failed to append to slide notes", e);
        }
    }
    
    /**
     * Get current notes text from a slide
     */
    public String getSlideNotes(int slideNumber) throws XMLParsingException {
        try {
            Path tempDir = Files.createTempDirectory("pptx_notes_read_");
            
            // Extract the presentation
            extractPptx(presentationPath, tempDir);
            
            // Check if notes slide exists
            Path notesPath = tempDir.resolve("ppt/notesSlides/notesSlide" + slideNumber + ".xml");
            
            String notesText = "";
            if (Files.exists(notesPath)) {
                notesText = xmlParser.parseNotesSlide(notesPath.toFile());
            }
            
            // Cleanup
            deleteDirectory(tempDir);
            
            return notesText;
            
        } catch (Exception e) {
            throw new XMLParsingException("Failed to read slide notes", e);
        }
    }
    
    /**
     * Add icon attribution to slide notes
     */
    public void addIconAttribution(int slideNumber, String iconUrl, String attribution) 
            throws XMLParsingException {
        String attributionText = String.format(
            "\n\n--- Icon Attribution ---\nIcon URL: %s\n%s\n",
            iconUrl,
            attribution
        );
        
        appendToSlideNotes(slideNumber, attributionText);
    }
    
    /**
     * Create a new notes slide with basic structure
     */
    private void createNotesSlide(Path tempDir, int slideNumber) throws Exception {
        // Create notesSlides directory if it doesn't exist
        Path notesDir = tempDir.resolve("ppt/notesSlides");
        Files.createDirectories(notesDir);
        Files.createDirectories(notesDir.resolve("_rels"));
        
        // Create the notes slide XML
        Document doc = XMLFactoryProvider.createDocument();

        // Create root element with namespaces
        Element notes = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:notes");
        notes.setAttribute("xmlns:a", XMLConstants.DRAWING_NS);
        notes.setAttribute("xmlns:r", XMLConstants.RELATIONSHIPS_NS);
        notes.setAttribute("xmlns:p", XMLConstants.PRESENTATION_NS);
        doc.appendChild(notes);

        // Create common slide data
        Element cSld = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cSld");
        notes.appendChild(cSld);

        // Create shape tree
        Element spTree = createShapeTree(doc);
        cSld.appendChild(spTree);

        // Add color mapping override
        Element clrMapOvr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:clrMapOvr");
        Element masterClrMapping = doc.createElementNS(XMLConstants.DRAWING_NS, "a:masterClrMapping");
        clrMapOvr.appendChild(masterClrMapping);
        notes.appendChild(clrMapOvr);

        // FIXED: Use sequential numbering (1, 2, 3...) not slide numbers
        int notesSequentialNumber = getNextNotesSlideNumber(notesDir);
        
        // Save the notes slide with sequential naming
        saveDocument(doc, tempDir.resolve("ppt/notesSlides/notesSlide" + notesSequentialNumber + ".xml"));
        
        // Create relationship file for notes slide
        createNotesSlideRels(tempDir, slideNumber, notesSequentialNumber);
        
        // Update slide relationship to point to notes slide
        updateSlideRels(tempDir, slideNumber, notesSequentialNumber);
        
        // Update content types with sequential notes slide number
        updateContentTypes(tempDir, notesSequentialNumber);
    }
    
    /**
     * Create the shape tree for notes slide
     */
    private Element createShapeTree(Document doc) {
        Element spTree = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spTree");
        
        // Non-visual group shape properties
        Element nvGrpSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvGrpSpPr");
        Element cNvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
        cNvPr.setAttribute("id", "1");
        cNvPr.setAttribute("name", "");
        nvGrpSpPr.appendChild(cNvPr);
        nvGrpSpPr.appendChild(doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvGrpSpPr"));
        nvGrpSpPr.appendChild(doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr"));
        spTree.appendChild(nvGrpSpPr);
        
        // Group shape properties
        Element grpSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:grpSpPr");
        Element xfrm = doc.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
        grpSpPr.appendChild(xfrm);
        
        // Transform elements
        Element off = doc.createElementNS(XMLConstants.DRAWING_NS, "a:off");
        off.setAttribute("x", "0");
        off.setAttribute("y", "0");
        xfrm.appendChild(off);
        
        Element ext = doc.createElementNS(XMLConstants.DRAWING_NS, "a:ext");
        ext.setAttribute("cx", "0");
        ext.setAttribute("cy", "0");
        xfrm.appendChild(ext);
        
        Element chOff = doc.createElementNS(XMLConstants.DRAWING_NS, "a:chOff");
        chOff.setAttribute("x", "0");
        chOff.setAttribute("y", "0");
        xfrm.appendChild(chOff);
        
        Element chExt = doc.createElementNS(XMLConstants.DRAWING_NS, "a:chExt");
        chExt.setAttribute("cx", "0");
        chExt.setAttribute("cy", "0");
        xfrm.appendChild(chExt);
        
        spTree.appendChild(grpSpPr);
        
        // Add placeholder shapes
        spTree.appendChild(createSlidePlaceholder(doc));
        spTree.appendChild(createNotesPlaceholder(doc));
        spTree.appendChild(createSlideNumberPlaceholder(doc));
        
        return spTree;
    }
    
    /**
     * Create slide image placeholder
     */
    private Element createSlidePlaceholder(Document doc) {
        Element sp = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");
        
        // Non-visual properties
        Element nvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
        Element cNvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
        cNvPr.setAttribute("id", "2");
        cNvPr.setAttribute("name", "Slide Image Placeholder 1");
        nvSpPr.appendChild(cNvPr);
        
        Element cNvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
        Element spLocks = doc.createElementNS(XMLConstants.DRAWING_NS, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        spLocks.setAttribute("noRot", "1");
        spLocks.setAttribute("noChangeAspect", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);
        
        Element nvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
        Element ph = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:ph");
        ph.setAttribute("type", "sldImg");
        nvPr.appendChild(ph);
        nvSpPr.appendChild(nvPr);
        
        sp.appendChild(nvSpPr);
        sp.appendChild(doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr"));
        
        return sp;
    }
    
    /**
     * Create notes text placeholder
     */
    private Element createNotesPlaceholder(Document doc) {
        Element sp = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");
        
        // Non-visual properties
        Element nvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
        Element cNvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
        cNvPr.setAttribute("id", "3");
        cNvPr.setAttribute("name", "Notes Placeholder 2");
        nvSpPr.appendChild(cNvPr);
        
        Element cNvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
        Element spLocks = doc.createElementNS(XMLConstants.DRAWING_NS, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);
        
        Element nvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
        Element ph = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:ph");
        ph.setAttribute("type", "body");
        ph.setAttribute("idx", "1");
        nvPr.appendChild(ph);
        nvSpPr.appendChild(nvPr);
        
        sp.appendChild(nvSpPr);
        sp.appendChild(doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr"));
        
        // Create text body
        Element txBody = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
        txBody.appendChild(doc.createElementNS(XMLConstants.DRAWING_NS, "a:bodyPr"));
        txBody.appendChild(doc.createElementNS(XMLConstants.DRAWING_NS, "a:lstStyle"));
        
        // Add empty paragraph
        Element p = doc.createElementNS(XMLConstants.DRAWING_NS, "a:p");
        Element endParaRPr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:endParaRPr");
        endParaRPr.setAttribute("lang", "en-US");
        p.appendChild(endParaRPr);
        txBody.appendChild(p);
        
        sp.appendChild(txBody);
        
        return sp;
    }
    
    /**
     * Create slide number placeholder
     */
    private Element createSlideNumberPlaceholder(Document doc) {
        Element sp = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");
        
        // Non-visual properties
        Element nvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvSpPr");
        Element cNvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvPr");
        cNvPr.setAttribute("id", "4");
        cNvPr.setAttribute("name", "Slide Number Placeholder 3");
        nvSpPr.appendChild(cNvPr);
        
        Element cNvSpPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:cNvSpPr");
        Element spLocks = doc.createElementNS(XMLConstants.DRAWING_NS, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);
        
        Element nvPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:nvPr");
        Element ph = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:ph");
        ph.setAttribute("type", "sldNum");
        ph.setAttribute("sz", "quarter");
        ph.setAttribute("idx", "5");
        nvPr.appendChild(ph);
        nvSpPr.appendChild(nvPr);
        
        sp.appendChild(nvSpPr);
        sp.appendChild(doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr"));
        
        return sp;
    }
    
    /**
     * Update notes content by appending text
     */
    private void updateNotesContent(Path notesPath, String textToAppend) throws Exception {
        Document doc = XMLFactoryProvider.parseDocument(notesPath.toFile());

        XPath xpath = XMLFactoryProvider.createXPath();
        
        // Find the notes text body
        Element txBody = (Element) xpath.evaluate(
            "//p:sp[p:nvSpPr/p:nvPr/p:ph[@type='body']]/p:txBody",
            doc,
            XPathConstants.NODE
        );
        
        if (txBody != null) {
            // Find or create paragraph
            Element paragraph = (Element) xpath.evaluate("a:p[last()]", txBody, XPathConstants.NODE);
            
            if (paragraph == null) {
                paragraph = doc.createElementNS(XMLConstants.DRAWING_NS, "a:p");
                txBody.appendChild(paragraph);
            }
            
            // Create run with text
            Element run = doc.createElementNS(XMLConstants.DRAWING_NS, "a:r");
            Element rPr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:rPr");
            rPr.setAttribute("lang", "en-US");
            rPr.setAttribute("dirty", "0");
            run.appendChild(rPr);
            
            Element text = doc.createElementNS(XMLConstants.DRAWING_NS, "a:t");
            text.setTextContent(textToAppend);
            run.appendChild(text);
            
            // Insert before endParaRPr if it exists
            Element endParaRPr = (Element) xpath.evaluate("a:endParaRPr", paragraph, XPathConstants.NODE);
            if (endParaRPr != null) {
                paragraph.insertBefore(run, endParaRPr);
            } else {
                paragraph.appendChild(run);
                // Add endParaRPr
                endParaRPr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:endParaRPr");
                endParaRPr.setAttribute("lang", "en-US");
                paragraph.appendChild(endParaRPr);
            }
        }
        
        saveDocument(doc, notesPath);
    }
    
    
    /**
     * Create relationship file for notes slide
     */
    private void createNotesSlideRels(Path tempDir, int slideNumber, int notesSequentialNumber) throws Exception {
        Document doc = XMLFactoryProvider.createDocument();
        
        Element relationships = doc.createElementNS(
            "http://schemas.openxmlformats.org/package/2006/relationships",
            "Relationships"
        );
        doc.appendChild(relationships);
        
        // Add relationship to notes master
        Element rel = doc.createElementNS(
            "http://schemas.openxmlformats.org/package/2006/relationships",
            "Relationship"
        );
        rel.setAttribute("Id", "rId1");
        rel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster");
        rel.setAttribute("Target", "../notesMasters/notesMaster1.xml");
        relationships.appendChild(rel);
        
        // Add relationship to slide
        rel = doc.createElementNS(
            "http://schemas.openxmlformats.org/package/2006/relationships",
            "Relationship"
        );
        rel.setAttribute("Id", "rId2");
        rel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide");
        rel.setAttribute("Target", "../slides/slide" + slideNumber + ".xml");
        relationships.appendChild(rel);
        
        Path relsPath = tempDir.resolve("ppt/notesSlides/_rels/notesSlide" + notesSequentialNumber + ".xml.rels");
        saveDocument(doc, relsPath);
    }
    
    /**
     * Update slide relationships to include notes slide
     */
    private void updateSlideRels(Path tempDir, int slideNumber, int notesSequentialNumber) throws Exception {
        Path slideRelsPath = tempDir.resolve("ppt/slides/_rels/slide" + slideNumber + ".xml.rels");

        Document doc = XMLFactoryProvider.parseDocument(slideRelsPath.toFile());

        // Check if notes relationship already exists
        XPath xpath = XMLFactoryProvider.createXPathWithoutNamespace();
        Element notesRel = (Element) xpath.evaluate(
            "//Relationship[@Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide']",
            doc,
            XPathConstants.NODE
        );
        
        if (notesRel == null) {
            // Add notes slide relationship
            Element relationships = doc.getDocumentElement();
            Element rel = doc.createElementNS(
                "http://schemas.openxmlformats.org/package/2006/relationships",
                "Relationship"
            );
            
            // Find next available ID
            NodeList allRels = relationships.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/package/2006/relationships",
                "Relationship"
            );
            int maxId = 0;
            for (int i = 0; i < allRels.getLength(); i++) {
                String id = ((Element) allRels.item(i)).getAttribute("Id");
                int num = Integer.parseInt(id.substring(3));
                maxId = Math.max(maxId, num);
            }
            
            rel.setAttribute("Id", "rId" + (maxId + 1));
            rel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide");
            rel.setAttribute("Target", "../notesSlides/notesSlide" + slideNumber + ".xml");
            relationships.appendChild(rel);
            
            saveDocument(doc, slideRelsPath);
        }
    }
    
    /**
     * Update content types to include notes slides
     * FIXED: Use ContentTypesXMLBuilder to ensure proper OOXML attribute ordering
     */
    private void updateContentTypes(Path tempDir, int notesSequentialNumber) throws Exception {
        Path contentTypesPath = tempDir.resolve("[Content_Types].xml");
        
        // Parse existing content types using our builder
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.parseExisting(contentTypesPath.toFile());
        
        // Add the specific notes slide with sequential numbering
        String partName = "/ppt/notesSlides/notesSlide" + notesSequentialNumber + ".xml";
        builder.addNotesSlide(partName);
        
        // Rebuild Content_Types.xml with correct attribute ordering
        String updatedXml = builder.build();
        Files.writeString(contentTypesPath, updatedXml);
    }
    
    /**
     * Extract PPTX file to temporary directory
     */
    private void extractPptx(String pptxPath, Path tempDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(pptxPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = tempDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Repackage PPTX file from temporary directory
     */
    private void repackagePptx(Path tempDir, String pptxPath) throws IOException {
        Path tempOutput = Paths.get(pptxPath + ".tmp");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempOutput.toFile()))) {
            Files.walk(tempDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        String zipPath = tempDir.relativize(path).toString().replace('\\', '/');
                        ZipEntry entry = new ZipEntry(zipPath);
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        
        // Replace original file
        Files.move(tempOutput, Paths.get(pptxPath), StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Save XML document to file
     */
    private void saveDocument(Document doc, Path filePath) throws IOException {
        OOXMLAttributeOrder.serialize(doc, filePath.toFile());
    }
    
    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
