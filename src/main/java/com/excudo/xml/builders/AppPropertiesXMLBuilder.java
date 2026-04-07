package com.excudo.xml.builders;

import com.excudo.core.utils.XMLConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating docProps/app.xml files.
 * Handles extended properties per ECMA-376 Part 1 Section 22.2 and
 * MS-OE376 Section 7.2.2 (HeadingPairs, TitlesOfParts).
 *
 * Used at scaffold time for initial creation, and at save time by
 * PresentationOrchestrationManager to update dynamic values (slide count, etc.).
 */
public class AppPropertiesXMLBuilder {

    private String application = "Excudo";
    private String appVersion = "1.0000";
    private String presentationFormat = XMLConstants.DocProperties.FORMAT_WIDESCREEN;
    private int slides = 0;
    private int paragraphs = 0;
    private int words = 0;
    private int notes = 0;
    private int hiddenSlides = 0;
    private int mmClips = 0;
    private int totalTime = 0;
    private boolean scaleCrop = false;
    private boolean linksUpToDate = false;
    private boolean sharedDoc = false;
    private boolean hyperlinksChanged = false;

    private final List<String> fontsUsed = new ArrayList<>();
    private String themeName = null;
    private final List<String> slideTitles = new ArrayList<>();

    public AppPropertiesXMLBuilder withApplication(String application) {
        this.application = application;
        return this;
    }

    public AppPropertiesXMLBuilder withAppVersion(String appVersion) {
        this.appVersion = appVersion;
        return this;
    }

    public AppPropertiesXMLBuilder withPresentationFormat(String format) {
        this.presentationFormat = format;
        return this;
    }

    public AppPropertiesXMLBuilder withSlides(int slides) {
        this.slides = slides;
        return this;
    }

    public AppPropertiesXMLBuilder withParagraphs(int paragraphs) {
        this.paragraphs = paragraphs;
        return this;
    }

    public AppPropertiesXMLBuilder withWords(int words) {
        this.words = words;
        return this;
    }

    public AppPropertiesXMLBuilder withNotes(int notes) {
        this.notes = notes;
        return this;
    }

    public AppPropertiesXMLBuilder withHiddenSlides(int hiddenSlides) {
        this.hiddenSlides = hiddenSlides;
        return this;
    }

    public AppPropertiesXMLBuilder withTotalTime(int totalTime) {
        this.totalTime = totalTime;
        return this;
    }

    public AppPropertiesXMLBuilder addFont(String fontName) {
        if (!fontsUsed.contains(fontName)) {
            fontsUsed.add(fontName);
        }
        return this;
    }

    public AppPropertiesXMLBuilder withThemeName(String themeName) {
        this.themeName = themeName;
        return this;
    }

    public AppPropertiesXMLBuilder addSlideTitle(String title) {
        slideTitles.add(title);
        return this;
    }

    public String build() {
        StringBuilder xml = new StringBuilder();
        xml.append(XMLConstants.XML_DECLARATION).append("\n");
        xml.append("<Properties xmlns=\"").append(XMLConstants.DocProperties.EXTENDED_PROPERTIES_NS).append("\"");
        xml.append(" xmlns:vt=\"").append(XMLConstants.DocProperties.VT_TYPES_NS).append("\">");

        xml.append("<Template></Template>");
        xml.append("<TotalTime>").append(totalTime).append("</TotalTime>");
        xml.append("<Words>").append(words).append("</Words>");
        xml.append("<Application>").append(escapeXml(application)).append("</Application>");
        xml.append("<PresentationFormat>").append(escapeXml(presentationFormat)).append("</PresentationFormat>");
        xml.append("<Paragraphs>").append(paragraphs).append("</Paragraphs>");
        xml.append("<Slides>").append(slides).append("</Slides>");
        xml.append("<Notes>").append(notes).append("</Notes>");
        xml.append("<HiddenSlides>").append(hiddenSlides).append("</HiddenSlides>");
        xml.append("<MMClips>").append(mmClips).append("</MMClips>");
        xml.append("<ScaleCrop>").append(scaleCrop).append("</ScaleCrop>");

        buildHeadingPairsAndTitles(xml);

        xml.append("<LinksUpToDate>").append(linksUpToDate).append("</LinksUpToDate>");
        xml.append("<SharedDoc>").append(sharedDoc).append("</SharedDoc>");
        xml.append("<HyperlinksChanged>").append(hyperlinksChanged).append("</HyperlinksChanged>");
        xml.append("<AppVersion>").append(escapeXml(appVersion)).append("</AppVersion>");

        xml.append("</Properties>");
        return xml.toString();
    }

    /**
     * Builds HeadingPairs and TitlesOfParts vectors per MS-OE376 Section 7.2.2.8.
     * PowerPoint uses three heading groups: Fonts Used, Theme, Slide Titles.
     * The vector sizes must match exactly.
     */
    private void buildHeadingPairsAndTitles(StringBuilder xml) {
        boolean hasFonts = !fontsUsed.isEmpty();
        boolean hasTheme = themeName != null;
        boolean hasTitles = !slideTitles.isEmpty();

        int pairCount = 0;
        if (hasFonts) pairCount++;
        if (hasTheme) pairCount++;
        if (hasTitles) pairCount++;

        if (pairCount == 0) {
            return;
        }

        // HeadingPairs vector: size = pairCount * 2 (label + i4 count for each pair)
        xml.append("<HeadingPairs>");
        xml.append("<vt:vector size=\"").append(pairCount * 2).append("\" baseType=\"variant\">");

        if (hasFonts) {
            xml.append("<vt:variant><vt:lpstr>").append(XMLConstants.DocProperties.HEADING_FONTS_USED).append("</vt:lpstr></vt:variant>");
            xml.append("<vt:variant><vt:i4>").append(fontsUsed.size()).append("</vt:i4></vt:variant>");
        }
        if (hasTheme) {
            xml.append("<vt:variant><vt:lpstr>").append(XMLConstants.DocProperties.HEADING_THEME).append("</vt:lpstr></vt:variant>");
            xml.append("<vt:variant><vt:i4>1</vt:i4></vt:variant>");
        }
        if (hasTitles) {
            xml.append("<vt:variant><vt:lpstr>").append(XMLConstants.DocProperties.HEADING_SLIDE_TITLES).append("</vt:lpstr></vt:variant>");
            xml.append("<vt:variant><vt:i4>").append(slideTitles.size()).append("</vt:i4></vt:variant>");
        }

        xml.append("</vt:vector>");
        xml.append("</HeadingPairs>");

        // TitlesOfParts vector: total = fonts + themes + slide titles
        int totalParts = fontsUsed.size() + (hasTheme ? 1 : 0) + slideTitles.size();
        xml.append("<TitlesOfParts>");
        xml.append("<vt:vector size=\"").append(totalParts).append("\" baseType=\"lpstr\">");

        for (String font : fontsUsed) {
            xml.append("<vt:lpstr>").append(escapeXml(font)).append("</vt:lpstr>");
        }
        if (hasTheme) {
            xml.append("<vt:lpstr>").append(escapeXml(themeName)).append("</vt:lpstr>");
        }
        for (String title : slideTitles) {
            xml.append("<vt:lpstr>").append(escapeXml(title)).append("</vt:lpstr>");
        }

        xml.append("</vt:vector>");
        xml.append("</TitlesOfParts>");
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    public static AppPropertiesXMLBuilder create() {
        return new AppPropertiesXMLBuilder();
    }
}
