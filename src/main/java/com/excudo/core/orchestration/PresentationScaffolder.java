package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.xml.builders.AppPropertiesXMLBuilder;
import com.excudo.xml.builders.ContentTypesXMLBuilder;
import com.excudo.xml.builders.PresPropsXMLBuilder;
import com.excudo.xml.builders.PresentationXMLBuilder;
import com.excudo.xml.builders.RelationshipXMLBuilder;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Creates a complete PPTX directory structure from scratch.
 * No seeder/template file required -- generates all 15+ required XML parts.
 *
 * Usage:
 *   File dir = PresentationScaffolder.scaffold("minimal");
 *   orchestrator.initialize(dir);
 *   orchestrator.savePresentation(new File("output.pptx"));
 */
public final class PresentationScaffolder {

    private static final ComponentLogger logger = Logger.console();

    private PresentationScaffolder() {}

    /**
     * Create a complete PPTX directory structure with the specified theme.
     *
     * @param themeId Theme to apply (e.g. "minimal", "corporate", "academic")
     * @return Extracted directory ready for PPTXOrchestratorImpl.initialize()
     */
    public static File scaffold(String themeId) throws IOException {
        ThemeLoader.initialize();
        ThemeDefinition theme = ThemeLoader.get(themeId);
        return scaffold(theme);
    }

    /**
     * Create a complete PPTX directory structure with the given theme definition.
     *
     * @param theme The theme definition to apply
     * @return Extracted directory ready for PPTXOrchestratorImpl.initialize()
     */
    public static File scaffold(ThemeDefinition theme) throws IOException {
        File tempDir = Files.createTempDirectory("pptx_scaffold_").toFile();
        File root = new File(tempDir, "extracted");
        root.mkdirs();

        try {
            createDirectoryStructure(root);
            writeContentTypes(root, theme);
            writeMainRels(root);
            writePresentationXML(root);
            writePresentationRels(root, theme);
            writeThemeAndMasterAndLayouts(root, theme);
            writePresProps(root);
            writeViewProps(root);
            writeTableStyles(root);
            writeCoreProperties(root);
            writeAppProperties(root, theme);

            logger.info("Scaffolded new presentation with theme '" + theme.getId()
                    + "' at " + root.getAbsolutePath());
            return root;
        } catch (Exception e) {
            throw new IOException("Failed to scaffold presentation: " + e.getMessage(), e);
        }
    }

    /**
     * Create a PPTXDocument in-memory without any temp directory.
     *
     * @param themeId Theme to apply
     * @return PPTXDocument ready for initialize()
     */
    public static PPTXDocument scaffoldDocument(String themeId) throws IOException {
        ThemeLoader.initialize();
        ThemeDefinition theme = ThemeLoader.get(themeId);
        return scaffoldDocument(theme);
    }

    /**
     * Create a PPTXDocument in-memory from a theme definition.
     */
    public static PPTXDocument scaffoldDocument(ThemeDefinition theme) throws IOException {
        PPTXDocument doc = PPTXDocument.createEmpty();

        try {
            // Content types
            ContentTypesXMLBuilder ctBuilder = ContentTypesXMLBuilder.create()
                    .withStandardDefaults()
                    .addImageDefaults()
                    .addPresentation("/ppt/presentation.xml")
                    .addSlideMaster("/ppt/slideMasters/slideMaster1.xml")
                    .addTheme("/ppt/theme/theme1.xml")
                    .addOverride("/ppt/presProps.xml",
                            "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml")
                    .addOverride("/ppt/viewProps.xml",
                            "application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml")
                    .addOverride("/ppt/tableStyles.xml",
                            "application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml")
                    .addOverride("/docProps/core.xml",
                            "application/vnd.openxmlformats-package.core-properties+xml")
                    .addOverride("/docProps/app.xml",
                            "application/vnd.openxmlformats-officedocument.extended-properties+xml");
            List<LayoutDefinition> layouts = theme.getLayouts();
            for (int i = 1; i <= layouts.size(); i++) {
                ctBuilder.addSlideLayout("/ppt/slideLayouts/slideLayout" + i + ".xml");
            }
            doc.putXmlPart("[Content_Types].xml", parseXmlString(ctBuilder.build()));

            // Main rels
            String mainRels = RelationshipXMLBuilder.create()
                    .addOfficeDocument("rId1", "ppt/presentation.xml")
                    .addRelationship("rId2",
                            "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
                            "docProps/core.xml")
                    .addRelationship("rId3",
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties",
                            "docProps/app.xml")
                    .build();
            doc.putXmlPart("_rels/.rels", parseXmlString(mainRels));

            // Presentation XML
            doc.putXmlPart("ppt/presentation.xml",
                    parseXmlString(PresentationXMLBuilder.createWithDefaults().build()));

            // Presentation rels
            RelationshipXMLBuilder presRelsBuilder = RelationshipXMLBuilder.create()
                    .addSlideMaster("slideMasters/slideMaster1.xml")
                    .addRelationship(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps",
                            "presProps.xml")
                    .addRelationship(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps",
                            "viewProps.xml")
                    .addTheme("theme/theme1.xml")
                    .addRelationship(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles",
                            "tableStyles.xml");
            doc.putXmlPart("ppt/_rels/presentation.xml.rels", parseXmlString(presRelsBuilder.build()));

            // Theme, master, and layouts
            ThemeXMLGenerator gen = new ThemeXMLGenerator();
            int masterNumber = 1;
            doc.putXmlPart("ppt/theme/theme" + masterNumber + ".xml", gen.generateThemeXML(theme));
            doc.putXmlPart("ppt/slideMasters/slideMaster" + masterNumber + ".xml",
                    gen.generateSlideMasterXML(theme));
            doc.putXmlPart("ppt/slideMasters/_rels/slideMaster" + masterNumber + ".xml.rels",
                    gen.generateSlideMasterRels(theme, masterNumber));
            for (int i = 0; i < layouts.size(); i++) {
                int layoutNum = i + 1;
                doc.putXmlPart("ppt/slideLayouts/slideLayout" + layoutNum + ".xml",
                        gen.generateSlideLayoutXML(theme, layouts.get(i)));
                doc.putXmlPart("ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels",
                        gen.generateSlideLayoutRels(masterNumber));
            }

            // PresProps
            doc.putXmlPart("ppt/presProps.xml",
                    parseXmlString(PresPropsXMLBuilder.createWithDefaults().build()));

            // ViewProps
            String viewPropsXml = buildViewPropsXml();
            doc.putXmlPart("ppt/viewProps.xml", parseXmlString(viewPropsXml));

            // TableStyles
            String guid = "{" + UUID.randomUUID().toString().toUpperCase() + "}";
            String tableStylesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<a:tblStyleLst xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                    + " def=\"" + guid + "\"/>";
            doc.putXmlPart("ppt/tableStyles.xml", parseXmlString(tableStylesXml));

            // Core properties
            String now = ZonedDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_INSTANT);
            doc.putXmlPart("docProps/core.xml", parseXmlString(buildCorePropsXml(now)));

            // App properties
            doc.putXmlPart("docProps/app.xml", parseXmlString(
                    AppPropertiesXMLBuilder.create()
                            .withPresentationFormat("Widescreen")
                            .withThemeName(theme.getDisplayName())
                            .build()));

            logger.info("Scaffolded in-memory presentation with theme '" + theme.getId() + "'");
            return doc;
        } catch (Exception e) {
            throw new IOException("Failed to scaffold in-memory presentation: " + e.getMessage(), e);
        }
    }

    private static Document parseXmlString(String xml) throws Exception {
        return XMLFactoryProvider.createDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String buildViewPropsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<p:viewPr xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:normalViewPr><p:restoredLeft sz=\"15620\"/>"
                + "<p:restoredTop sz=\"94660\"/></p:normalViewPr>"
                + "<p:slideViewPr><p:cSldViewPr><p:cViewPr varScale=\"1\">"
                + "<p:scale><a:sx n=\"100\" d=\"100\"/><a:sy n=\"100\" d=\"100\"/></p:scale>"
                + "<p:origin x=\"0\" y=\"0\"/></p:cViewPr>"
                + "<p:guideLst/></p:cSldViewPr></p:slideViewPr>"
                + "<p:notesTextViewPr><p:cViewPr>"
                + "<p:scale><a:sx n=\"1\" d=\"1\"/><a:sy n=\"1\" d=\"1\"/></p:scale>"
                + "<p:origin x=\"0\" y=\"0\"/></p:cViewPr></p:notesTextViewPr>"
                + "<p:gridSpacing cx=\"76200\" cy=\"76200\"/>"
                + "</p:viewPr>";
    }

    private static String buildCorePropsXml(String isoTimestamp) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<cp:coreProperties"
                + " xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                + " xmlns:dcterms=\"http://purl.org/dc/terms/\""
                + " xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>Untitled Presentation</dc:title>"
                + "<dc:creator>Excudo</dc:creator>"
                + "<cp:lastModifiedBy>Excudo</cp:lastModifiedBy>"
                + "<cp:revision>1</cp:revision>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + isoTimestamp + "</dcterms:created>"
                + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + isoTimestamp + "</dcterms:modified>"
                + "</cp:coreProperties>";
    }

    private static void createDirectoryStructure(File root) {
        new File(root, "_rels").mkdirs();
        new File(root, "ppt/_rels").mkdirs();
        new File(root, "ppt/theme").mkdirs();
        new File(root, "ppt/slideMasters/_rels").mkdirs();
        new File(root, "ppt/slideLayouts/_rels").mkdirs();
        new File(root, "ppt/slides/_rels").mkdirs();
        new File(root, "docProps").mkdirs();
    }

    private static void writeContentTypes(File root, ThemeDefinition theme) throws IOException {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
                .withStandardDefaults()
                .addImageDefaults()
                .addPresentation("/ppt/presentation.xml")
                .addSlideMaster("/ppt/slideMasters/slideMaster1.xml")
                .addTheme("/ppt/theme/theme1.xml")
                .addOverride("/ppt/presProps.xml",
                        "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml")
                .addOverride("/ppt/viewProps.xml",
                        "application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml")
                .addOverride("/ppt/tableStyles.xml",
                        "application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml")
                .addOverride("/docProps/core.xml",
                        "application/vnd.openxmlformats-package.core-properties+xml")
                .addOverride("/docProps/app.xml",
                        "application/vnd.openxmlformats-officedocument.extended-properties+xml");

        List<LayoutDefinition> layouts = theme.getLayouts();
        for (int i = 1; i <= layouts.size(); i++) {
            builder.addSlideLayout("/ppt/slideLayouts/slideLayout" + i + ".xml");
        }

        Files.writeString(new File(root, "[Content_Types].xml").toPath(), builder.build());
    }

    private static void writeMainRels(File root) throws IOException {
        String rels = RelationshipXMLBuilder.create()
                .addOfficeDocument("rId1", "ppt/presentation.xml")
                .addRelationship("rId2",
                        "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
                        "docProps/core.xml")
                .addRelationship("rId3",
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties",
                        "docProps/app.xml")
                .build();
        Files.writeString(new File(root, "_rels/.rels").toPath(), rels);
    }

    private static void writePresentationXML(File root) throws IOException {
        // Empty sldIdLst -- slides will be added via create command
        String xml = PresentationXMLBuilder.createWithDefaults().build();
        Files.writeString(new File(root, "ppt/presentation.xml").toPath(), xml);
    }

    private static void writePresentationRels(File root, ThemeDefinition theme) throws IOException {
        // Order must match ReservedRIdManager.SUFFIX_RELATIONSHIPS:
        // slideMaster(s), [slides go here], presProps, viewProps, theme, tableStyles.
        // At scaffold time there are 0 slides, so suffix starts at rId2.
        // When slides are created, cascadeRelationshipIds shifts suffix rIds up.
        RelationshipXMLBuilder builder = RelationshipXMLBuilder.create()
                .addSlideMaster("slideMasters/slideMaster1.xml")
                .addRelationship(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps",
                        "presProps.xml")
                .addRelationship(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps",
                        "viewProps.xml")
                .addTheme("theme/theme1.xml")
                .addRelationship(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles",
                        "tableStyles.xml");
        Files.writeString(new File(root, "ppt/_rels/presentation.xml.rels").toPath(), builder.build());
    }

    private static void writeThemeAndMasterAndLayouts(File root, ThemeDefinition theme) throws Exception {
        ThemeXMLGenerator gen = new ThemeXMLGenerator();
        int masterNumber = 1;

        Document themeDoc = gen.generateThemeXML(theme);
        OOXMLAttributeOrder.serialize(themeDoc, new File(root, "ppt/theme/theme" + masterNumber + ".xml"));

        Document masterDoc = gen.generateSlideMasterXML(theme);
        OOXMLAttributeOrder.serialize(masterDoc, new File(root, "ppt/slideMasters/slideMaster" + masterNumber + ".xml"));

        Document masterRels = gen.generateSlideMasterRels(theme, masterNumber);
        OOXMLAttributeOrder.serialize(masterRels,
                new File(root, "ppt/slideMasters/_rels/slideMaster" + masterNumber + ".xml.rels"));

        List<LayoutDefinition> layouts = theme.getLayouts();
        for (int i = 0; i < layouts.size(); i++) {
            int layoutNum = i + 1;
            Document layoutDoc = gen.generateSlideLayoutXML(theme, layouts.get(i));
            OOXMLAttributeOrder.serialize(layoutDoc,
                    new File(root, "ppt/slideLayouts/slideLayout" + layoutNum + ".xml"));

            Document layoutRels = gen.generateSlideLayoutRels(masterNumber);
            OOXMLAttributeOrder.serialize(layoutRels,
                    new File(root, "ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels"));
        }
    }

    private static void writePresProps(File root) throws IOException {
        String xml = PresPropsXMLBuilder.createWithDefaults().build();
        Files.writeString(new File(root, "ppt/presProps.xml").toPath(), xml);
    }

    private static void writeViewProps(File root) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<p:viewPr xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:normalViewPr><p:restoredLeft sz=\"15620\"/>"
                + "<p:restoredTop sz=\"94660\"/></p:normalViewPr>"
                + "<p:slideViewPr><p:cSldViewPr><p:cViewPr varScale=\"1\">"
                + "<p:scale><a:sx n=\"100\" d=\"100\"/><a:sy n=\"100\" d=\"100\"/></p:scale>"
                + "<p:origin x=\"0\" y=\"0\"/></p:cViewPr>"
                + "<p:guideLst/></p:cSldViewPr></p:slideViewPr>"
                + "<p:notesTextViewPr><p:cViewPr>"
                + "<p:scale><a:sx n=\"1\" d=\"1\"/><a:sy n=\"1\" d=\"1\"/></p:scale>"
                + "<p:origin x=\"0\" y=\"0\"/></p:cViewPr></p:notesTextViewPr>"
                + "<p:gridSpacing cx=\"76200\" cy=\"76200\"/>"
                + "</p:viewPr>";
        Files.writeString(new File(root, "ppt/viewProps.xml").toPath(), xml);
    }

    private static void writeTableStyles(File root) throws IOException {
        String guid = "{" + UUID.randomUUID().toString().toUpperCase() + "}";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<a:tblStyleLst xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " def=\"" + guid + "\"/>";
        Files.writeString(new File(root, "ppt/tableStyles.xml").toPath(), xml);
    }

    private static void writeCoreProperties(File root) throws IOException {
        String now = ZonedDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_INSTANT);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<cp:coreProperties"
                + " xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                + " xmlns:dcterms=\"http://purl.org/dc/terms/\""
                + " xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>Untitled Presentation</dc:title>"
                + "<dc:creator>Excudo</dc:creator>"
                + "<cp:lastModifiedBy>Excudo</cp:lastModifiedBy>"
                + "<cp:revision>1</cp:revision>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created>"
                + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>"
                + "</cp:coreProperties>";
        Files.writeString(new File(root, "docProps/core.xml").toPath(), xml);
    }

    private static void writeAppProperties(File root, ThemeDefinition theme) throws IOException {
        String xml = AppPropertiesXMLBuilder.create()
                .withPresentationFormat("Widescreen")
                .withThemeName(theme.getDisplayName())
                .build();
        Files.writeString(new File(root, "docProps/app.xml").toPath(), xml);
    }
}
