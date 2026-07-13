package com.excudo.core.geometry;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses DrawingML geometry-definition XML into {@link GeometryDefinition}:
 * both inline {@code a:custGeom} elements from slide XML and the entries
 * of the vendored spec preset file (same grammar, ECMA-376 Part 1
 * &sect;20.1.9). All element matching is by local name so it works for
 * {@code a:}-prefixed slide XML and the default-namespaced vendored file
 * alike.
 *
 * <p>{@code ahLst} (adjust handles) and {@code cxnLst} (connection sites)
 * are recognized and skipped: both only matter for interactive editing.
 * Malformed geometry throws {@link IllegalArgumentException} rather than
 * degrading to a rectangle -- a fallback here would silently hide broken
 * geometry behind a plausible-looking box.
 */
public final class CustomGeometryParser {

    private CustomGeometryParser() {}

    /** Parse an {@code a:custGeom} element from slide XML. */
    public static GeometryDefinition parse(Element custGeomElement) {
        return parseDefinition("custGeom", custGeomElement);
    }

    /**
     * Parse the children of {@code container} (avLst/gdLst/pathLst/rect)
     * into a definition named {@code name}. Shared by {@link #parse} and
     * {@link PresetGeometryRegistry}.
     */
    static GeometryDefinition parseDefinition(String name, Element container) {
        List<GeometryDefinition.Guide> adjustDefaults = new ArrayList<>();
        List<GeometryDefinition.Guide> guides = new ArrayList<>();
        List<GeometryPath> paths = new ArrayList<>();
        GeometryDefinition.TextRect textRect = null;

        for (Element child : childElements(container)) {
            switch (localName(child)) {
                case "avLst" -> parseGuideList(child, adjustDefaults);
                case "gdLst" -> parseGuideList(child, guides);
                case "pathLst" -> {
                    for (Element pathEl : childElements(child)) {
                        if ("path".equals(localName(pathEl))) {
                            paths.add(parsePath(pathEl));
                        }
                    }
                }
                case "rect" -> textRect = new GeometryDefinition.TextRect(
                    child.getAttribute("l"), child.getAttribute("t"),
                    child.getAttribute("r"), child.getAttribute("b"));
                case "ahLst", "cxnLst" -> { /* editing-only metadata: skip */ }
                default -> { /* tolerate unknown extensions */ }
            }
        }
        return new GeometryDefinition(name, adjustDefaults, guides, paths, textRect);
    }

    private static void parseGuideList(Element listEl, List<GeometryDefinition.Guide> out) {
        for (Element gd : childElements(listEl)) {
            if (!"gd".equals(localName(gd))) continue;
            out.add(new GeometryDefinition.Guide(gd.getAttribute("name"), gd.getAttribute("fmla")));
        }
    }

    private static GeometryPath parsePath(Element pathEl) {
        double w = parseDim(pathEl, "w");
        double h = parseDim(pathEl, "h");
        GeometryPath.FillMode fill = GeometryPath.FillMode.fromXml(pathEl.getAttribute("fill"));
        boolean stroke = !isXmlFalse(pathEl.getAttribute("stroke"));
        // extrusionOk: 3D hint, ignored.

        List<GeometryPath.Command> commands = new ArrayList<>();
        for (Element cmd : childElements(pathEl)) {
            switch (localName(cmd)) {
                case "moveTo" -> {
                    String[] pt = onePoint(cmd);
                    commands.add(new GeometryPath.MoveTo(pt[0], pt[1]));
                }
                case "lnTo" -> {
                    String[] pt = onePoint(cmd);
                    commands.add(new GeometryPath.LnTo(pt[0], pt[1]));
                }
                case "arcTo" -> commands.add(new GeometryPath.ArcTo(
                    requireAttr(cmd, "wR"), requireAttr(cmd, "hR"),
                    requireAttr(cmd, "stAng"), requireAttr(cmd, "swAng")));
                case "cubicBezTo" -> {
                    String[][] pts = points(cmd, 3);
                    commands.add(new GeometryPath.CubicBezTo(
                        pts[0][0], pts[0][1], pts[1][0], pts[1][1], pts[2][0], pts[2][1]));
                }
                case "quadBezTo" -> {
                    String[][] pts = points(cmd, 2);
                    commands.add(new GeometryPath.QuadBezTo(
                        pts[0][0], pts[0][1], pts[1][0], pts[1][1]));
                }
                case "close" -> commands.add(new GeometryPath.Close());
                default -> throw new IllegalArgumentException(
                    "Unknown path command <" + localName(cmd) + ">");
            }
        }
        return new GeometryPath(w, h, fill, stroke, commands);
    }

    // ========== helpers ==========

    private static double parseDim(Element el, String attr) {
        String v = el.getAttribute(attr);
        if (v == null || v.isEmpty()) return 0;
        double parsed = Double.parseDouble(v);
        if (parsed < 0) {
            throw new IllegalArgumentException("path @" + attr + " must be >= 0, got " + v);
        }
        return parsed;
    }

    private static boolean isXmlFalse(String value) {
        return "false".equalsIgnoreCase(value) || "0".equals(value);
    }

    private static String requireAttr(Element el, String attr) {
        String v = el.getAttribute(attr);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(
                "<" + localName(el) + "> is missing required attribute @" + attr);
        }
        return v;
    }

    /** The single {@code <a:pt>} child of moveTo/lnTo, as {x, y} tokens. */
    private static String[] onePoint(Element cmd) {
        return points(cmd, 1)[0];
    }

    private static String[][] points(Element cmd, int expected) {
        String[][] out = new String[expected][];
        int n = 0;
        for (Element pt : childElements(cmd)) {
            if (!"pt".equals(localName(pt))) continue;
            if (n == expected) {
                throw new IllegalArgumentException(
                    "<" + localName(cmd) + "> has more than " + expected + " <pt> children");
            }
            out[n++] = new String[]{requireAttr(pt, "x"), requireAttr(pt, "y")};
        }
        if (n != expected) {
            throw new IllegalArgumentException(
                "<" + localName(cmd) + "> needs " + expected + " <pt> children, found " + n);
        }
        return out;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el) out.add(el);
        }
        return out;
    }

    private static String localName(Element el) {
        String local = el.getLocalName();
        if (local != null) return local;
        String tag = el.getTagName();
        int colon = tag.indexOf(':');
        return colon >= 0 ? tag.substring(colon + 1) : tag;
    }
}
