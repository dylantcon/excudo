package com.excudo.core.utils;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

/**
 * Centralized provider for XML parsing factories.
 * Eliminates duplicate DocumentBuilderFactory and XPathFactory initialization
 * scattered across 35+ files in the codebase.
 *
 * Thread-safe: Uses ThreadLocal for DocumentBuilder (not thread-safe by design)
 * and creates new XPath instances as needed (lightweight).
 *
 * Security: Configures XXE (XML External Entity) attack protection by default.
 */
public final class XMLFactoryProvider {

    // Feature flags for XXE protection
    private static final String FEATURE_DISALLOW_DOCTYPE =
        "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
        "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
        "http://xml.org/sax/features/external-parameter-entities";
    private static final String FEATURE_LOAD_EXTERNAL_DTD =
        "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    // Singleton factory instances (thread-safe, immutable after init)
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;
    private static final XPathFactory XPATH_FACTORY;

    // ThreadLocal for DocumentBuilder (not thread-safe)
    private static final ThreadLocal<DocumentBuilder> DOCUMENT_BUILDER_THREAD_LOCAL;

    static {
        // Initialize DocumentBuilderFactory with namespace awareness and XXE protection
        DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);

        // Configure XXE protection - fail silently if features not supported
        configureXXEProtection(DOCUMENT_BUILDER_FACTORY);

        // Initialize XPathFactory (thread-safe)
        XPATH_FACTORY = XPathFactory.newInstance();

        // ThreadLocal DocumentBuilder for thread safety
        DOCUMENT_BUILDER_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
            try {
                return DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Failed to create DocumentBuilder", e);
            }
        });
    }

    private XMLFactoryProvider() {
        // Utility class - no instantiation
    }

    /**
     * Configures XXE attack protection on the DocumentBuilderFactory.
     * Silently ignores unsupported features for maximum compatibility.
     */
    private static void configureXXEProtection(DocumentBuilderFactory factory) {
        try {
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        } catch (ParserConfigurationException ignored) {
            // Feature not supported - continue with other protections
        }

        try {
            factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        } catch (ParserConfigurationException ignored) {
            // Feature not supported
        }

        try {
            factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        } catch (ParserConfigurationException ignored) {
            // Feature not supported
        }

        try {
            factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
        } catch (ParserConfigurationException ignored) {
            // Feature not supported
        }

        // Additional protection
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
    }

    /**
     * Returns a thread-local DocumentBuilder configured for PowerPoint OOXML parsing.
     * The builder is namespace-aware and has XXE protection enabled.
     *
     * Note: Call reset() on the returned builder before parsing if you need
     * to clear any previously set EntityResolver or ErrorHandler.
     *
     * @return a configured DocumentBuilder for the current thread
     */
    public static DocumentBuilder getDocumentBuilder() {
        DocumentBuilder builder = DOCUMENT_BUILDER_THREAD_LOCAL.get();
        builder.reset(); // Clear any state from previous use
        return builder;
    }

    /**
     * Creates a new DocumentBuilder instance.
     * Use this when you need a dedicated builder that won't be shared.
     *
     * @return a new configured DocumentBuilder
     * @throws ParserConfigurationException if builder creation fails
     */
    public static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        return DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
    }

    /**
     * Returns a new XPath instance configured with PowerPoint namespace context.
     * XPath instances are lightweight and safe to create per-use.
     *
     * @return a new XPath with PowerPointNamespaceContext configured
     */
    public static XPath createXPath() {
        XPath xpath = XPATH_FACTORY.newXPath();
        xpath.setNamespaceContext(XMLConstants.createNamespaceContext());
        return xpath;
    }

    /**
     * Returns a new XPath instance without namespace context.
     * Use this for parsing non-namespaced XML (e.g., [Content_Types].xml).
     *
     * @return a new XPath without namespace context
     */
    public static XPath createXPathWithoutNamespace() {
        return XPATH_FACTORY.newXPath();
    }

    /**
     * Convenience method: Parse an XML file and return a Document.
     *
     * @param file the XML file to parse
     * @return parsed Document
     * @throws IOException if file cannot be read
     * @throws SAXException if XML is malformed
     */
    public static Document parseDocument(File file) throws IOException, SAXException {
        return getDocumentBuilder().parse(file);
    }

    /**
     * Convenience method: Parse XML from a string and return a Document.
     */
    public static Document parseDocumentFromString(String xml) throws IOException, SAXException {
        return getDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    /**
     * Convenience method: Parse XML from an InputStream and return a Document.
     *
     * @param inputStream the XML input stream to parse
     * @return parsed Document
     * @throws IOException if stream cannot be read
     * @throws SAXException if XML is malformed
     */
    public static Document parseDocument(InputStream inputStream) throws IOException, SAXException {
        return getDocumentBuilder().parse(inputStream);
    }

    /**
     * Convenience method: Parse XML from a String and return a Document.
     *
     * @param xmlString the XML string to parse
     * @return parsed Document
     * @throws IOException if parsing fails
     * @throws SAXException if XML is malformed
     */
    public static Document parseDocument(String xmlString) throws IOException, SAXException {
        return getDocumentBuilder().parse(new InputSource(new StringReader(xmlString)));
    }

    /**
     * Creates a new empty Document for building XML programmatically.
     *
     * @return a new empty Document
     */
    public static Document createDocument() {
        return getDocumentBuilder().newDocument();
    }

    /**
     * Cleans up ThreadLocal resources. Call this when shutting down
     * thread pools or when a thread is about to be destroyed.
     */
    public static void cleanup() {
        DOCUMENT_BUILDER_THREAD_LOCAL.remove();
    }
}
