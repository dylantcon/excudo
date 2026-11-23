package com.excudo.xml.writers;

import com.excudo.core.model.ContentTypesModel;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Speaker notes on the in-memory document.
 *
 * <p>A slide's notes are the body placeholder of its notes slide
 * ({@code ppt/notesSlides/notesSlideN.xml}), which must link to a notes
 * master. Decks scaffolded here have no notes master, so the first notes
 * written create one, with its own copy of the deck's theme, as PowerPoint
 * does.
 */
public final class SpeakerNotesWriter {

    private static final String P = XMLConstants.PRESENTATION_NS;
    private static final String A = XMLConstants.DRAWING_NS;
    private static final String R = XMLConstants.RELATIONSHIPS_NS;
    private static final String PKG_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private static final String CT = "application/vnd.openxmlformats-officedocument.";
    private static final String NOTES_MASTER = "ppt/notesMasters/notesMaster1.xml";

    private SpeakerNotesWriter() {}

    /** The slide's notes text (paragraphs joined by newlines), or null when it has no notes slide. */
    public static String getNotes(PPTXDocument doc, int slideNumber) {
        String part = notesPartOf(doc, slideNumber);
        if (part == null || doc.getXmlPart(part) == null) return null;
        Element body = bodyPlaceholder(doc.getXmlPart(part));
        if (body == null) return "";
        StringBuilder text = new StringBuilder();
        NodeList paragraphs = body.getElementsByTagNameNS(A, "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            if (i > 0) text.append('\n');
            NodeList ts = ((Element) paragraphs.item(i)).getElementsByTagNameNS(A, "t");
            for (int t = 0; t < ts.getLength(); t++) text.append(ts.item(t).getTextContent());
        }
        return text.toString();
    }

    /** Replace the slide's notes with this text (one paragraph per line), creating the notes slide if needed. */
    public static void setNotes(PPTXDocument doc, int slideNumber, String text) {
        if (!doc.hasSlide(slideNumber)) throw new IllegalArgumentException("Slide " + slideNumber + " not found");
        String part = notesPartOf(doc, slideNumber);
        if (part == null || doc.getXmlPart(part) == null) {
            part = createNotesSlide(doc, slideNumber);
        }
        Document notes = doc.getXmlPart(part);
        Element sp = bodyPlaceholder(notes);
        if (sp == null) {
            sp = placeholder(notes, nextShapeId(notes), "Notes Placeholder", "body");
            spTree(notes).appendChild(sp);
        }
        writeText(sp, text == null ? "" : text);
        doc.putXmlPart(part, notes);
    }

    // ========== Notes slide ==========

    private static String createNotesSlide(PPTXDocument doc, int slideNumber) {
        String masterPart = ensureNotesMaster(doc);
        int n = 1;
        while (doc.hasPart("ppt/notesSlides/notesSlide" + n + ".xml")) n++;
        String part = "ppt/notesSlides/notesSlide" + n + ".xml";

        Document notes = XMLFactoryProvider.createDocument();
        Element root = notes.createElementNS(P, "p:notes");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", A);
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:r", R);
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:p", P);
        notes.appendChild(root);
        Element cSld = append(root, P, "p:cSld");
        Element tree = append(cSld, P, "p:spTree");
        groupProperties(tree);
        tree.appendChild(placeholder(notes, 2, "Slide Image Placeholder 1", "sldImg"));
        tree.appendChild(placeholder(notes, 3, "Notes Placeholder 2", "body"));
        append(append(root, P, "p:clrMapOvr"), A, "a:masterClrMapping");
        doc.putXmlPart(part, notes);

        Document rels = newRels();
        addRel(rels, "notesMaster", "../" + masterPart.substring("ppt/".length()));
        addRel(rels, "slide", "../slides/slide" + slideNumber + ".xml");
        doc.putXmlPart("ppt/notesSlides/_rels/notesSlide" + n + ".xml.rels", rels);

        String slideRelsPart = "ppt/slides/_rels/slide" + slideNumber + ".xml.rels";
        Document slideRels = doc.getXmlPart(slideRelsPart);
        if (slideRels == null) slideRels = newRels();
        addRel(slideRels, "notesSlide", "../notesSlides/notesSlide" + n + ".xml");
        doc.putXmlPart(slideRelsPart, slideRels);

        override(doc, part, "presentationml.notesSlide+xml");
        return part;
    }

    /** The notes slide part linked from the slide, or null. */
    private static String notesPartOf(PPTXDocument doc, int slideNumber) {
        Document rels = doc.getXmlPart("ppt/slides/_rels/slide" + slideNumber + ".xml.rels");
        String target = targetOfType(rels, "notesSlide");
        return target == null ? null : resolve("ppt/slides/", target);
    }

    // ========== Notes master ==========

    private static String ensureNotesMaster(PPTXDocument doc) {
        Document presRels = doc.getXmlPart("ppt/_rels/presentation.xml.rels");
        String existing = targetOfType(presRels, "notesMaster");
        if (existing != null) return resolve("ppt/", existing);

        // Its own copy of the deck's theme, as PowerPoint gives a notes master
        String sourceTheme = null;
        for (String name : doc.getPartNamesByPrefix("ppt/theme/")) {
            if (name.endsWith(".xml") && (sourceTheme == null || name.compareTo(sourceTheme) < 0)) sourceTheme = name;
        }
        int t = 1;
        while (doc.hasPart("ppt/theme/theme" + t + ".xml")) t++;
        String themePart = "ppt/theme/theme" + t + ".xml";
        Document theme = (Document) doc.getXmlPart(sourceTheme).cloneNode(true);
        doc.putXmlPart(themePart, theme);
        String sourceRels = sourceTheme.replace("ppt/theme/", "ppt/theme/_rels/") + ".rels";
        if (doc.getXmlPart(sourceRels) != null) {
            doc.putXmlPart("ppt/theme/_rels/theme" + t + ".xml.rels", (Document) doc.getXmlPart(sourceRels).cloneNode(true));
        }
        override(doc, themePart, "theme+xml");

        Document master = XMLFactoryProvider.createDocument();
        Element root = master.createElementNS(P, "p:notesMaster");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", A);
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:r", R);
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:p", P);
        master.appendChild(root);
        Element cSld = append(root, P, "p:cSld");
        Element tree = append(cSld, P, "p:spTree");
        groupProperties(tree);
        // Positions on the notes page, from the deck's notes size
        long[] size = notesSize(doc);
        long w = size[0], h = size[1], margin = w / 10;
        Element img = placeholder(master, 2, "Slide Image Placeholder 1", "sldImg");
        position(img, margin, h / 8, w - 2 * margin, (w - 2 * margin) * 9 / 16);
        tree.appendChild(img);
        Element body = placeholder(master, 3, "Notes Placeholder 2", "body");
        position(body, margin, h * 12 / 25, w - 2 * margin, h * 2 / 5);
        tree.appendChild(body);
        Element clrMap = append(root, P, "p:clrMap");
        String[][] map = {{"bg1", "lt1"}, {"tx1", "dk1"}, {"bg2", "lt2"}, {"tx2", "dk2"},
            {"accent1", "accent1"}, {"accent2", "accent2"}, {"accent3", "accent3"}, {"accent4", "accent4"},
            {"accent5", "accent5"}, {"accent6", "accent6"}, {"hlink", "hlink"}, {"folHlink", "folHlink"}};
        for (String[] m : map) clrMap.setAttribute(m[0], m[1]);
        doc.putXmlPart(NOTES_MASTER, master);
        override(doc, NOTES_MASTER, "presentationml.notesMaster+xml");

        Document masterRels = newRels();
        addRel(masterRels, "theme", "../theme/theme" + t + ".xml");
        doc.putXmlPart("ppt/notesMasters/_rels/notesMaster1.xml.rels", masterRels);

        String rId = addRel(presRels, "notesMaster", "notesMasters/notesMaster1.xml");
        doc.putXmlPart("ppt/_rels/presentation.xml.rels", presRels);

        // presentation.xml: notesMasterIdLst goes right after sldMasterIdLst
        Document pres = doc.getPresentationXml();
        Element presRoot = pres.getDocumentElement();
        Element list = pres.createElementNS(P, "p:notesMasterIdLst");
        Element id = pres.createElementNS(P, "p:notesMasterId");
        id.setAttributeNS(R, "r:id", rId);
        list.appendChild(id);
        Element after = firstChild(presRoot, "sldMasterIdLst");
        presRoot.insertBefore(list, after != null ? after.getNextSibling() : presRoot.getFirstChild());
        doc.markPresentationXmlDirty();
        return NOTES_MASTER;
    }

    private static long[] notesSize(PPTXDocument doc) {
        Element sz = firstChild(doc.getPresentationXml().getDocumentElement(), "notesSz");
        try {
            return new long[] {Long.parseLong(sz.getAttribute("cx")), Long.parseLong(sz.getAttribute("cy"))};
        } catch (RuntimeException e) {
            return new long[] {6_858_000L, 9_144_000L}; // portrait letter, PowerPoint's default
        }
    }

    // ========== DOM helpers ==========

    private static Element placeholder(Document d, int id, String name, String type) {
        Element sp = d.createElementNS(P, "p:sp");
        Element nv = append(sp, P, "p:nvSpPr");
        Element cNvPr = append(nv, P, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(id));
        cNvPr.setAttribute("name", name);
        Element locks = append(append(nv, P, "p:cNvSpPr"), A, "a:spLocks");
        locks.setAttribute("noGrp", "1");
        if ("sldImg".equals(type)) {
            locks.setAttribute("noRot", "1");
            locks.setAttribute("noChangeAspect", "1");
        }
        Element ph = append(append(nv, P, "p:nvPr"), P, "p:ph");
        ph.setAttribute("type", type);
        if ("body".equals(type)) ph.setAttribute("idx", "1");
        append(sp, P, "p:spPr");
        if ("body".equals(type)) writeText(sp, "");
        return sp;
    }

    private static void position(Element sp, long x, long y, long cx, long cy) {
        Element spPr = firstChild(sp, "spPr");
        Element xfrm = append(spPr, A, "a:xfrm");
        Element off = append(xfrm, A, "a:off");
        off.setAttribute("x", String.valueOf(x));
        off.setAttribute("y", String.valueOf(y));
        Element ext = append(xfrm, A, "a:ext");
        ext.setAttribute("cx", String.valueOf(cx));
        ext.setAttribute("cy", String.valueOf(cy));
        Element geom = append(spPr, A, "a:prstGeom");
        geom.setAttribute("prst", "rect");
        append(geom, A, "a:avLst");
    }

    /** Replace the shape's text body with one paragraph per line. */
    private static void writeText(Element sp, String text) {
        Document d = sp.getOwnerDocument();
        Element old = firstChild(sp, "txBody");
        Element txBody = d.createElementNS(P, "p:txBody");
        append(txBody, A, "a:bodyPr");
        append(txBody, A, "a:lstStyle");
        for (String line : text.split("\\R", -1)) {
            Element p = append(txBody, A, "a:p");
            if (line.isEmpty()) continue;
            Element r = append(p, A, "a:r");
            Element rPr = append(r, A, "a:rPr");
            rPr.setAttribute("lang", "en-US");
            rPr.setAttribute("dirty", "0");
            append(r, A, "a:t").setTextContent(line);
        }
        if (old != null) sp.replaceChild(txBody, old); else sp.appendChild(txBody);
    }

    private static void groupProperties(Element tree) {
        Element nv = append(tree, P, "p:nvGrpSpPr");
        Element cNvPr = append(nv, P, "p:cNvPr");
        cNvPr.setAttribute("id", "1");
        cNvPr.setAttribute("name", "");
        append(nv, P, "p:cNvGrpSpPr");
        append(nv, P, "p:nvPr");
        append(tree, P, "p:grpSpPr");
    }

    private static Element bodyPlaceholder(Document notes) {
        NodeList phs = notes.getElementsByTagNameNS(P, "ph");
        for (int i = 0; i < phs.getLength(); i++) {
            Element ph = (Element) phs.item(i);
            if ("body".equals(ph.getAttribute("type"))) {
                Node sp = ph.getParentNode().getParentNode().getParentNode();
                if (sp instanceof Element e && "sp".equals(e.getLocalName())) return e;
            }
        }
        return null;
    }

    private static Element spTree(Document d) {
        return (Element) d.getElementsByTagNameNS(P, "spTree").item(0);
    }

    private static int nextShapeId(Document d) {
        int max = 0;
        NodeList ids = d.getElementsByTagNameNS(P, "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) {
            try { max = Math.max(max, Integer.parseInt(((Element) ids.item(i)).getAttribute("id"))); }
            catch (NumberFormatException ignored) {}
        }
        return max + 1;
    }

    private static Document newRels() {
        Document rels = XMLFactoryProvider.createDocument();
        rels.appendChild(rels.createElementNS(PKG_RELS, "Relationships"));
        return rels;
    }

    /** Add a relationship with the next unused rId; returns that id. */
    private static String addRel(Document rels, String type, String target) {
        Element root = rels.getDocumentElement();
        int max = 0;
        NodeList existing = root.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < existing.getLength(); i++) {
            String id = ((Element) existing.item(i)).getAttribute("Id");
            if (id.startsWith("rId")) {
                try { max = Math.max(max, Integer.parseInt(id.substring(3))); } catch (NumberFormatException ignored) {}
            }
        }
        String rId = "rId" + (max + 1);
        Element rel = rels.createElementNS(PKG_RELS, "Relationship");
        rel.setAttribute("Id", rId);
        rel.setAttribute("Type", REL + type);
        rel.setAttribute("Target", target);
        root.appendChild(rel);
        return rId;
    }

    private static String targetOfType(Document rels, String type) {
        if (rels == null) return null;
        NodeList existing = rels.getDocumentElement().getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < existing.getLength(); i++) {
            Element rel = (Element) existing.item(i);
            if ((REL + type).equals(rel.getAttribute("Type"))) return rel.getAttribute("Target");
        }
        return null;
    }

    /** A relationship target relative to the source part's folder, as a part name. */
    private static String resolve(String folder, String target) {
        if (target.startsWith("/")) return target.substring(1);
        String path = folder + target;
        java.util.Deque<String> parts = new java.util.ArrayDeque<>();
        for (String seg : path.split("/")) {
            if (seg.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else if (!seg.isEmpty() && !seg.equals(".")) parts.addLast(seg);
        }
        return String.join("/", parts);
    }

    private static void override(PPTXDocument doc, String part, String type) {
        ContentTypesModel ct = doc.getContentTypes();
        if (ct == null) return;
        ct.addOverride("/" + part, CT + type);
        doc.markContentTypesDirty();
    }

    private static Element append(Element parent, String ns, String qname) {
        Element e = parent.getOwnerDocument().createElementNS(ns, qname);
        parent.appendChild(e);
        return e;
    }

    private static Element firstChild(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) return e;
        }
        return null;
    }
}
