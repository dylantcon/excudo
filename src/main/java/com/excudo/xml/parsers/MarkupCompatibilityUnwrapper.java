package com.excudo.xml.parsers;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pre-processes an OOXML document by unwrapping
 * {@code <mc:AlternateContent>} elements according to the OOXML Markup
 * Compatibility specification (ECMA-376 Part 3 / ISO/IEC 29500-3).
 *
 * <p>The {@code mc:AlternateContent} element wraps a primary
 * {@code mc:Choice} (which uses extension namespaces like
 * {@code a14} for Office 2010 features) and a {@code mc:Fallback} (the
 * version older readers can render). Without unwrapping, our XPath
 * shape walks miss the Choice branch entirely -- so slides that author
 * math, charts, or 2010+ effects in the Choice path render with
 * empty bodies.
 *
 * <p>Selection rule:
 * <ol>
 *   <li>Pick the first {@code mc:Choice} whose {@code Requires}
 *       attribute lists only namespaces we support.</li>
 *   <li>If no Choice qualifies, fall back to {@code mc:Fallback}.</li>
 *   <li>If neither exists, drop the {@code mc:AlternateContent}
 *       element entirely.</li>
 * </ol>
 *
 * <p>Supported {@code Requires} namespaces: {@code a14} (Office 2010
 * drawing extensions, including OMML math). New supported extensions
 * extend {@link #SUPPORTED_NAMESPACES}.
 */
public final class MarkupCompatibilityUnwrapper {

    private static final String MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    /**
     * Namespace prefixes we know how to render. A {@code mc:Choice}
     * whose {@code Requires} is a subset of this set wins selection.
     * Adding {@code a14} here means we now read math (via OMML) and
     * other 2010 drawing extensions; if a Choice requires something
     * outside this set (e.g., a custom vendor extension), we fall
     * back to the {@code mc:Fallback} branch which is guaranteed to
     * use only the base ECMA-376 vocabulary.
     */
    private static final Set<String> SUPPORTED_NAMESPACES = Set.of("a14");

    private MarkupCompatibilityUnwrapper() {}

    /**
     * Walk the document and replace every {@code mc:AlternateContent}
     * element with the children of its selected branch (Choice or
     * Fallback). Mutates the supplied document in place.
     */
    public static void unwrap(Document document) {
        if (document == null) return;
        Element root = document.getDocumentElement();
        if (root == null) return;
        unwrapElement(root);
    }

    private static void unwrapElement(Element element) {
        // Snapshot children first; we'll mutate the tree below.
        List<Element> kids = childElements(element);
        for (Element kid : kids) {
            if (isAlternateContent(kid)) {
                replaceWithSelectedBranch(kid);
            } else {
                unwrapElement(kid);
            }
        }
    }

    private static boolean isAlternateContent(Element el) {
        return MC_NS.equals(el.getNamespaceURI()) && "AlternateContent".equals(el.getLocalName());
    }

    /** Replace an {@code mc:AlternateContent} node with the chosen
     *  branch's children, then recurse into those children so nested
     *  AlternateContent (yes, this happens) is unwrapped too. */
    private static void replaceWithSelectedBranch(Element altContent) {
        Element selected = pickBranch(altContent);
        Node parent = altContent.getParentNode();
        if (parent == null) return;

        if (selected == null) {
            // No usable branch -- drop the wrapper to keep the document well-formed.
            parent.removeChild(altContent);
            return;
        }

        // Move every child of the selected branch into the parent at
        // the position of altContent, in document order.
        Document doc = altContent.getOwnerDocument();
        List<Node> moved = new ArrayList<>();
        NodeList branchKids = selected.getChildNodes();
        for (int i = 0; i < branchKids.getLength(); i++) {
            moved.add(branchKids.item(i).cloneNode(true));
        }
        for (Node m : moved) {
            doc.adoptNode(m);
            parent.insertBefore(m, altContent);
        }
        parent.removeChild(altContent);

        // Recurse on the freshly-inserted children (in case of nested
        // AlternateContent), filtering to elements only.
        for (Node m : moved) {
            if (m instanceof Element me) unwrapElement(me);
        }
    }

    /**
     * Selection algorithm: prefer the first {@code mc:Choice} whose
     * {@code Requires} attribute references only namespaces we support.
     * Falls back to {@code mc:Fallback} if no Choice qualifies.
     */
    private static Element pickBranch(Element altContent) {
        Element fallback = null;
        for (Element kid : childElements(altContent)) {
            if (!MC_NS.equals(kid.getNamespaceURI())) continue;
            String local = kid.getLocalName();
            if ("Choice".equals(local)) {
                String requires = kid.getAttribute("Requires");
                if (allNamespacesSupported(requires)) return kid;
            } else if ("Fallback".equals(local) && fallback == null) {
                fallback = kid;
            }
        }
        return fallback;
    }

    /** True iff every space-separated namespace prefix in {@code requires}
     *  is in our supported set. Empty requires matches (vacuously true). */
    private static boolean allNamespacesSupported(String requires) {
        if (requires == null || requires.isBlank()) return true;
        for (String token : requires.trim().split("\\s+")) {
            if (!SUPPORTED_NAMESPACES.contains(token)) return false;
        }
        return true;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element e) out.add(e);
        }
        return out;
    }
}
