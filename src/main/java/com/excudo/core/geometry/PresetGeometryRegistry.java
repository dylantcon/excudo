package com.excudo.core.geometry;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The ECMA-376 preset shape geometry catalogue: lazily parses the
 * vendored {@code presetShapeDefinitions.xml} (all ~187 presets, see
 * {@code docs/THIRD_PARTY.md} for provenance) once, thread-safely, into
 * a name &rarr; {@link GeometryDefinition} map.
 *
 * <p>{@link #get} throws for unknown preset names. There is deliberately
 * NO fallback: a preset the registry lacks is either a typo in the
 * caller or a hole in the vendored file, and both must fail loudly --
 * a bounding-box substitute here is exactly the fake-correctness the
 * geometry engine replaces.
 */
public final class PresetGeometryRegistry {

    private PresetGeometryRegistry() {}

    private static final String RESOURCE = "/geometry/presetShapeDefinitions.xml";

    /** Lazy holder: the JVM guarantees exactly-once, thread-safe init. */
    private static final class Holder {
        static final Map<String, GeometryDefinition> DEFS = load();
    }

    /**
     * The definition for an OOXML preset name (e.g. {@code roundRect}).
     *
     * @throws IllegalArgumentException for names absent from the spec
     *         definitions -- never falls back.
     */
    public static GeometryDefinition get(String presetName) {
        GeometryDefinition def = Holder.DEFS.get(presetName);
        if (def == null) {
            throw new IllegalArgumentException("Unknown preset geometry '" + presetName
                + "': not in the vendored ECMA-376 preset definitions");
        }
        return def;
    }

    public static boolean contains(String presetName) {
        return Holder.DEFS.containsKey(presetName);
    }

    public static int size() {
        return Holder.DEFS.size();
    }

    public static Set<String> names() {
        return java.util.Collections.unmodifiableSet(Holder.DEFS.keySet());
    }

    private static Map<String, GeometryDefinition> load() {
        try (InputStream in = PresetGeometryRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE
                    + " -- the vendored preset definitions were not copied into the build");
            }
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(in);

            Map<String, GeometryDefinition> defs = new LinkedHashMap<>(256);
            Element root = doc.getDocumentElement();
            for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element el)) continue;
                String preset = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                defs.put(preset, CustomGeometryParser.parseDefinition(preset, el));
            }
            if (defs.size() < 180) {
                throw new IllegalStateException("Vendored preset definitions look truncated: "
                    + defs.size() + " entries (expected >= 180)");
            }
            return defs;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + RESOURCE, e);
        }
    }
}
