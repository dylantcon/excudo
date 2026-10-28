package com.excudo.test.utils;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural checks on an in-memory package, plus a fixture that turns a
 * scaffolded single-master deck into a two-master one.
 */
public final class PackageIntegrity {

    private static final String PNS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String RNS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PKG = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String LAYOUT_REL = RNS + "/slideLayout";
    private static final String THEME_REL = RNS + "/theme";

    private PackageIntegrity() {}

    /**
     * Fails (AssertionError) unless: every internal rels target exists; every
     * master's sldLayoutId r:id resolves in its rels; sldLayoutId values are
     * unique across masters; content-type overrides point at existing parts
     * and cover every layout, master, and theme part.
     */
    public static void assertIntact(PPTXDocument doc) {
        for (String part : doc.getPartNames()) {
            if (!part.endsWith(".rels")) continue;
            Document rels = doc.getXmlPart(part);
            String sourceDir = part.substring(0, part.indexOf("_rels/"));
            NodeList nodes = rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element rel = (Element) nodes.item(i);
                if ("External".equals(rel.getAttribute("TargetMode"))) continue;
                String resolved = resolve(sourceDir, rel.getAttribute("Target"));
                check(doc.hasPart(resolved), part + " -> missing part " + resolved);
            }
        }

        Set<String> ids = new HashSet<>();
        for (String part : doc.getPartNamesByPrefix("ppt/slideMasters/slideMaster")) {
            if (!part.endsWith(".xml")) continue;
            Document master = doc.getXmlPart(part);
            Document rels = doc.getXmlPart(part.replace("slideMasters/", "slideMasters/_rels/") + ".rels");
            check(rels != null, "no rels for " + part);
            Set<String> relIds = new HashSet<>();
            NodeList relNodes = rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < relNodes.getLength(); i++) relIds.add(((Element) relNodes.item(i)).getAttribute("Id"));
            NodeList layoutIds = master.getElementsByTagNameNS(PNS, "sldLayoutId");
            for (int i = 0; i < layoutIds.getLength(); i++) {
                Element el = (Element) layoutIds.item(i);
                check(relIds.contains(el.getAttributeNS(RNS, "id")), part + " sldLayoutId r:id unresolved");
                check(ids.add(el.getAttribute("id")), "duplicate sldLayoutId " + el.getAttribute("id"));
            }
        }

        Document ct = doc.getContentTypesDoc();
        Set<String> overridden = new HashSet<>();
        NodeList overrides = ct.getElementsByTagNameNS("*", "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            String name = ((Element) overrides.item(i)).getAttribute("PartName").substring(1);
            overridden.add(name);
            check(doc.hasPart(name), "content type for missing part " + name);
        }
        for (String prefix : new String[]{"ppt/slideLayouts/slideLayout", "ppt/slideMasters/slideMaster", "ppt/theme/theme"}) {
            for (String part : doc.getPartNamesByPrefix(prefix)) {
                if (part.endsWith(".xml")) check(overridden.contains(part), "no content type for " + part);
            }
        }
    }

    /**
     * Give the deck a second master (slideMaster2, with its own theme2 copied
     * from theme1) that takes ownership of the given layouts.
     */
    public static void addSecondMaster(PPTXDocument doc, int... ownedLayouts) throws Exception {
        Document m1 = doc.getXmlPart("ppt/slideMasters/slideMaster1.xml");
        Document m1Rels = doc.getXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels");

        Document m2 = copy(m1);
        Element lst = (Element) m2.getElementsByTagNameNS(PNS, "sldLayoutIdLst").item(0);
        while (lst.hasChildNodes()) lst.removeChild(lst.getFirstChild());

        Document m2Rels = XMLFactoryProvider.createDocument();
        Element root = m2Rels.createElementNS(PKG, "Relationships");
        m2Rels.appendChild(root);

        long nextId = 2147483900L;
        int rId = 1;
        for (int layout : ownedLayouts) {
            String target = "../slideLayouts/slideLayout" + layout + ".xml";
            // detach from master 1
            String removed = null;
            NodeList rels = m1Rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < rels.getLength(); i++) {
                Element rel = (Element) rels.item(i);
                if (target.equals(rel.getAttribute("Target"))) {
                    removed = rel.getAttribute("Id");
                    rel.getParentNode().removeChild(rel);
                    break;
                }
            }
            NodeList ids = m1.getElementsByTagNameNS(PNS, "sldLayoutId");
            for (int i = 0; i < ids.getLength(); i++) {
                Element el = (Element) ids.item(i);
                if (removed != null && removed.equals(el.getAttributeNS(RNS, "id"))) {
                    el.getParentNode().removeChild(el);
                    break;
                }
            }
            // attach to master 2
            root.appendChild(rel(m2Rels, "rId" + rId, LAYOUT_REL, target));
            Element id = m2.createElementNS(PNS, "p:sldLayoutId");
            id.setAttribute("id", String.valueOf(nextId++));
            id.setAttributeNS(RNS, "r:id", "rId" + rId);
            lst.appendChild(id);
            rId++;

            Document layoutRels = doc.getXmlPart("ppt/slideLayouts/_rels/slideLayout" + layout + ".xml.rels");
            NodeList lr = layoutRels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < lr.getLength(); i++) {
                Element r = (Element) lr.item(i);
                if (r.getAttribute("Type").endsWith("/slideMaster")) r.setAttribute("Target", "../slideMasters/slideMaster2.xml");
            }
            doc.markDirty("ppt/slideLayouts/_rels/slideLayout" + layout + ".xml.rels");
        }
        root.appendChild(rel(m2Rels, "rId" + rId, THEME_REL, "../theme/theme2.xml"));

        doc.putXmlPart("ppt/theme/theme2.xml", copy(doc.getXmlPart("ppt/theme/theme1.xml")));
        doc.putXmlPart("ppt/slideMasters/slideMaster1.xml", m1);
        doc.putXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels", m1Rels);
        doc.putXmlPart("ppt/slideMasters/slideMaster2.xml", m2);
        doc.putXmlPart("ppt/slideMasters/_rels/slideMaster2.xml.rels", m2Rels);

        // presentation.xml: register the master
        Document pres = doc.getPresentationXml();
        Element masterIdLst = (Element) pres.getElementsByTagNameNS(PNS, "sldMasterIdLst").item(0);
        Document presRels = doc.getXmlPart("ppt/_rels/presentation.xml.rels");
        int maxRId = 0;
        NodeList pr = presRels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < pr.getLength(); i++) {
            maxRId = Math.max(maxRId, Integer.parseInt(((Element) pr.item(i)).getAttribute("Id").substring(3)));
        }
        String masterRId = "rId" + (maxRId + 1);
        presRels.getDocumentElement().appendChild(
            rel(presRels, masterRId, RNS + "/slideMaster", "slideMasters/slideMaster2.xml"));
        Element masterId = pres.createElementNS(PNS, "p:sldMasterId");
        masterId.setAttribute("id", "2147483890");
        masterId.setAttributeNS(RNS, "r:id", masterRId);
        masterIdLst.appendChild(masterId);
        doc.markDirty("ppt/_rels/presentation.xml.rels");
        doc.markPresentationXmlDirty();

        doc.getContentTypes().addOverride("/ppt/slideMasters/slideMaster2.xml",
            "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml");
        doc.getContentTypes().addOverride("/ppt/theme/theme2.xml",
            "application/vnd.openxmlformats-officedocument.theme+xml");
        doc.markContentTypesDirty();
    }

    private static Element rel(Document rels, String id, String type, String target) {
        Element r = rels.createElementNS(PKG, "Relationship");
        r.setAttribute("Id", id);
        r.setAttribute("Type", type);
        r.setAttribute("Target", target);
        return r;
    }

    private static Document copy(Document source) throws Exception {
        Document copy = XMLFactoryProvider.createDocument();
        copy.appendChild(copy.importNode(source.getDocumentElement(), true));
        return copy;
    }

    private static String resolve(String sourceDir, String target) {
        if (target.startsWith("/")) return target.substring(1);
        List<String> segments = new ArrayList<>(List.of(sourceDir.split("/")));
        segments.removeIf(String::isEmpty);
        for (String s : target.split("/")) {
            if (s.equals("..")) segments.remove(segments.size() - 1);
            else if (!s.equals(".")) segments.add(s);
        }
        return String.join("/", segments);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
