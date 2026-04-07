package com.excudo.xml.validation;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates DOM Documents against ECMA-376 PresentationML XSD schemas.
 *
 * Uses javax.xml.validation with pre-compiled Schema (expensive ~500ms init,
 * then fast per-document validation). Singleton pattern to avoid recompilation.
 *
 * Schema files loaded from classpath: /schemas/ecma376/pml.xsd
 * (which imports dml-main.xsd, shared-commonSimpleTypes.xsd, etc.)
 */
public final class OOXMLSchemaValidator {

    private static final Schema PML_SCHEMA;
    private static final String SCHEMA_INIT_ERROR;

    static {
        Schema schema = null;
        String error = null;
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            URL pmlUrl = OOXMLSchemaValidator.class.getResource("/schemas/ecma376/pml.xsd");
            if (pmlUrl == null) {
                throw new IllegalStateException("pml.xsd not found on classpath at /schemas/ecma376/pml.xsd");
            }
            // pml.xsd imports dml-main.xsd and shared schemas via relative paths,
            // which resolve correctly because they're all in the same directory
            schema = factory.newSchema(pmlUrl);
        } catch (SAXException | IllegalStateException e) {
            error = "Failed to compile PresentationML schema: " + e.getMessage();
        }
        PML_SCHEMA = schema;
        SCHEMA_INIT_ERROR = error;
    }

    private OOXMLSchemaValidator() {}

    /**
     * Validates a slide DOM Document against the ECMA-376 PresentationML XSD.
     *
     * @param slideDoc a DOM Document representing a PresentationML slide (p:sld root)
     * @return validation result with errors and warnings
     */
    public static SchemaValidationResult validate(Document slideDoc) {
        if (PML_SCHEMA == null) {
            return SchemaValidationResult.initFailure(SCHEMA_INIT_ERROR);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            Validator validator = PML_SCHEMA.newValidator();
            validator.setErrorHandler(new CollectingErrorHandler(errors, warnings));
            validator.validate(new DOMSource(slideDoc));
        } catch (SAXException e) {
            errors.add("Fatal validation error: " + e.getMessage());
        } catch (IOException e) {
            errors.add("I/O error during validation: " + e.getMessage());
        }

        return new SchemaValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Check if schema was loaded successfully (useful for skipping tests gracefully).
     */
    public static boolean isSchemaAvailable() {
        return PML_SCHEMA != null;
    }

    /**
     * Get the schema initialization error message, if any.
     */
    public static String getSchemaInitError() {
        return SCHEMA_INIT_ERROR;
    }

    // ========== RESULT TYPE ==========

    public static final class SchemaValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        SchemaValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        static SchemaValidationResult initFailure(String reason) {
            List<String> errors = new ArrayList<>();
            errors.add("Schema initialization failed: " + reason);
            return new SchemaValidationResult(false, errors, Collections.emptyList());
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }

        public String getSummary() {
            if (valid && warnings.isEmpty()) return "PASS";
            StringBuilder sb = new StringBuilder();
            if (!valid) {
                sb.append("FAIL (").append(errors.size()).append(" errors)");
                for (String err : errors) {
                    sb.append("\n  - ").append(err);
                }
            }
            if (!warnings.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("WARNINGS (").append(warnings.size()).append(")");
                for (String warn : warnings) {
                    sb.append("\n  - ").append(warn);
                }
            }
            return sb.toString();
        }
    }

    // ========== ERROR HANDLER ==========

    private static class CollectingErrorHandler implements ErrorHandler {
        private final List<String> errors;
        private final List<String> warnings;

        CollectingErrorHandler(List<String> errors, List<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }

        @Override
        public void warning(SAXParseException e) {
            warnings.add(formatException("WARNING", e));
        }

        @Override
        public void error(SAXParseException e) {
            errors.add(formatException("ERROR", e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            errors.add(formatException("FATAL", e));
            throw e;
        }

        private String formatException(String level, SAXParseException e) {
            return String.format("%s [line %d, col %d]: %s",
                level, e.getLineNumber(), e.getColumnNumber(), e.getMessage());
        }
    }
}
