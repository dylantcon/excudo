package com.excudo.core.model;

import org.w3c.dom.Document;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.exceptions.XMLParsingException;

import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * In-memory OPC package model for PPTX presentations.
 *
 * Holds ALL parts (XML and media) in memory. No temp directory is required
 * for the in-memory path (loadFromZip). The legacy path (load from extracted dir)
 * is retained for backward compatibility during migration.
 *
 * Lifecycle: load/create -> mutate (mark dirty) -> save -> close
 */
public class PPTXDocument implements AutoCloseable {

    private static final ComponentLogger logger = Logger.getLogger(PPTXDocument.class);

    // Universal OPC part store
    private final Map<String, Document> xmlParts;
    private final Map<String, MediaElement> mediaParts;
    private final Set<String> dirtyParts;

    // ZIP metadata for round-trip fidelity
    private final Map<String, Integer> compressionMethods;

    // Non-XML, non-media binary parts (e.g., vbaProject.bin, printerSettings)
    private final Map<String, byte[]> binaryParts;

    // Legacy compatibility
    private final File extractedDir;   // null for in-memory path
    private final File sourceFile;
    private File lockFile;
    private Thread shutdownHook;

    // ========== PART NAME CONSTANTS ==========

    private static final String CONTENT_TYPES_PART = "[Content_Types].xml";
    private static final String PRESENTATION_PART = "ppt/presentation.xml";
    private static final String SLIDE_PREFIX = "ppt/slides/slide";

    // ========== CONSTRUCTORS ==========

    private PPTXDocument(File extractedDir, File sourceFile) {
        this.xmlParts = new ConcurrentHashMap<>();
        this.mediaParts = new ConcurrentHashMap<>();
        this.dirtyParts = ConcurrentHashMap.newKeySet();
        this.compressionMethods = new ConcurrentHashMap<>();
        this.binaryParts = new ConcurrentHashMap<>();
        this.extractedDir = extractedDir;
        this.sourceFile = sourceFile;
    }

    // ========== FACTORY METHODS ==========

    /**
     * Load from extracted directory (legacy path, backward compatible).
     */
    public static PPTXDocument load(File extractedDir, File sourceFile) throws XMLParsingException {
        PPTXDocument doc = new PPTXDocument(extractedDir, sourceFile);
        doc.loadAllDocumentsFromDir();
        doc.createLockFile();
        return doc;
    }

    /**
     * Create from extracted directory for from-scratch presentations (legacy path).
     */
    public static PPTXDocument createNew(File extractedDir) throws XMLParsingException {
        PPTXDocument doc = new PPTXDocument(extractedDir, null);
        doc.loadAllDocumentsFromDir();
        return doc;
    }

    /**
     * Load directly from a PPTX ZIP file into memory. No temp directory created.
     */
    public static PPTXDocument loadFromZip(File pptxFile) throws XMLParsingException {
        try (FileInputStream fis = new FileInputStream(pptxFile)) {
            PPTXDocument doc = loadFromZip(fis);
            doc.createLockFileFor(pptxFile);
            return doc;
        } catch (IOException e) {
            throw new XMLParsingException("Failed to load PPTX from " + pptxFile, e);
        }
    }

    /**
     * Load directly from an input stream (ZIP format) into memory.
     */
    public static PPTXDocument loadFromZip(InputStream zipStream) throws XMLParsingException {
        PPTXDocument doc = new PPTXDocument(null, null);
        try {
            DocumentBuilder builder = XMLFactoryProvider.createDocumentBuilder();
            ZipInputStream zis = new ZipInputStream(zipStream);
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                int compressionMethod = entry.getMethod();
                if (compressionMethod < 0) compressionMethod = ZipEntry.DEFLATED;
                doc.compressionMethods.put(entryName, compressionMethod);

                byte[] bytes = zis.readAllBytes();

                if (entryName.endsWith(".xml") || entryName.endsWith(".rels")) {
                    try {
                        Document xmlDoc = builder.parse(new ByteArrayInputStream(bytes));
                        doc.xmlParts.put(entryName, xmlDoc);
                    } catch (Exception e) {
                        // Not parseable XML, store as binary
                        doc.binaryParts.put(entryName, bytes);
                    }
                } else if (entryName.startsWith("ppt/media/")) {
                    String mimeType = guessMimeType(entryName);
                    MediaElement media = new MediaElement(entryName, mimeType, bytes, compressionMethod);
                    doc.mediaParts.put(entryName, media);
                } else {
                    doc.binaryParts.put(entryName, bytes);
                }
            }

            logger.info("Loaded PPTXDocument from ZIP: {} XML parts, {} media parts, {} binary parts",
                        doc.xmlParts.size(), doc.mediaParts.size(), doc.binaryParts.size());
            return doc;

        } catch (Exception e) {
            throw new XMLParsingException("Failed to load PPTXDocument from ZIP stream", e);
        }
    }

    /**
     * Create a completely empty PPTXDocument for scaffold-based creation.
     */
    public static PPTXDocument createEmpty() {
        return new PPTXDocument(null, null);
    }

    // ========== GENERIC PART ACCESS ==========

    public Document getXmlPart(String partName) {
        return xmlParts.get(partName);
    }

    public void putXmlPart(String partName, Document doc) {
        xmlParts.put(partName, doc);
        dirtyParts.add(partName);
    }

    public void removeXmlPart(String partName) {
        xmlParts.remove(partName);
        dirtyParts.remove(partName);
    }

    public MediaElement getMediaPart(String partName) {
        return mediaParts.get(partName);
    }

    public void putMediaPart(MediaElement media) {
        mediaParts.put(media.getPartName(), media);
        dirtyParts.add(media.getPartName());
    }

    public void removeMediaPart(String partName) {
        mediaParts.remove(partName);
        dirtyParts.remove(partName);
    }

    public boolean hasPart(String partName) {
        return xmlParts.containsKey(partName) ||
               mediaParts.containsKey(partName) ||
               binaryParts.containsKey(partName);
    }

    public Set<String> getPartNames() {
        Set<String> names = new TreeSet<>();
        names.addAll(xmlParts.keySet());
        names.addAll(mediaParts.keySet());
        names.addAll(binaryParts.keySet());
        return names;
    }

    public Set<String> getPartNamesByPrefix(String prefix) {
        Set<String> result = new TreeSet<>();
        for (String name : xmlParts.keySet()) {
            if (name.startsWith(prefix)) result.add(name);
        }
        for (String name : mediaParts.keySet()) {
            if (name.startsWith(prefix)) result.add(name);
        }
        for (String name : binaryParts.keySet()) {
            if (name.startsWith(prefix)) result.add(name);
        }
        return result;
    }

    // ========== DIRTY TRACKING ==========

    public void markDirty(String partName) {
        dirtyParts.add(partName);
    }

    // ========== SLIDE DOCUMENT ACCESS (typed convenience) ==========

    public Document getSlideDocument(int slideNumber) {
        String partName = slidePartName(slideNumber);
        Document doc = xmlParts.get(partName);
        if (doc == null) {
            throw new IllegalArgumentException("Slide " + slideNumber + " not found in PPTXDocument. " +
                "Available slides: " + getSlideNumbers());
        }
        return doc;
    }

    public boolean hasSlide(int slideNumber) {
        return xmlParts.containsKey(slidePartName(slideNumber));
    }

    public void putSlideDocument(int slideNumber, Document document) {
        String partName = slidePartName(slideNumber);
        xmlParts.put(partName, document);
        dirtyParts.add(partName);
        logger.debug("Put slide {} into PPTXDocument (now dirty)", slideNumber);
    }

    public void removeSlideDocument(int slideNumber) {
        String partName = slidePartName(slideNumber);
        xmlParts.remove(partName);
        dirtyParts.remove(partName);

        // Also remove the rels file
        String relsPartName = slideRelsPartName(slideNumber);
        xmlParts.remove(relsPartName);
        dirtyParts.remove(relsPartName);

        // Legacy: also delete the file from extractedDir if it exists
        if (extractedDir != null) {
            File slideFile = new File(extractedDir, SLIDE_PREFIX + slideNumber + ".xml");
            if (slideFile.exists()) {
                slideFile.delete();
            }
        }
        logger.debug("Removed slide {} from PPTXDocument", slideNumber);
    }

    public void rekeySlides(int startFrom, int delta) {
        // Collect slide entries AND their rels that need rekeying
        Map<Integer, Document> slidesToRekey = new TreeMap<>();
        Map<Integer, Document> relsToRekey = new TreeMap<>();
        for (String partName : xmlParts.keySet()) {
            int num = extractSlideNumber(partName);
            if (num >= startFrom) {
                slidesToRekey.put(num, xmlParts.get(partName));
            }
            int relsNum = extractSlideRelsNumber(partName);
            if (relsNum >= startFrom) {
                relsToRekey.put(relsNum, xmlParts.get(partName));
            }
        }

        // Remove old keys (slides + rels)
        for (int key : slidesToRekey.keySet()) {
            String oldPart = slidePartName(key);
            xmlParts.remove(oldPart);
            dirtyParts.remove(oldPart);
        }
        for (int key : relsToRekey.keySet()) {
            String oldPart = slideRelsPartName(key);
            xmlParts.remove(oldPart);
            dirtyParts.remove(oldPart);
        }

        // Re-insert with shifted keys
        for (Map.Entry<Integer, Document> entry : slidesToRekey.entrySet()) {
            int newKey = entry.getKey() + delta;
            String newPart = slidePartName(newKey);
            xmlParts.put(newPart, entry.getValue());
            dirtyParts.add(newPart);
        }
        for (Map.Entry<Integer, Document> entry : relsToRekey.entrySet()) {
            int newKey = entry.getKey() + delta;
            String newPart = slideRelsPartName(newKey);
            xmlParts.put(newPart, entry.getValue());
            dirtyParts.add(newPart);
        }

        logger.debug("Rekeyed {} slides + {} rels from {} with delta {}",
            slidesToRekey.size(), relsToRekey.size(), startFrom, delta);
    }

    public void markSlideDirty(int slideNumber) {
        dirtyParts.add(slidePartName(slideNumber));
    }

    public int getSlideCount() {
        int count = 0;
        for (String partName : xmlParts.keySet()) {
            if (extractSlideNumber(partName) > 0) count++;
        }
        return count;
    }

    public List<Integer> getSlideNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (String partName : xmlParts.keySet()) {
            int num = extractSlideNumber(partName);
            if (num > 0) numbers.add(num);
        }
        Collections.sort(numbers);
        return numbers;
    }

    // ========== PRESENTATION.XML AND CONTENT_TYPES ACCESS ==========

    public Document getPresentationXml() {
        return xmlParts.get(PRESENTATION_PART);
    }

    public void markPresentationXmlDirty() {
        dirtyParts.add(PRESENTATION_PART);
    }

    public Document getContentTypesDoc() {
        return xmlParts.get(CONTENT_TYPES_PART);
    }

    public void markContentTypesDirty() {
        dirtyParts.add(CONTENT_TYPES_PART);
    }

    /**
     * Get a ContentTypesModel wrapping the [Content_Types].xml DOM.
     * Returns null if no content types document is loaded.
     */
    public ContentTypesModel getContentTypes() {
        Document ctDoc = getContentTypesDoc();
        return ctDoc != null ? new ContentTypesModel(ctDoc) : null;
    }

    // ========== QUERY (legacy compatibility) ==========

    public File getExtractedDir() {
        return extractedDir;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    // ========== SAVE ==========

    /**
     * Flush dirty DOMs to the extracted directory on disk.
     * Only operational when extractedDir is set (legacy path).
     * No-op for in-memory-only documents.
     */
    public void flush() throws IOException {
        if (extractedDir == null) {
            // In-memory-only mode, nothing to flush to disk
            return;
        }

        for (String partName : dirtyParts) {
            Document doc = xmlParts.get(partName);
            if (doc != null) {
                File file = new File(extractedDir, partName);
                file.getParentFile().mkdirs();
                OOXMLAttributeOrder.serialize(doc, file);
            }
        }

        logger.debug("Flushed {} dirty parts to disk", dirtyParts.size());
        dirtyParts.clear();
    }

    /**
     * Save the presentation to a file. Uses in-memory serialization
     * when no extractedDir exists, or flush+zip for legacy mode.
     */
    public void save(File outputFile) throws IOException {
        logger.info("Saving presentation: {} XML parts, {} media parts, {} dirty",
                    xmlParts.size(), mediaParts.size(), dirtyParts.size());

        if (extractedDir != null) {
            // Legacy path: flush to disk, then zip the directory
            flush();
            packageDirToZip(extractedDir, outputFile);
        } else {
            // In-memory path: serialize directly to ZIP
            saveToZip(outputFile);
        }

        logger.info("Saved presentation to {}", outputFile.getAbsolutePath());
    }

    /**
     * Save the presentation to an output stream as a ZIP.
     */
    public void save(OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            writePartsToZip(zipOut);
        }
    }

    // ========== MEDIA RECOVERY ==========

    /**
     * Dump all unsaved media parts to a cache directory for crash recovery.
     * Useful for premium icons that were downloaded but presentation wasn't saved.
     */
    public void dumpUnsavedMedia(File cacheDir) {
        if (mediaParts.isEmpty()) return;
        cacheDir.mkdirs();
        for (MediaElement media : mediaParts.values()) {
            try {
                String fileName = media.getPartName().substring(media.getPartName().lastIndexOf('/') + 1);
                File outFile = new File(cacheDir, fileName);
                Files.write(outFile.toPath(), media.getData());
                logger.debug("Dumped unsaved media: {}", fileName);
            } catch (IOException e) {
                logger.warn("Failed to dump media {}: {}", media.getPartName(), e.getMessage());
            }
        }
    }

    /**
     * Write all parts to a temporary directory on disk.
     * Used as a compatibility bridge when file-based components need a directory.
     *
     * @return The directory containing all materialized parts
     * @deprecated Scheduled for removal. Migrate callers to read from PPTXDocument directly.
     */
    @Deprecated
    public File materializeToDirectory() throws IOException {
        File tempDir = Files.createTempDirectory("pptx_materialize_").toFile();
        File root = new File(tempDir, "extracted");
        root.mkdirs();

        // Write XML parts
        for (Map.Entry<String, Document> entry : xmlParts.entrySet()) {
            File file = new File(root, entry.getKey());
            file.getParentFile().mkdirs();
            OOXMLAttributeOrder.serialize(entry.getValue(), file);
        }

        // Write media parts
        for (Map.Entry<String, MediaElement> entry : mediaParts.entrySet()) {
            File file = new File(root, entry.getKey());
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), entry.getValue().getData());
        }

        // Write binary parts
        for (Map.Entry<String, byte[]> entry : binaryParts.entrySet()) {
            File file = new File(root, entry.getKey());
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), entry.getValue());
        }

        logger.info("Materialized PPTXDocument to {}", root.getAbsolutePath());
        return root;
    }

    // ========== CLOSE ==========

    @Override
    public void close() {
        if (lockFile != null && lockFile.exists()) {
            boolean deleted = lockFile.delete();
            if (deleted) {
                logger.debug("Deleted lock file: {}", lockFile.getName());
            } else {
                logger.warn("Failed to delete lock file: {}", lockFile.getName());
            }
            lockFile = null;
        }
        // Deregister shutdown hook since we cleaned up normally
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // JVM is already shutting down -- hook will run on its own
            }
            shutdownHook = null;
        }
    }

    // ========== INTERNAL: LOAD FROM DIRECTORY ==========

    private void loadAllDocumentsFromDir() throws XMLParsingException {
        try {
            DocumentBuilder builder = XMLFactoryProvider.createDocumentBuilder();

            // Walk entire directory and load all XML/rels files
            loadXmlFilesRecursive(extractedDir, extractedDir, builder);

            // Load media files
            File mediaDir = new File(extractedDir, "ppt/media");
            if (mediaDir.exists() && mediaDir.isDirectory()) {
                File[] mediaFiles = mediaDir.listFiles();
                if (mediaFiles != null) {
                    for (File file : mediaFiles) {
                        if (file.isFile()) {
                            String partName = "ppt/media/" + file.getName();
                            byte[] data = Files.readAllBytes(file.toPath());
                            String mimeType = guessMimeType(partName);
                            mediaParts.put(partName, new MediaElement(partName, mimeType, data));
                        }
                    }
                }
            }

            logger.info("Loaded PPTXDocument from dir: {} XML parts, {} media parts",
                        xmlParts.size(), mediaParts.size());

        } catch (Exception e) {
            throw new XMLParsingException("Failed to load PPTXDocument from " + extractedDir, e);
        }
    }

    private void loadXmlFilesRecursive(File rootDir, File currentDir, DocumentBuilder builder) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Skip media directory (handled separately)
                String relPath = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                if (relPath.equals("ppt/media")) continue;
                loadXmlFilesRecursive(rootDir, file, builder);
            } else {
                String name = file.getName();
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    String partName = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                    try {
                        Document doc = builder.parse(file);
                        xmlParts.put(partName, doc);
                    } catch (Exception e) {
                        // Non-parseable XML, store as binary
                        binaryParts.put(partName, Files.readAllBytes(file.toPath()));
                    }
                } else {
                    // Non-XML, non-media binary file (e.g., .bin, .fntdata)
                    String partName = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                    if (!partName.startsWith("ppt/media/")) {
                        binaryParts.put(partName, Files.readAllBytes(file.toPath()));
                    }
                }
            }
        }
    }

    // ========== INTERNAL: SAVE ==========

    private void saveToZip(File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            writePartsToZip(zipOut);
        }
    }

    private void writePartsToZip(ZipOutputStream zipOut) throws IOException {
        // Write XML parts
        for (Map.Entry<String, Document> entry : xmlParts.entrySet()) {
            String partName = entry.getKey();
            byte[] bytes = serializeXmlToBytes(entry.getValue());
            ZipEntry ze = new ZipEntry(partName);
            int method = compressionMethods.getOrDefault(partName, ZipEntry.DEFLATED);
            ze.setMethod(method);
            if (method == ZipEntry.STORED) {
                ze.setSize(bytes.length);
                ze.setCompressedSize(bytes.length);
                ze.setCrc(computeCrc32(bytes));
            }
            zipOut.putNextEntry(ze);
            zipOut.write(bytes);
            zipOut.closeEntry();
        }

        // Write media parts
        for (Map.Entry<String, MediaElement> entry : mediaParts.entrySet()) {
            ZipEntry ze = new ZipEntry(entry.getKey());
            int method = entry.getValue().getCompressionMethod();
            ze.setMethod(method);
            byte[] data = entry.getValue().getData();
            if (method == ZipEntry.STORED) {
                ze.setSize(data.length);
                ze.setCompressedSize(data.length);
                ze.setCrc(computeCrc32(data));
            }
            zipOut.putNextEntry(ze);
            zipOut.write(data);
            zipOut.closeEntry();
        }

        // Write binary parts
        for (Map.Entry<String, byte[]> entry : binaryParts.entrySet()) {
            ZipEntry ze = new ZipEntry(entry.getKey());
            int method = compressionMethods.getOrDefault(entry.getKey(), ZipEntry.DEFLATED);
            ze.setMethod(method);
            byte[] data = entry.getValue();
            if (method == ZipEntry.STORED) {
                ze.setSize(data.length);
                ze.setCompressedSize(data.length);
                ze.setCrc(computeCrc32(data));
            }
            zipOut.putNextEntry(ze);
            zipOut.write(data);
            zipOut.closeEntry();
        }
    }

    private static byte[] serializeXmlToBytes(Document doc) throws IOException {
        // Use OOXMLAttributeOrder to serialize to a temp file, then read bytes.
        // This avoids needing an OutputStream overload at compile time.
        File tempFile = File.createTempFile("ooxml-serialize-", ".xml");
        try {
            OOXMLAttributeOrder.serialize(doc, tempFile);
            return Files.readAllBytes(tempFile.toPath());
        } finally {
            tempFile.delete();
        }
    }

    private static long computeCrc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static void packageDirToZip(File sourceDir, File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(outputFile))) {
            addDirectoryToZip(sourceDir, sourceDir, zipOut);
        }
    }

    private static void addDirectoryToZip(File rootDir, File currentDir,
                                           ZipOutputStream zipOut) throws IOException {
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirectoryToZip(rootDir, file, zipOut);
                } else {
                    String relativePath = rootDir.toPath().relativize(file.toPath()).toString();
                    relativePath = relativePath.replace('\\', '/');
                    ZipEntry entry = new ZipEntry(relativePath);
                    zipOut.putNextEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, bytesRead);
                        }
                    }
                    zipOut.closeEntry();
                }
            }
        }
    }

    // ========== INTERNAL: LOCK FILE ==========

    private void createLockFile() {
        if (sourceFile == null) return;
        createLockFileFor(sourceFile);
    }

    private void createLockFileFor(File forFile) {
        try {
            if (forFile.getParentFile() == null) return;
            lockFile = new File(forFile.getParentFile(), "~$" + forFile.getName());
            if (lockFile.createNewFile()) {
                Files.writeString(lockFile.toPath(),
                    "PID=" + ProcessHandle.current().pid() +
                    "\nTimestamp=" + System.currentTimeMillis() + "\n");
                logger.debug("Created lock file: {}", lockFile.getName());

                // Register shutdown hook for crash recovery:
                // 1. Dump unsaved premium media (icons the user paid for)
                // 2. Clean up lock file
                shutdownHook = new Thread(() -> {
                    if (!mediaParts.isEmpty()) {
                        File recoveryDir = new File(System.getProperty("user.home"),
                            ".excudo/crash-recovery");
                        dumpUnsavedMedia(recoveryDir);
                    }
                    if (lockFile != null && lockFile.exists()) {
                        lockFile.delete();
                    }
                }, "PPTXDocument-Shutdown-" + forFile.getName());
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            }
        } catch (IOException e) {
            logger.warn("Failed to create lock file: {}", e.getMessage());
            lockFile = null;
        }
    }

    // ========== INTERNAL: PART NAME UTILITIES ==========

    private static final String SLIDE_RELS_PREFIX = "ppt/slides/_rels/slide";
    private static final String SLIDE_RELS_SUFFIX = ".xml.rels";

    private static String slidePartName(int slideNumber) {
        return SLIDE_PREFIX + slideNumber + ".xml";
    }

    private static String slideRelsPartName(int slideNumber) {
        return SLIDE_RELS_PREFIX + slideNumber + SLIDE_RELS_SUFFIX;
    }

    /**
     * Extract slide number from a part name like "ppt/slides/slide3.xml".
     * Returns -1 if not a slide part.
     */
    private static int extractSlideNumber(String partName) {
        if (!partName.startsWith(SLIDE_PREFIX) || !partName.endsWith(".xml")) return -1;
        // Exclude rels files (they contain "slide" too)
        if (partName.contains("_rels")) return -1;
        String numStr = partName.substring(SLIDE_PREFIX.length(), partName.length() - 4);
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Extract slide number from a rels part name like "ppt/slides/_rels/slide3.xml.rels".
     * Returns -1 if not a slide rels part.
     */
    private static int extractSlideRelsNumber(String partName) {
        if (!partName.startsWith(SLIDE_RELS_PREFIX) || !partName.endsWith(SLIDE_RELS_SUFFIX)) return -1;
        String numStr = partName.substring(SLIDE_RELS_PREFIX.length(),
            partName.length() - SLIDE_RELS_SUFFIX.length());
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String guessMimeType(String partName) {
        String lower = partName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".emf")) return "image/x-emf";
        if (lower.endsWith(".wmf")) return "image/x-wmf";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }
}
