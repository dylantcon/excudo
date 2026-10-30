package com.excudo.core.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural checks run before a save. Each finding is something PowerPoint
 * "repairs" on open -- usually by dropping content -- so a save that finds one
 * is refused instead of writing a file that silently loses slides.
 *
 * <p>Package level: every slide in presentation.xml resolves through
 * presentation.xml.rels to a part that exists, every slide part is listed
 * exactly once, and every internal relationship target exists. Slide level:
 * timing node ids are unique, animations only target shapes on the slide,
 * and the build list has at most one entry per (shape, group).
 */
public final class PackageIntegrityValidator {

    private static final String PNS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String RNS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PRES_RELS = "ppt/_rels/presentation.xml.rels";

    private PackageIntegrityValidator() {}

    /** Problems found, each a one-line description; empty when the package is sound. */
    public static List<String> validate(PPTXDocument doc) {
        List<String> problems = new ArrayList<>();
        checkSlideList(doc, problems);
        checkRelationshipTargets(doc, problems);
        for (String part : doc.getPartNamesByPrefix("ppt/slides/")) {
            if (part.matches("ppt/slides/slide\\d+\\.xml")) checkTiming(doc, part, problems);
        }
        return problems;
    }

    private static void checkSlideList(PPTXDocument doc, List<String> problems) {
        Document pres = doc.getXmlPart("ppt/presentation.xml");
        Document rels = doc.getXmlPart(PRES_RELS);
        if (pres == null || rels == null) return;
        Map<String, String> targetById = new HashMap<>();
        NodeList relNodes = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < relNodes.getLength(); i++) {
            Element rel = (Element) relNodes.item(i);
            targetById.put(rel.getAttribute("Id"), resolve("ppt/", rel.getAttribute("Target")));
        }
        Set<String> listed = new HashSet<>();
        NodeList sldIds = pres.getElementsByTagNameNS(PNS, "sldId");
        for (int i = 0; i < sldIds.getLength(); i++) {
            String rId = ((Element) sldIds.item(i)).getAttributeNS(RNS, "id");
            String target = targetById.get(rId);
            if (target == null) {
                problems.add("slide " + (i + 1) + " in presentation.xml uses " + rId
                    + ", which has no relationship in presentation.xml.rels");
            } else if (!doc.hasPart(target)) {
                problems.add("slide " + (i + 1) + " (" + rId + ") points at " + target + ", which doesn't exist");
            } else if (!listed.add(target)) {
                problems.add(target + " is listed as more than one slide");
            }
        }
        for (String part : doc.getPartNamesByPrefix("ppt/slides/")) {
            if (part.matches("ppt/slides/slide\\d+\\.xml") && !listed.contains(part)) {
                problems.add(part + " exists but isn't in the slide list (PowerPoint would drop it)");
            }
        }
    }

    private static void checkRelationshipTargets(PPTXDocument doc, List<String> problems) {
        for (String part : doc.getPartNames()) {
            if (!part.endsWith(".rels") || !part.contains("_rels/")) continue;
            Document rels = doc.getXmlPart(part);
            if (rels == null) continue;
            String sourceDir = part.substring(0, part.indexOf("_rels/"));
            NodeList nodes = rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element rel = (Element) nodes.item(i);
                if ("External".equals(rel.getAttribute("TargetMode"))) continue;
                String resolved = resolve(sourceDir, rel.getAttribute("Target"));
                if (!doc.hasPart(resolved)) {
                    problems.add(part + " " + rel.getAttribute("Id") + " points at missing part " + resolved);
                }
            }
        }
    }

    private static void checkTiming(PPTXDocument doc, String part, List<String> problems) {
        Document slide = doc.getXmlPart(part);
        if (slide == null) return;
        String name = part.substring(part.lastIndexOf('/') + 1);

        // Duplicate shape ids aren't checked: PowerPoint itself writes them
        // occasionally (19_20_javascript_events.pptx slide 21) and opens them fine
        if (slide.getElementsByTagNameNS(PNS, "timing").getLength() == 0) return;

        Set<String> shapeIds = new HashSet<>();
        NodeList cNvPr = slide.getElementsByTagNameNS(PNS, "cNvPr");
        for (int i = 0; i < cNvPr.getLength(); i++) shapeIds.add(((Element) cNvPr.item(i)).getAttribute("id"));

        Set<String> ctnIds = new HashSet<>();
        Set<String> duplicates = new java.util.TreeSet<>();
        NodeList ctns = slide.getElementsByTagNameNS(PNS, "cTn");
        for (int i = 0; i < ctns.getLength(); i++) {
            String id = ((Element) ctns.item(i)).getAttribute("id");
            if (!id.isEmpty() && !ctnIds.add(id)) duplicates.add(id);
        }
        if (!duplicates.isEmpty()) problems.add(name + ": duplicate timing node ids " + duplicates);

        Set<String> missing = new java.util.TreeSet<>();
        NodeList targets = slide.getElementsByTagNameNS(PNS, "spTgt");
        for (int i = 0; i < targets.getLength(); i++) {
            String spid = ((Element) targets.item(i)).getAttribute("spid");
            if (!shapeIds.contains(spid)) missing.add(spid);
        }
        if (!missing.isEmpty()) problems.add(name + ": animations target shapes that aren't on the slide " + missing);

        Set<String> builds = new HashSet<>();
        NodeList bldPs = slide.getElementsByTagNameNS(PNS, "bldP");
        for (int i = 0; i < bldPs.getLength(); i++) {
            Element b = (Element) bldPs.item(i);
            String key = b.getAttribute("spid") + "/" + b.getAttribute("grpId");
            if (!builds.add(key)) problems.add(name + ": duplicate build entry for shape/group " + key);
            if (!shapeIds.contains(b.getAttribute("spid"))) {
                problems.add(name + ": build entry for shape " + b.getAttribute("spid") + ", which isn't on the slide");
            }
        }
    }

    /** Resolve a relationship Target against the directory of its source part. */
    static String resolve(String sourceDir, String target) {
        if (target.startsWith("/")) return target.substring(1);
        List<String> segments = new ArrayList<>(List.of(sourceDir.split("/")));
        segments.removeIf(String::isEmpty);
        for (String s : target.split("/")) {
            if (s.equals("..")) {
                if (!segments.isEmpty()) segments.remove(segments.size() - 1);
            } else if (!s.equals(".") && !s.isEmpty()) {
                segments.add(s);
            }
        }
        return String.join("/", segments);
    }
}
