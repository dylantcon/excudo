package com.excudo.core.media;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Fetches and processes icons from open-source repositories
 * Note: In production, this would use proper API keys and respect rate limits
 */
public class IconFetcher {

    private static final ComponentLogger logger = Logger.system();

    // Simulated icon search - in production, this would use actual APIs
    private static final Map<String, String> ICON_MAPPINGS = Map.of(
        "email", "https://cdn-icons-png.flaticon.com/512/646/646094.png",
        "phone", "https://cdn-icons-png.flaticon.com/512/597/597177.png",
        "location", "https://cdn-icons-png.flaticon.com/512/684/684908.png",
        "time", "https://cdn-icons-png.flaticon.com/512/2838/2838779.png",
        "money", "https://cdn-icons-png.flaticon.com/512/2488/2488756.png",
        "chart", "https://cdn-icons-png.flaticon.com/512/3121/3121571.png",
        "team", "https://cdn-icons-png.flaticon.com/512/1256/1256650.png",
        "idea", "https://cdn-icons-png.flaticon.com/512/3625/3625048.png"
    );
    
    private final String cacheDir;
    
    public IconFetcher(String cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(Paths.get(cacheDir));
        } catch (IOException e) {
            logger.error("Failed to create icon cache directory: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fetch icon based on keyword with caching
     */
    public IconResult fetchIcon(String keyword, IconStyle style) {
        try {
            // Normalize keyword
            String normalizedKeyword = keyword.toLowerCase().trim();
            
            // Check cache first
            String cachedPath = getCachedIcon(normalizedKeyword, style);
            if (cachedPath != null) {
                return new IconResult(true, cachedPath, "Cached icon");
            }
            
            // Find best matching icon URL
            String iconUrl = findBestIconUrl(normalizedKeyword);
            if (iconUrl == null) {
                return new IconResult(false, null, "No icon found for: " + keyword);
            }
            
            // Download and process icon
            String processedPath = downloadAndProcessIcon(iconUrl, normalizedKeyword, style);
            return new IconResult(true, processedPath, "Icon fetched and processed");
            
        } catch (Exception e) {
            return new IconResult(false, null, "Error fetching icon: " + e.getMessage());
        }
    }
    
    /**
     * Generate icon based on text (fallback when no icon found)
     */
    public IconResult generateTextIcon(String text, IconStyle style) {
        try {
            String initials = getInitials(text);
            String fileName = "generated_" + text.hashCode() + ".png";
            String filePath = Paths.get(cacheDir, fileName).toString();
            
            // Create image
            int size = style.getSize();
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            
            // Enable antialiasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Draw background
            g2d.setColor(style.getBackgroundColor());
            if (style.isCircular()) {
                g2d.fill(new Ellipse2D.Float(0, 0, size, size));
            } else {
                g2d.fillRoundRect(0, 0, size, size, size/8, size/8);
            }
            
            // Draw text
            g2d.setColor(style.getForegroundColor());
            Font font = new Font("Arial", Font.BOLD, size / 3);
            g2d.setFont(font);
            
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(initials);
            int textHeight = fm.getHeight();
            int x = (size - textWidth) / 2;
            int y = (size - textHeight) / 2 + fm.getAscent();
            
            g2d.drawString(initials, x, y);
            g2d.dispose();
            
            // Save image
            ImageIO.write(image, "PNG", new File(filePath));
            
            return new IconResult(true, filePath, "Generated text icon");
            
        } catch (Exception e) {
            return new IconResult(false, null, "Error generating icon: " + e.getMessage());
        }
    }
    
    /**
     * Create compound icon (icon + background shape)
     */
    public IconResult createCompoundIcon(String iconPath, IconStyle style) {
        try {
            BufferedImage icon = ImageIO.read(new File(iconPath));
            int size = style.getSize();
            
            BufferedImage compound = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = compound.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw background
            g2d.setColor(style.getBackgroundColor());
            if (style.isCircular()) {
                g2d.fill(new Ellipse2D.Float(0, 0, size, size));
            } else {
                g2d.fillRoundRect(0, 0, size, size, size/8, size/8);
            }
            
            // Scale and center icon
            int iconSize = (int)(size * 0.6);
            int offset = (size - iconSize) / 2;
            g2d.drawImage(icon, offset, offset, iconSize, iconSize, null);
            g2d.dispose();
            
            String outputPath = iconPath.replace(".png", "_compound.png");
            ImageIO.write(compound, "PNG", new File(outputPath));
            
            return new IconResult(true, outputPath, "Compound icon created");
            
        } catch (Exception e) {
            return new IconResult(false, null, "Error creating compound icon: " + e.getMessage());
        }
    }
    
    private String findBestIconUrl(String keyword) {
        // Direct match
        if (ICON_MAPPINGS.containsKey(keyword)) {
            return ICON_MAPPINGS.get(keyword);
        }
        
        // Partial match
        for (Map.Entry<String, String> entry : ICON_MAPPINGS.entrySet()) {
            if (keyword.contains(entry.getKey()) || entry.getKey().contains(keyword)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    private String downloadAndProcessIcon(String iconUrl, String keyword, IconStyle style) 
            throws IOException {
        String fileName = keyword + "_" + style.getSize() + ".png";
        String filePath = Paths.get(cacheDir, fileName).toString();
        
        // Download icon
        try (InputStream in = new URL(iconUrl).openStream()) {
            Files.copy(in, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Process icon (resize, recolor if needed)
        if (style.needsProcessing()) {
            processIcon(filePath, style);
        }
        
        return filePath;
    }
    
    private void processIcon(String filePath, IconStyle style) throws IOException {
        BufferedImage original = ImageIO.read(new File(filePath));
        int targetSize = style.getSize();
        
        // Create processed image
        BufferedImage processed = new BufferedImage(targetSize, targetSize, 
                                                   BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = processed.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Scale image
        g2d.drawImage(original, 0, 0, targetSize, targetSize, null);
        
        // Apply color filter if needed
        if (style.getColorFilter() != null) {
            applyColorFilter(processed, style.getColorFilter());
        }
        
        g2d.dispose();
        
        // Save processed image
        ImageIO.write(processed, "PNG", new File(filePath));
    }
    
    private void applyColorFilter(BufferedImage image, Color filterColor) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xff;
                if (alpha > 0) {
                    // Preserve alpha, apply color
                    int newRgb = (alpha << 24) | 
                                (filterColor.getRed() << 16) | 
                                (filterColor.getGreen() << 8) | 
                                filterColor.getBlue();
                    image.setRGB(x, y, newRgb);
                }
            }
        }
    }
    
    private String getCachedIcon(String keyword, IconStyle style) {
        String fileName = keyword + "_" + style.getSize() + ".png";
        Path path = Paths.get(cacheDir, fileName);
        return Files.exists(path) ? path.toString() : null;
    }
    
    private String getInitials(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1))
                    .toUpperCase();
        } else if (words.length == 1 && words[0].length() > 0) {
            return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
        }
        return "?";
    }
}

class IconResult {
    private final boolean success;
    private final String filePath;
    private final String message;
    
    public IconResult(boolean success, String filePath, String message) {
        this.success = success;
        this.filePath = filePath;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public String getFilePath() { return filePath; }
    public String getMessage() { return message; }
}

class IconStyle {
    private final int size;
    private final Color backgroundColor;
    private final Color foregroundColor;
    private final Color colorFilter;
    private final boolean circular;
    
    public IconStyle(int size, Color backgroundColor, Color foregroundColor, 
                     Color colorFilter, boolean circular) {
        this.size = size;
        this.backgroundColor = backgroundColor;
        this.foregroundColor = foregroundColor;
        this.colorFilter = colorFilter;
        this.circular = circular;
    }
    
    // Factory methods for common styles
    public static IconStyle modern(int size) {
        return new IconStyle(size, 
            new Color(66, 133, 244), // Google blue
            Color.WHITE, 
            null, 
            true);
    }
    
    public static IconStyle professional(int size) {
        return new IconStyle(size, 
            new Color(44, 62, 80), // Dark blue-gray
            Color.WHITE, 
            null, 
            false);
    }
    
    public static IconStyle minimal(int size) {
        return new IconStyle(size, 
            new Color(245, 245, 245), // Light gray
            new Color(51, 51, 51), // Dark gray
            null, 
            true);
    }
    
    // Getters
    public int getSize() { return size; }
    public Color getBackgroundColor() { return backgroundColor; }
    public Color getForegroundColor() { return foregroundColor; }
    public Color getColorFilter() { return colorFilter; }
    public boolean isCircular() { return circular; }
    
    public boolean needsProcessing() {
        return colorFilter != null;
    }
}