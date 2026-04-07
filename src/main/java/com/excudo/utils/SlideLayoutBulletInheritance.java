package com.excudo.utils;

import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Handles bullet style inheritance from slideLayout and slideMaster definitions.
 * Implements the 3-tier OOXML inheritance chain:
 *   1. Slide layout lstStyle on body placeholder
 *   2. Slide master txStyles > bodyStyle
 *   3. Logged fallback with console warning
 */
public class SlideLayoutBulletInheritance {

    private static final ComponentLogger logger = Logger.getLogger(SlideLayoutBulletInheritance.class);

    private final XPath xpath;
    private final Map<String, BulletStyleCache> layoutCaches;

    public SlideLayoutBulletInheritance(XPath xpath) {
        this.xpath = xpath;
        this.layoutCaches = new HashMap<>();
    }

    /**
     * Load bullet styles from a slideLayout file
     */
    public void loadSlideLayoutBulletStyles(File slideLayoutFile, String layoutId) {
        try {
            Document layoutDocument = XMLFactoryProvider.parseDocument(slideLayoutFile);

            BulletStyleCache cache = new BulletStyleCache();

            // Find body placeholder by type first, then fall back to any indexed placeholder
            NodeList placeholders = (NodeList) xpath.evaluate(
                "//p:sp[p:nvSpPr/p:nvPr/p:ph[@type='body']]",
                layoutDocument, XPathConstants.NODESET);

            if (placeholders.getLength() == 0) {
                placeholders = (NodeList) xpath.evaluate(
                    "//p:sp[p:nvSpPr/p:nvPr/p:ph[@idx and not(@type)]]",
                    layoutDocument, XPathConstants.NODESET);
            }

            for (int i = 0; i < placeholders.getLength(); i++) {
                Element placeholder = (Element) placeholders.item(i);
                parseLayoutBulletStyles(placeholder, cache);
            }

            layoutCaches.put(layoutId, cache);

        } catch (Exception e) {
            logger.warn("Failed to load slideLayout bullet styles: {}", e.getMessage());
        }
    }

    /**
     * Parse bullet styles from a placeholder's lstStyle
     */
    private void parseLayoutBulletStyles(Element placeholder, BulletStyleCache cache) {
        try {
            Element lstStyle = (Element) xpath.evaluate(".//a:lstStyle", placeholder, XPathConstants.NODE);
            if (lstStyle == null) return;

            for (int level = 1; level <= 9; level++) {
                String levelName = "lvl" + level + "pPr";
                Element levelPr = (Element) xpath.evaluate("a:" + levelName, lstStyle, XPathConstants.NODE);

                if (levelPr != null) {
                    InheritedBulletProperties props = parseLevelBulletProperties(levelPr);
                    if (props != null) {
                        cache.setBulletProperties(level - 1, props);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to parse layout bullet styles: {}", e.getMessage());
        }
    }

    /**
     * Parse bullet properties from a level definition.
     * Supports buChar, buAutoNum, buBlip, and buNone.
     */
    private InheritedBulletProperties parseLevelBulletProperties(Element levelPr) {
        try {
            Element buChar = (Element) xpath.evaluate("a:buChar", levelPr, XPathConstants.NODE);
            Element buFont = (Element) xpath.evaluate("a:buFont", levelPr, XPathConstants.NODE);
            Element buNone = (Element) xpath.evaluate("a:buNone", levelPr, XPathConstants.NODE);
            Element buAutoNum = (Element) xpath.evaluate("a:buAutoNum", levelPr, XPathConstants.NODE);
            Element buBlip = (Element) xpath.evaluate("a:buBlip", levelPr, XPathConstants.NODE);

            if (buNone != null) {
                return null;
            }

            String bulletChar = null;
            String bulletFont = null;

            if (buChar != null && buChar.hasAttribute("char")) {
                bulletChar = buChar.getAttribute("char");
            } else if (buAutoNum != null && buAutoNum.hasAttribute("type")) {
                bulletChar = "[" + buAutoNum.getAttribute("type") + "]";
            } else if (buBlip != null) {
                bulletChar = "[image-bullet]";
            }

            if (buFont != null && buFont.hasAttribute("typeface")) {
                bulletFont = buFont.getAttribute("typeface");
            }

            if (bulletChar != null) {
                return new InheritedBulletProperties(bulletChar, bulletFont);
            }

        } catch (Exception e) {
            logger.warn("Failed to parse level bullet properties: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get inherited bullet properties for a placeholder type and level
     */
    public InheritedBulletProperties getInheritedBulletProperties(String layoutId, int bulletLevel) {
        BulletStyleCache cache = layoutCaches.get(layoutId);
        if (cache != null) {
            return cache.getBulletProperties(bulletLevel);
        }
        return null;
    }

    /**
     * Check if bullet inheritance is available for a layout
     */
    public boolean hasInheritedBulletStyles(String layoutId) {
        return layoutCaches.containsKey(layoutId);
    }

    /**
     * Get inherited bullet properties for content placeholders.
     * Implements 3-tier inheritance: layout lstStyle -> master txStyles/bodyStyle -> logged fallback.
     */
    public InheritedBulletProperties getInheritedContentBulletProperties(File slideLayoutDir, int bulletLevel) {
        if (slideLayoutDir == null || !slideLayoutDir.exists()) {
            logger.warn("No inherited bullet style found for level {}; using default (bullet, Arial)", bulletLevel);
            return new InheritedBulletProperties("\u2022", "Arial");
        }

        // Tier 1: Check all slide layout files for body placeholder lstStyle
        File[] layoutFiles = slideLayoutDir.listFiles(
            (dir, name) -> name.matches("slideLayout\\d+\\.xml"));

        if (layoutFiles != null && layoutFiles.length > 0) {
            Arrays.sort(layoutFiles, (a, b) -> {
                int numA = extractLayoutNumber(a.getName());
                int numB = extractLayoutNumber(b.getName());
                return Integer.compare(numA, numB);
            });

            for (File layoutFile : layoutFiles) {
                String layoutId = layoutFile.getName().replace(".xml", "");
                if (!hasInheritedBulletStyles(layoutId)) {
                    loadSlideLayoutBulletStyles(layoutFile, layoutId);
                }

                InheritedBulletProperties props = getInheritedBulletProperties(layoutId, bulletLevel);
                if (props != null) {
                    return props;
                }

                // Try closest available level as fallback within this layout
                BulletStyleCache cache = layoutCaches.get(layoutId);
                if (cache != null && cache.getLevelCount() > 0) {
                    for (int fallbackLevel = bulletLevel; fallbackLevel >= 0; fallbackLevel--) {
                        InheritedBulletProperties fallbackProps = cache.getBulletProperties(fallbackLevel);
                        if (fallbackProps != null) {
                            return fallbackProps;
                        }
                    }
                    for (int fallbackLevel = bulletLevel + 1; fallbackLevel < 9; fallbackLevel++) {
                        InheritedBulletProperties fallbackProps = cache.getBulletProperties(fallbackLevel);
                        if (fallbackProps != null) {
                            return fallbackProps;
                        }
                    }
                }
            }

            // Tier 2: Resolve slide master via first layout's .rels and check txStyles/bodyStyle
            if (layoutFiles.length > 0) {
                InheritedBulletProperties masterProps = resolveFromMaster(layoutFiles[0], bulletLevel);
                if (masterProps != null) {
                    return masterProps;
                }
            }
        }

        // Tier 3: Logged fallback
        logger.warn("No inherited bullet style found for level {}; using default (bullet, Arial)", bulletLevel);
        return new InheritedBulletProperties("\u2022", "Arial");
    }

    /**
     * Resolve bullet properties from the slide master's txStyles > bodyStyle.
     * Navigates from layout file to master via the layout's .rels file.
     */
    private InheritedBulletProperties resolveFromMaster(File layoutFile, int bulletLevel) {
        try {
            // Find the .rels file for this layout
            File relsDir = new File(layoutFile.getParentFile(), "_rels");
            File relsFile = new File(relsDir, layoutFile.getName() + ".rels");
            if (!relsFile.exists()) {
                return null;
            }

            Document relsDoc = XMLFactoryProvider.parseDocument(relsFile);
            String masterTarget = null;

            NodeList rels = relsDoc.getElementsByTagName("Relationship");
            for (int i = 0; i < rels.getLength(); i++) {
                Element rel = (Element) rels.item(i);
                String type = rel.getAttribute("Type");
                if (type != null && type.contains("slideMaster")) {
                    masterTarget = rel.getAttribute("Target");
                    break;
                }
            }

            if (masterTarget == null) {
                return null;
            }

            // Resolve master path relative to layout directory
            File masterFile = new File(layoutFile.getParentFile(), masterTarget).getCanonicalFile();
            if (!masterFile.exists()) {
                return null;
            }

            Document masterDoc = XMLFactoryProvider.parseDocument(masterFile);

            // Look for p:txStyles > p:bodyStyle > a:lvlNpPr
            String levelName = "a:lvl" + (bulletLevel + 1) + "pPr";
            Element levelPr = (Element) xpath.evaluate(
                "//p:txStyles/p:bodyStyle/" + levelName,
                masterDoc, XPathConstants.NODE);

            if (levelPr != null) {
                InheritedBulletProperties props = parseLevelBulletProperties(levelPr);
                if (props != null) {
                    return props;
                }
            }

            // Try level 1 as fallback if specific level not found
            if (bulletLevel > 0) {
                Element lvl1 = (Element) xpath.evaluate(
                    "//p:txStyles/p:bodyStyle/a:lvl1pPr",
                    masterDoc, XPathConstants.NODE);
                if (lvl1 != null) {
                    return parseLevelBulletProperties(lvl1);
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to resolve master bullet styles: {}", e.getMessage());
        }
        return null;
    }

    private int extractLayoutNumber(String filename) {
        try {
            String num = filename.replaceAll("[^0-9]", "");
            return num.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Container for inherited bullet properties
     */
    public static class InheritedBulletProperties {
        public final String bulletChar;
        public final String bulletFont;

        public InheritedBulletProperties(String bulletChar, String bulletFont) {
            this.bulletChar = bulletChar;
            this.bulletFont = bulletFont;
        }

        @Override
        public String toString() {
            return "InheritedBulletProperties{char='" + bulletChar + "', font='" + bulletFont + "'}";
        }
    }

    /**
     * Cache for bullet styles by level
     */
    private static class BulletStyleCache {
        private final Map<Integer, InheritedBulletProperties> levelStyles = new HashMap<>();

        public void setBulletProperties(int level, InheritedBulletProperties properties) {
            levelStyles.put(level, properties);
        }

        public InheritedBulletProperties getBulletProperties(int level) {
            return levelStyles.get(level);
        }

        public int getLevelCount() {
            return levelStyles.size();
        }
    }
}
