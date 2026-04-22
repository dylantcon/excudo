package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.smartcontent.IconRepository;
import com.excudo.core.smartcontent.IconRepository.IconAsset;
import com.excudo.core.smartcontent.IconRepository.IconSearchResult;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

/**
 * Console command for icon management: search, upload, list, remove.
 * Interactive prompts (e.g. attribution on upload) go through the DisplayAdapter.
 */
public class IconCommand implements Command {

    private static final ComponentLogger logger = Logger.getLogger(IconCommand.class);

    private final IconRepository repository;
    private final String subcommand;
    private final String[] args;
    private final DisplayAdapter displayAdapter;
    private boolean executed = false;

    public interface DisplayAdapter {
        void displayMessage(String message);
        void displayError(String message);
        String promptUser(String prompt);
    }

    public IconCommand(IconRepository repository, String subcommand, String[] args,
                       DisplayAdapter displayAdapter) {
        this.repository = repository;
        this.subcommand = subcommand != null ? subcommand.toLowerCase() : "help";
        this.args = args != null ? args : new String[0];
        this.displayAdapter = displayAdapter;
    }

    @Override
    public void execute() {
        executed = true;
        try {
            switch (subcommand) {
                case "search" -> handleSearch();
                case "upload" -> handleUpload();
                case "list"   -> handleList();
                case "sources" -> handleSources();
                case "help"   -> handleHelp();
                default -> {
                    displayAdapter.displayError("Unknown icon subcommand: " + subcommand);
                    handleHelp();
                }
            }
        } catch (Exception e) {
            displayAdapter.displayError("icon " + subcommand + " failed: " + e.getMessage());
        }
    }

    @Override public void undo() { /* Icon commands are not undoable */ }
    @Override public boolean canUndo() { return false; }
    @Override public String getDescription() { return "icon " + subcommand; }
    @Override public boolean isExecuted() { return executed; }

    private void handleSearch() {
        if (args.length == 0) {
            displayAdapter.displayError("Usage: icon search <query> [maxResults]");
            return;
        }
        String query = args[0];
        int maxResults = args.length > 1 ? parseIntOrDefault(args[1], 5) : 5;

        ExecutionResult<IconSearchResult> result = repository.searchIcons(query, maxResults);
        if (!result.isSuccess()) {
            displayAdapter.displayError("Search failed: " + result.getMessage());
            return;
        }

        IconSearchResult searchResult = result.getData().get();
        List<IconAsset> icons = searchResult.getIcons();

        if (icons.isEmpty()) {
            displayAdapter.displayMessage("No icons found for '" + query + "'.");
            return;
        }

        displayAdapter.displayMessage("Found " + icons.size() + " icon(s) for '" + query
            + "' (" + searchResult.getSearchTimeMs() + "ms):\n");

        for (int i = 0; i < icons.size(); i++) {
            IconAsset icon = icons.get(i);
            String line = String.format("  %d. %-30s [%s]  score=%.2f",
                i + 1, icon.getName(), icon.getSource(), icon.getRelevanceScore());
            displayAdapter.displayMessage(line);
            if (icon.getAttribution() != null && !icon.getAttribution().isEmpty()) {
                displayAdapter.displayMessage("     " + icon.getAttribution());
            }
        }
        displayAdapter.displayMessage("\nUse 'inject <slide> \"" + query + "\"' to place the top result on a slide.");
    }

    private void handleUpload() {
        if (args.length == 0) {
            displayAdapter.displayError("Usage: icon upload <filepath> [name]");
            return;
        }
        String filePath = args[0];
        File file = new File(filePath);

        if (!file.exists()) {
            displayAdapter.displayError("File not found: " + filePath);
            return;
        }

        // Validate file type
        String lower = filePath.toLowerCase();
        if (!lower.endsWith(".svg") && !lower.endsWith(".png")
                && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
            displayAdapter.displayError("Unsupported file type. Accepted: SVG, PNG, JPG");
            return;
        }

        // Derive or accept icon name
        String iconName;
        if (args.length > 1) {
            iconName = args[1];
        } else {
            iconName = file.getName().replaceFirst("\\.[^.]+$", "");
            displayAdapter.displayMessage("Using icon name: " + iconName);
        }

        // Prompt for attribution (liability protection)
        displayAdapter.displayMessage("\n-- Attribution Information --");
        displayAdapter.displayMessage("Provide attribution for this icon. If you own it or it's public domain,");
        displayAdapter.displayMessage("state that clearly. Leaving this blank means you accept responsibility");
        displayAdapter.displayMessage("for any intellectual property claims.\n");

        String author = displayAdapter.promptUser("Author/artist name (or press Enter to skip): ");
        String license = displayAdapter.promptUser("License (e.g. MIT, CC-BY-4.0, Public Domain, or Enter to skip): ");
        String sourceUrl = displayAdapter.promptUser("Source URL (or press Enter to skip): ");

        // Build attribution string
        String attribution = buildAttribution(iconName, author, license, sourceUrl);
        displayAdapter.displayMessage("\nAttribution: " + attribution);

        // Collect tags
        String tagInput = displayAdapter.promptUser("Tags (comma-separated, e.g. 'business,chart,graph'): ");
        Set<String> tags = new HashSet<>();
        tags.add(iconName.toLowerCase());
        if (tagInput != null && !tagInput.trim().isEmpty()) {
            for (String tag : tagInput.split(",")) {
                String trimmed = tag.trim().toLowerCase();
                if (!trimmed.isEmpty()) tags.add(trimmed);
            }
        }

        ExecutionResult<IconAsset> result = repository.uploadLocalIcon(filePath, iconName, tags);
        if (result.isSuccess()) {
            displayAdapter.displayMessage("[OK] Uploaded '" + iconName + "' to local icon library.");
            displayAdapter.displayMessage("Search for it with: icon search " + iconName);
        } else {
            displayAdapter.displayError("Upload failed: " + result.getMessage());
        }
    }

    private String buildAttribution(String iconName, String author, String license, String sourceUrl) {
        StringBuilder sb = new StringBuilder();

        boolean hasAuthor = author != null && !author.trim().isEmpty();
        boolean hasLicense = license != null && !license.trim().isEmpty();
        boolean hasUrl = sourceUrl != null && !sourceUrl.trim().isEmpty();

        if (!hasAuthor && !hasLicense && !hasUrl) {
            // No attribution provided -- make the liability explicit
            return "User-provided icon '" + iconName + "' -- NO ATTRIBUTION PROVIDED. "
                + "Uploaded " + LocalDate.now() + ". User accepts IP responsibility.";
        }

        sb.append("Icon '").append(iconName).append("'");
        if (hasAuthor) sb.append(" by ").append(author.trim());
        if (hasUrl) sb.append(" (").append(sourceUrl.trim()).append(")");
        if (hasLicense) sb.append(" - ").append(license.trim());
        return sb.toString();
    }

    private void handleList() {
        List<IconAsset> allIcons = repository.getAllAvailableIcons();

        // Group by source
        Map<String, List<IconAsset>> bySource = new LinkedHashMap<>();
        for (IconAsset icon : allIcons) {
            bySource.computeIfAbsent(icon.getSource(), k -> new ArrayList<>()).add(icon);
        }

        displayAdapter.displayMessage("Icon library: " + allIcons.size() + " icon(s) total\n");

        for (Map.Entry<String, List<IconAsset>> entry : bySource.entrySet()) {
            String source = entry.getKey();
            List<IconAsset> icons = entry.getValue();
            String label = switch (source) {
                case "devicon" -> "Devicon (programming logos)";
                case "local"   -> "Local uploads";
                case "iconify" -> "Iconify (cached)";
                case "freepik" -> "Freepik (cached)";
                default -> source;
            };
            displayAdapter.displayMessage("  " + label + ": " + icons.size());

            // For local uploads, show each one (there won't be many)
            if ("local".equals(source)) {
                for (IconAsset icon : icons) {
                    displayAdapter.displayMessage("    - " + icon.getName()
                        + " [" + String.join(", ", icon.getTags()) + "]");
                    if (icon.getAttribution() != null) {
                        displayAdapter.displayMessage("      " + icon.getAttribution());
                    }
                }
            }
        }
    }

    private void handleSources() {
        displayAdapter.displayMessage("Icon sources (searched in order):\n");
        displayAdapter.displayMessage("  1. Local uploads     -- user-provided icons (icon upload)");
        displayAdapter.displayMessage("  2. Devicon           -- " + repository.getDeviconIconCount()
            + " programming/tech logos (built-in)");
        displayAdapter.displayMessage("  3. Iconify           -- 200k+ open-source icons (free, no key)");
        if (repository.isFreepikAPIEnabled() && repository.hasFreepikApiKey()) {
            displayAdapter.displayMessage("  4. Freepik           -- premium icons (API key configured)");
        } else {
            displayAdapter.displayMessage("  4. Freepik           -- premium icons (no API key -- disabled)");
        }
    }

    private void handleHelp() {
        displayAdapter.displayMessage("""
            Icon Commands:

            icon search <query> [max]  - Search for icons across all sources
            icon upload <path> [name]  - Upload a local icon (SVG, PNG, JPG)
            icon list                  - List all available icons by source
            icon sources               - Show configured icon sources and status

            Examples:
              icon search database
              icon search "cloud computing" 10
              icon upload ~/icons/logo.svg company-logo
              icon list
            """);
    }

    private int parseIntOrDefault(String s, int defaultValue) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
    }
}
