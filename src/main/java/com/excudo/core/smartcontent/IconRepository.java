package com.excudo.core.smartcontent;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.EnvLoader;
import com.excudo.core.utils.Logger;
import com.google.gson.*;
import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.file.*;
import java.util.stream.Collectors;

/**
 * Icon repository that provides icons from multiple sources:
 * 1. Local Devicon repository (programming-specific icons)
 * 2. Freepik API (licensed icon search and download)
 * 3. Local upload directory for custom icons
 *
 * Designed for immediate usability in curriculum development workflow.
 */
public class IconRepository {
    
    public static class IconAsset {
        private final String name;
        private final String source;
        private final String path;
        private final Set<String> tags;
        private final String attribution;
        private final double relevanceScore;
        private final String resourceId;        // For downloading
        private final String authorName;        // Artist name
        private final String iconDescription;   // Icon description
        private final String sourceUrl;         // Link to source/artist
        
        public IconAsset(String name, String source, String path, Set<String> tags, 
                        String attribution, double relevanceScore) {
            this(name, source, path, tags, attribution, relevanceScore, null, null, null, null);
        }
        
        public IconAsset(String name, String source, String path, Set<String> tags, 
                        String attribution, double relevanceScore, String resourceId,
                        String authorName, String iconDescription, String sourceUrl) {
            this.name = name;
            this.source = source;
            this.path = path;
            this.tags = tags;
            this.attribution = attribution;
            this.relevanceScore = relevanceScore;
            this.resourceId = resourceId;
            this.authorName = authorName;
            this.iconDescription = iconDescription;
            this.sourceUrl = sourceUrl;
        }
        
        public String getName() { return name; }
        public String getSource() { return source; }
        public String getPath() { return path; }
        public Set<String> getTags() { return tags; }
        public String getAttribution() { return attribution; }
        public double getRelevanceScore() { return relevanceScore; }
        public String getResourceId() { return resourceId; }
        public String getAuthorName() { return authorName; }
        public String getIconDescription() { return iconDescription; }
        public String getSourceUrl() { return sourceUrl; }
    }
    
    public static class IconSearchResult {
        private final List<IconAsset> icons;
        private final String searchQuery;
        private final long searchTimeMs;
        
        public IconSearchResult(List<IconAsset> icons, String searchQuery, long searchTimeMs) {
            this.icons = icons;
            this.searchQuery = searchQuery;
            this.searchTimeMs = searchTimeMs;
        }
        
        public List<IconAsset> getIcons() { return icons; }
        public String getSearchQuery() { return searchQuery; }
        public long getSearchTimeMs() { return searchTimeMs; }
    }
    
    private static final ComponentLogger logger = Logger.getLogger(IconRepository.class);

    private final String cacheDirectory;
    private final Map<String, IconAsset> deviconIndex;
    private final Map<String, IconAsset> localUploadedIcons;
    private final boolean enableFreepikAPI;
    private final boolean networkEnabled;
    private String freepikApiKey;
    private final HttpClient httpClient;
    
    // Freepik API constants
    private static final String FREEPIK_API_BASE = "https://api.freepik.com/v1";
    private static final String FREEPIK_SEARCH_ENDPOINT = "/icons";
    private static final String FREEPIK_DOWNLOAD_ENDPOINT = "/icons/{id}/download";
    private static final String FREEPIK_ICON_DETAIL_ENDPOINT = "/icons/{id}";

    // Iconify API constants (free, no auth, 200k+ icons from 100+ open-source sets)
    private static final String ICONIFY_API_BASE = "https://api.iconify.design";
    private static final String ICONIFY_SEARCH_ENDPOINT = "/search";
    
    // Complete Devicon programming icons - ultimate CS education set (180+ icons)
    private static final List<DeviconEntry> DEVICON_ICONS = Arrays.asList(
        // Creative & Design Tools
        new DeviconEntry("aftereffects", Set.of("aftereffects", "video", "motion", "animation", "adobe", "multimedia"), "Adobe After Effects"),
        new DeviconEntry("illustrator", Set.of("illustrator", "vector", "adobe", "ai", "svg"), "Adobe Illustrator"),
        new DeviconEntry("premierepro", Set.of("premierepro", "video", "editing", "adobe", "film", "multimedia"), "Adobe Premiere Pro"),
        new DeviconEntry("behance", Set.of("behance", "portfolio", "adobe", "showcase"), "Behance portfolio"),
        new DeviconEntry("inkscape", Set.of("inkscape", "vector", "svg"), "Inkscape vector graphics"),
        new DeviconEntry("maya", Set.of("maya", "3d", "modeling", "animation", "autodesk", "cgi"), "Autodesk Maya"),

        // Programming Languages
        new DeviconEntry("java", Set.of("java", "coffee", "jvm"), "Java programming language"),
        new DeviconEntry("python", Set.of("python", "snake", "science", "ai", "ml", "django"), "Python programming language"),
        new DeviconEntry("javascript", Set.of("javascript", "js", "node"), "JavaScript programming language"),
        new DeviconEntry("typescript", Set.of("typescript", "ts", "javascript", "typed", "microsoft", "angular"), "TypeScript programming language"),
        new DeviconEntry("csharp", Set.of("csharp", "c#", "dotnet", "microsoft", "windows"), "C# programming language"),
        new DeviconEntry("go", Set.of("go", "golang", "google", "concurrent"), "Go programming language"),
        new DeviconEntry("swift", Set.of("swift", "ios", "apple", "xcode", "macos"), "Swift programming language"),
        new DeviconEntry("ruby", Set.of("ruby", "rails", "gem"), "Ruby programming language"),
        new DeviconEntry("clojure", Set.of("clojure", "functional", "lisp", "jvm", "immutable"), "Clojure programming language"),
        new DeviconEntry("clojurescript", Set.of("clojurescript", "functional", "javascript", "react"), "ClojureScript language"),
        new DeviconEntry("coffeescript", Set.of("coffeescript", "javascript", "transpile", "syntax"), "CoffeeScript language"),
        new DeviconEntry("haskell", Set.of("haskell", "functional", "pure", "lazy", "academic"), "Haskell programming language"),
        new DeviconEntry("erlang", Set.of("erlang", "functional", "concurrent", "fault", "tolerant", "telecom"), "Erlang programming language"),
        new DeviconEntry("elm", Set.of("elm", "functional", "reactive", "stateless"), "Elm programming language"),
        new DeviconEntry("haxe", Set.of("haxe", "compile", "target"), "Haxe programming language"),
        new DeviconEntry("objectivec", Set.of("objectivec", "objc", "ios", "macos", "apple", "cocoa"), "Objective-C programming language"),
        new DeviconEntry("ceylon", Set.of("ceylon", "jvm", "modular", "redhat"), "Ceylon programming language"),
        new DeviconEntry("embeddedc", Set.of("embeddedc", "microcontroller", "iot", "hardware", "firmware"), "Embedded C programming"),

        // Web Technologies & Frameworks
        new DeviconEntry("html5", Set.of("html", "markup", "semantic"), "HTML5 markup language"),
        new DeviconEntry("css3", Set.of("css", "styling", "responsive"), "CSS3 styling language"),
        new DeviconEntry("react", Set.of("react", "javascript", "ui", "component", "jsx", "facebook"), "React JavaScript library"),
        new DeviconEntry("angularjs", Set.of("angular", "typescript", "spa", "google", "mvc"), "Angular framework"),
        new DeviconEntry("vuejs", Set.of("vue", "vuejs", "javascript", "progressive", "component"), "Vue.js framework"),
        new DeviconEntry("gatsby", Set.of("gatsby", "react", "ssr", "static", "jamstack", "graphql"), "Gatsby React framework"),
        new DeviconEntry("ember", Set.of("ember", "javascript", "mvc", "convention"), "Ember.js framework"),
        new DeviconEntry("backbonejs", Set.of("backbone", "javascript", "mvc", "underscore"), "Backbone.js framework"),
        new DeviconEntry("d3js", Set.of("d3", "visualization", "svg", "charts", "interactive"), "D3.js data visualization"),
        new DeviconEntry("jquery", Set.of("jquery", "javascript", "dom", "manipulation", "ajax"), "jQuery JavaScript library"),
        new DeviconEntry("json", Set.of("json", "format", "javascript", "object", "notation", "api"), "JSON data format"),
        new DeviconEntry("redux", Set.of("redux", "javascript", "state", "react", "predictable", "store"), "Redux state management"),
        new DeviconEntry("alpinejs", Set.of("alpinejs", "javascript", "reactive", "declarative"), "Alpine.js framework"),
        new DeviconEntry("chakraui", Set.of("chakraui", "ui", "react", "components"), "Chakra UI React components"),
        new DeviconEntry("materialui", Set.of("materialui", "ui", "react", "components", "google"), "Material-UI React components"),
        new DeviconEntry("vuestorefront", Set.of("vuestorefront", "vue", "storefront", "ecommerce", "pwa", "headless"), "Vue Storefront e-commerce"),

        // Backend & Runtime
        new DeviconEntry("nodejs", Set.of("nodejs", "javascript", "runtime", "npm", "v8"), "Node.js JavaScript runtime"),
        new DeviconEntry("express", Set.of("express", "nodejs", "api"), "Express.js framework"),
        new DeviconEntry("rails", Set.of("rails", "ruby", "mvc", "convention", "gem"), "Ruby on Rails framework"),
        new DeviconEntry("fastify", Set.of("fastify", "nodejs"), "Fastify Node.js framework"),
        new DeviconEntry("meteor", Set.of("meteor", "javascript", "realtime", "mongodb"), "Meteor.js platform"),
        new DeviconEntry("krakenjs", Set.of("krakenjs", "nodejs", "paypal", "express"), "Kraken.js Node.js framework"),
        new DeviconEntry("electron", Set.of("electron", "javascript", "chromium"), "Electron desktop framework"),
        new DeviconEntry("nodewebkit", Set.of("nodewebkit", "webkit", "javascript", "native"), "Node-webkit desktop"),

        // Databases & Storage
        new DeviconEntry("mysql", Set.of("mysql", "sql", "relational", "oracle"), "MySQL database"),
        new DeviconEntry("postgresql", Set.of("postgresql", "sql", "postgres", "relational"), "PostgreSQL database"),
        new DeviconEntry("mongodb", Set.of("mongodb", "nosql", "atlas", "json"), "MongoDB database"),
        new DeviconEntry("redis", Set.of("redis", "key", "value"), "Redis cache database"),
        new DeviconEntry("couchdb", Set.of("couchdb", "nosql", "apache", "json"), "CouchDB document database"),
        new DeviconEntry("oracle", Set.of("oracle", "sql", "rdbms"), "Oracle Database"),

        // Development Tools & IDEs
        new DeviconEntry("git", Set.of("git", "version", "control", "vcs", "distributed"), "Git version control"),
        new DeviconEntry("github", Set.of("github", "git", "repository", "microsoft"), "GitHub platform"),
        new DeviconEntry("gitlab", Set.of("gitlab", "git", "repository", "ci"), "GitLab platform"),
        new DeviconEntry("bitbucket", Set.of("bitbucket", "git", "repository", "atlassian", "pipelines"), "Bitbucket repository"),
        new DeviconEntry("sourcetree", Set.of("sourcetree", "git", "gui", "atlassian", "visual", "client"), "SourceTree Git client"),
        new DeviconEntry("androidstudio", Set.of("androidstudio", "android", "ide", "google", "kotlin", "java"), "Android Studio IDE"),
        new DeviconEntry("xcode", Set.of("xcode", "ios", "apple", "ide", "swift"), "Xcode IDE"),
        new DeviconEntry("vim", Set.of("vim", "editor", "terminal", "modal", "unix"), "Vim text editor"),
        new DeviconEntry("atom", Set.of("atom", "editor", "github", "hackable", "packages"), "Atom text editor"),

        // DevOps & Cloud Platforms
        new DeviconEntry("docker", Set.of("docker", "isolation"), "Docker containerization"),
        new DeviconEntry("ansible", Set.of("ansible", "configuration", "redhat", "playbook"), "Ansible automation"),
        new DeviconEntry("nginx", Set.of("nginx"), "Nginx web server"),
        new DeviconEntry("heroku", Set.of("heroku", "salesforce"), "Heroku cloud platform"),
        new DeviconEntry("vagrant", Set.of("vagrant", "provisioning"), "Vagrant virtualization"),
        new DeviconEntry("circleci", Set.of("circleci", "ci", "continuous"), "CircleCI platform"),
        new DeviconEntry("cloudflare", Set.of("cloudflare", "cdn", "dns", "security"), "Cloudflare CDN"),
        new DeviconEntry("cloudflareworkers", Set.of("cloudflareworkers", "cloudflare", "workers", "computing"), "Cloudflare Workers"),
        new DeviconEntry("codecov", Set.of("codecov", "coverage", "analysis", "ci"), "Codecov test coverage"),
        new DeviconEntry("tomcat", Set.of("tomcat", "apache", "servlet", "java"), "Apache Tomcat"),
        new DeviconEntry("uwsgi", Set.of("uwsgi", "python", "gateway", "interface"), "uWSGI Python server"),

        // Operating Systems
        new DeviconEntry("linux", Set.of("linux", "terminal", "unix"), "Linux operating system"),
        new DeviconEntry("ubuntu", Set.of("ubuntu", "linux", "debian", "canonical"), "Ubuntu Linux distribution"),
        new DeviconEntry("debian", Set.of("debian", "linux", "stable"), "Debian Linux distribution"),
        new DeviconEntry("redhat", Set.of("redhat", "linux", "rhel", "centos", "fedora"), "Red Hat Linux"),
        new DeviconEntry("windows8", Set.of("windows", "microsoft"), "Windows operating system"),
        new DeviconEntry("apple", Set.of("apple", "macos", "unix"), "Apple/macOS system"),
        new DeviconEntry("unix", Set.of("unix", "terminal", "posix", "commands"), "Unix operating system"),

        // Build Tools & Package Managers
        new DeviconEntry("webpack", Set.of("webpack", "bundler", "javascript", "module", "asset"), "Webpack module bundler"),
        new DeviconEntry("gulp", Set.of("gulp", "javascript", "streaming"), "Gulp build automation"),
        new DeviconEntry("grunt", Set.of("grunt", "javascript"), "Grunt task runner"),
        new DeviconEntry("bower", Set.of("bower", "package", "manager", "dependencies"), "Bower package manager"),
        new DeviconEntry("composer", Set.of("composer", "php", "package", "manager", "dependencies", "autoload"), "Composer PHP package manager"),
        new DeviconEntry("rollup", Set.of("rollup", "bundler", "javascript", "es6", "modules", "tree", "shaking"), "Rollup.js bundler"),
        new DeviconEntry("babel", Set.of("babel", "javascript", "transpiler", "es6", "compiler", "transform"), "Babel JavaScript compiler"),

        // Testing & Quality Assurance
        new DeviconEntry("jest", Set.of("jest", "javascript", "facebook", "unit", "snapshot"), "Jest testing framework"),
        new DeviconEntry("mocha", Set.of("mocha", "javascript", "nodejs", "bdd", "tdd"), "Mocha testing framework"),
        new DeviconEntry("karma", Set.of("karma", "javascript", "angular", "browser"), "Karma test runner"),
        new DeviconEntry("selenium", Set.of("selenium", "browser", "e2e"), "Selenium web testing"),

        // CSS Preprocessors & Frameworks
        new DeviconEntry("sass", Set.of("sass", "scss", "css", "preprocessor", "styling", "variables"), "Sass CSS preprocessor"),
        new DeviconEntry("stylus", Set.of("stylus", "css", "preprocessor", "expressive", "dynamic"), "Stylus CSS preprocessor"),
        new DeviconEntry("foundation", Set.of("foundation", "css", "responsive", "zurb"), "Foundation CSS framework"),
        new DeviconEntry("jeet", Set.of("jeet", "css", "grid", "stylus", "sass"), "Jeet grid system"),

        // Browsers & Web Platforms
        new DeviconEntry("chrome", Set.of("chrome", "browser", "google", "v8", "developer"), "Google Chrome browser"),
        new DeviconEntry("firefox", Set.of("firefox", "browser", "mozilla", "gecko"), "Mozilla Firefox browser"),
        new DeviconEntry("safari", Set.of("safari", "browser", "apple", "webkit", "macos", "ios"), "Safari browser"),
        new DeviconEntry("ie10", Set.of("ie10", "internet", "explorer", "browser", "microsoft", "legacy"), "Internet Explorer browser"),

        // Social & Collaboration Platforms
        new DeviconEntry("facebook", Set.of("facebook", "social", "react", "graph", "api"), "Facebook platform"),
        new DeviconEntry("google", Set.of("google", "search", "android", "apis", "services"), "Google platform"),
        new DeviconEntry("linkedin", Set.of("linkedin", "professional", "social", "network", "career"), "LinkedIn platform"),
        new DeviconEntry("ifttt", Set.of("ifttt", "webhooks", "services"), "IFTTT automation"),

        // Mobile Development
        new DeviconEntry("android", Set.of("android", "google", "java", "kotlin"), "Android mobile OS"),
        new DeviconEntry("ionic", Set.of("ionic", "hybrid", "angular", "cordova"), "Ionic mobile framework"),
        new DeviconEntry("flutter", Set.of("flutter", "dart", "google", "ui"), "Flutter mobile framework"),
        new DeviconEntry("xamarin", Set.of("xamarin", "microsoft", "c#"), "Xamarin mobile platform"),
        new DeviconEntry("appcelerator", Set.of("appcelerator", "titanium", "javascript"), "Appcelerator Titanium"),

        // PHP & Web Technologies
        new DeviconEntry("cakephp", Set.of("cakephp", "php", "mvc", "rapid"), "CakePHP framework"),
        new DeviconEntry("codeigniter", Set.of("codeigniter", "php", "mvc"), "CodeIgniter PHP framework"),
        new DeviconEntry("symfony", Set.of("symfony", "php", "components"), "Symfony PHP framework"),
        new DeviconEntry("drupal", Set.of("drupal", "cms", "php", "modular"), "Drupal CMS"),
        new DeviconEntry("wordpress", Set.of("wordpress", "cms", "php", "blog", "website", "plugin"), "WordPress CMS"),
        new DeviconEntry("woocommerce", Set.of("woocommerce", "ecommerce", "wordpress", "plugin", "shop"), "WooCommerce e-commerce"),
        new DeviconEntry("phalcon", Set.of("phalcon", "php", "c", "extension"), "Phalcon PHP framework"),
        new DeviconEntry("doctrine", Set.of("doctrine", "php", "orm", "mapping", "persistence"), "Doctrine PHP ORM"),
        new DeviconEntry("thymeleaf", Set.of("thymeleaf", "java", "template", "spring"), "Thymeleaf template engine"),
        new DeviconEntry("modx", Set.of("modx", "cms", "php", "flexible"), "MODX CMS"),
        new DeviconEntry("typo3", Set.of("typo3", "cms", "php"), "TYPO3 CMS"),
        new DeviconEntry("shopware", Set.of("shopware", "ecommerce", "php", "shop"), "Shopware e-commerce"),

        // Data Science & Analytics
        new DeviconEntry("matplotlib", Set.of("matplotlib", "python", "plotting", "visualization", "charts", "graphs"), "Matplotlib plotting library"),
        new DeviconEntry("pytorch", Set.of("pytorch", "machine", "learning", "deep", "neural", "networks", "facebook"), "PyTorch ML framework"),
        new DeviconEntry("minitab", Set.of("minitab", "statistics", "analysis", "six", "sigma"), "Minitab statistical software"),
        new DeviconEntry("spss", Set.of("spss", "statistics", "analysis", "ibm", "research"), "IBM SPSS statistics"),

        // Network & System Administration
        new DeviconEntry("ssh", Set.of("ssh", "secure", "shell", "remote", "access", "terminal", "protocol"), "SSH secure shell"),
        new DeviconEntry("ohmyzsh", Set.of("ohmyzsh", "zsh", "shell", "terminal", "customization", "productivity"), "Oh My Zsh shell"),
        new DeviconEntry("powershell", Set.of("powershell", "microsoft", "shell", "windows"), "PowerShell scripting"),

        // Specialized Tools & Platforms
        new DeviconEntry("labview", Set.of("labview", "national", "instruments", "graphical"), "LabVIEW graphical programming"),
        new DeviconEntry("latex", Set.of("latex", "typesetting", "academic", "publishing", "tex"), "LaTeX document system"),
        new DeviconEntry("hugo", Set.of("hugo", "static", "site", "generator", "go", "blog"), "Hugo static site generator"),
        new DeviconEntry("devicon", Set.of("devicon", "icons", "technology", "logos"), "Devicon icon library"),
        new DeviconEntry("ecto", Set.of("ecto", "elixir", "query", "migration"), "Ecto database library"),
        new DeviconEntry("sequelize", Set.of("sequelize", "nodejs", "orm", "sql", "promise"), "Sequelize Node.js ORM"),
        new DeviconEntry("spack", Set.of("spack", "package", "manager", "hpc", "scientific"), "Spack package manager"),
        new DeviconEntry("shotgrid", Set.of("shotgrid", "autodesk", "production", "pipeline", "vfx"), "ShotGrid production tracking"),
        new DeviconEntry("moodle", Set.of("moodle", "learning", "education", "elearning"), "Moodle LMS"),
        new DeviconEntry("salesforce", Set.of("salesforce", "crm", "customer", "relationship"), "Salesforce CRM platform"),
        new DeviconEntry("yunohost", Set.of("yunohost", "self", "debian", "administration"), "YunoHost self-hosting"),

        // .NET Ecosystem
        new DeviconEntry("dot-net", Set.of("dotnet", "net", "microsoft", "csharp", "runtime"), ".NET Framework")
    );
    
    private static class DeviconEntry {
        final String name;
        final Set<String> tags;
        final String description;
        
        DeviconEntry(String name, Set<String> tags, String description) {
            this.name = name;
            this.tags = tags;
            this.description = description;
        }
    }
    
    public IconRepository(String cacheDirectory) {
        this(cacheDirectory, true);
    }
    
    public IconRepository(String cacheDirectory, boolean enableFreepikAPI) {
        this.cacheDirectory = cacheDirectory;
        this.enableFreepikAPI = enableFreepikAPI;
        
        // Load Freepik API key from environment
        if (enableFreepikAPI) {
            this.freepikApiKey = EnvLoader.get("FREEPIK_API_KEY");
            if (this.freepikApiKey != null && !this.freepikApiKey.trim().isEmpty()) {
                logger.info("Freepik API key loaded from environment");
            } else {
                logger.info("Freepik API key not found in environment - API search disabled");
            }
        } else {
            this.freepikApiKey = null;
        }
        this.httpClient = HttpClient.newBuilder()
            .proxy(java.net.ProxySelector.getDefault()) // Use system default proxy settings
            .build();
        this.deviconIndex = buildDeviconIndex();
        this.localUploadedIcons = scanLocalUploads();
        
        // Ensure cache directory exists
        try {
            Files.createDirectories(Paths.get(cacheDirectory));
            Files.createDirectories(Paths.get(cacheDirectory, "devicon"));
            Files.createDirectories(Paths.get(cacheDirectory, "freepik"));
            Files.createDirectories(Paths.get(cacheDirectory, "iconify"));
            Files.createDirectories(Paths.get(cacheDirectory, "uploads"));
        } catch (Exception e) {
            logger.warn("Could not create icon cache directories: {}", e.getMessage());
        }
        
        // Pre-download all Devicon icons for better search accuracy and performance
        // Skip when created via localOnly() to avoid network I/O in tests/offline mode
        this.networkEnabled = enableFreepikAPI;
        if (this.networkEnabled) {
            preDownloadDeviconIcons();
        }
    }
    
    /**
     * Create an IconRepository with Freepik API key from environment
     */
    public static IconRepository fromEnvironment(String cacheDirectory) {
        return new IconRepository(cacheDirectory, true);
    }
    
    /**
     * Create an IconRepository without Freepik API (local only)
     */
    public static IconRepository localOnly(String cacheDirectory) {
        return new IconRepository(cacheDirectory, false);
    }
    
    /**
     * Pre-download all Devicon icons to ensure they're available locally
     * This improves search accuracy by eliminating cache misses that could 
     * cause inferior matches to be returned over better remote matches
     */
    private void preDownloadDeviconIcons() {
        logger.debug("Pre-downloading Devicon icons...");

        int totalIcons = DEVICON_ICONS.size();
        int downloaded = 0;
        int alreadyCached = 0;
        int failed = 0;

        for (int i = 0; i < totalIcons; i++) {
            DeviconEntry entry = DEVICON_ICONS.get(i);
            try {
                String iconPath = Paths.get(cacheDirectory, "devicon", entry.name + ".svg").toString();
                File iconFile = new File(iconPath);

                if (iconFile.exists() && iconFile.length() > 0) {
                    alreadyCached++;
                } else {
                    String[] urlPatterns = {
                        "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/" + entry.name + "/" + entry.name + "-original.svg",
                        "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/" + entry.name + "/" + entry.name + "-plain.svg",
                        "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/" + entry.name + "/" + entry.name + "-line.svg"
                    };

                    boolean success = false;
                    for (String urlPattern : urlPatterns) {
                        try {
                            URL url = new URL(urlPattern);
                            Path targetPath = Paths.get(iconPath);

                            try (InputStream in = url.openStream()) {
                                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                downloaded++;
                                success = true;
                                break;
                            }
                        } catch (IOException e) {
                            continue;
                        }
                    }

                    if (!success) {
                        failed++;
                        logger.warn("Failed to download Devicon icon '{}' - none of the URL patterns worked", entry.name);
                    }
                }

            } catch (Exception e) {
                failed++;
                logger.warn("Failed to download Devicon icon '{}': {}", entry.name, e.getMessage());
            }
        }

        logger.info("Devicon icons ready: {} downloaded, {} cached, {} failed (of {})",
            downloaded, alreadyCached, failed, totalIcons);

        if (failed > 0) {
            logger.warn("{} Devicon icons failed to download -- they will be fetched on-demand", failed);
        }
    }
    
    private Map<String, IconAsset> buildDeviconIndex() {
        Map<String, IconAsset> index = new HashMap<>();
        
        for (DeviconEntry entry : DEVICON_ICONS) {
            // Use placeholder path - will be downloaded when needed
            String iconPath = Paths.get(cacheDirectory, "devicon", entry.name + ".svg").toString();
            
            IconAsset asset = new IconAsset(
                entry.name,
                "devicon",
                iconPath,
                entry.tags,
                "Devicon by konpa (https://devicon.dev/) - MIT License",
                1.0 // Will be calculated during search
            );
            
            index.put(entry.name, asset);
        }
        
        return index;
    }
    
    private Map<String, IconAsset> scanLocalUploads() {
        Map<String, IconAsset> uploads = new HashMap<>();
        
        try {
            Path uploadsDir = Paths.get(cacheDirectory, "uploads");
            if (Files.exists(uploadsDir)) {
                Files.list(uploadsDir)
                    .filter(path -> path.toString().toLowerCase().matches(".*\\.(svg|png|jpg|jpeg)$"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String name = filename.replaceFirst("\\.[^.]+$", "");
                        IconAsset asset = new IconAsset(
                            name,
                            "local",
                            path.toString(),
                            Set.of(filename.toLowerCase()),
                            "Local upload",
                            1.0
                        );
                        uploads.put(filename, asset);
                    });
            }
        } catch (Exception e) {
            logger.warn("Could not scan local uploads: {}", e.getMessage());
        }
        
        return uploads;
    }
    
    public ExecutionResult<IconSearchResult> searchIcons(String query, int maxResults) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Handle empty queries
            if (query == null || query.trim().isEmpty()) {
                IconSearchResult result = new IconSearchResult(new ArrayList<>(), query,
                    System.currentTimeMillis() - startTime);
                return ExecutionResult.success("icon-search", result);
            }

            // Lemmatize the query so "databases" -> "database", "securing" -> "security", etc.
            query = QueryLemmatizer.lemmatize(query);
            logger.debug("Lemmatized query: '{}'", query);

            // Query all sources with a generous internal limit, then rank the
            // combined pool. Previous logic short-circuited Freepik when Devicon
            // filled the slots, so a tag-only match like "redis" for "database"
            // would shadow a proper conceptual icon from the API.
            int fetchLimit = Math.max(maxResults, 3);

            List<IconAsset> results = new ArrayList<>();

            // 1. Local uploads (user-provided, always highest trust)
            results.addAll(searchLocalUploads(query, fetchLimit));

            // 2. Devicon (tech brand logos -- good for exact tool names)
            results.addAll(searchDevicon(query, fetchLimit));

            // 3. Iconify API (free, no auth -- conceptual icons from open-source sets)
            if (networkEnabled) {
                results.addAll(searchIconifyAPI(query, fetchLimit));
            }

            // 4. Freepik API (premium conceptual icons -- requires paid API key)
            if (enableFreepikAPI && freepikApiKey != null) {
                results.addAll(searchFreepikAPI(query, fetchLimit));
            }

            // Rank combined pool by relevance, then trim
            results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
            if (results.size() > maxResults) {
                results = new ArrayList<>(results.subList(0, maxResults));
            }
            
            long searchTime = System.currentTimeMillis() - startTime;
            IconSearchResult result = new IconSearchResult(results, query, searchTime);
            
            return ExecutionResult.success("icon-search", result);
            
        } catch (Exception e) {
            return ExecutionResult.failure("icon-search", "Search failed: " + e.getMessage(), e);
        }
    }
    
    private List<IconAsset> searchDevicon(String query, int maxResults) {
        String lowerQuery = query.toLowerCase();
        List<IconAsset> matches = new ArrayList<>();

        for (IconAsset asset : deviconIndex.values()) {
            double score = calculateRelevanceScore(lowerQuery, asset);
            if (score > 0.25) {
                // Devicon is a brand logo library. Tag-only matches (no name match)
                // are likely a brand logo that happens to relate to the concept,
                // not a conceptual icon. Penalize tag-only matches so Freepik's
                // purpose-built icons win for generic queries like "database".
                boolean isNameMatch = asset.getName().toLowerCase().contains(lowerQuery)
                                   || lowerQuery.contains(asset.getName().toLowerCase());
                if (!isNameMatch && score < 0.9) {
                    score *= 0.4; // e.g. 0.8 -> 0.32 for tag-only
                }

                IconAsset scoredAsset = new IconAsset(
                    asset.getName(), asset.getSource(), asset.getPath(),
                    asset.getTags(), asset.getAttribution(), score
                );
                matches.add(scoredAsset);
            }
        }

        matches.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        return matches.stream().limit(maxResults).collect(Collectors.toList());
    }
    
    private List<IconAsset> searchLocalUploads(String query, int maxResults) {
        String lowerQuery = query.toLowerCase();
        List<IconAsset> matches = new ArrayList<>();
        
        for (Map.Entry<String, IconAsset> entry : localUploadedIcons.entrySet()) {
            String filename = entry.getKey();
            IconAsset asset = entry.getValue();
            
            // Check if filename or icon name contains the query
            if (filename.toLowerCase().contains(lowerQuery) || 
                asset.getName().toLowerCase().contains(lowerQuery)) {
                double score = filename.toLowerCase().equals(lowerQuery) || 
                              asset.getName().toLowerCase().equals(lowerQuery) ? 1.0 : 0.7;
                
                // Create a new asset with updated score
                IconAsset searchResult = new IconAsset(
                    asset.getName(),
                    asset.getSource(),
                    asset.getPath(),
                    asset.getTags(),
                    asset.getAttribution(),
                    score
                );
                matches.add(searchResult);
            }
        }
        
        return matches.stream().limit(maxResults).collect(Collectors.toList());
    }
    
    private List<IconAsset> searchFreepikAPI(String query, int maxResults) {
        if (freepikApiKey == null || freepikApiKey.trim().isEmpty()) {
            logger.debug("Freepik API key not provided -- skipping API search");
            return new ArrayList<>();
        }
        
        try {
            // Freepik Icons API uses 'term' parameter for search
            String searchUrl = FREEPIK_API_BASE + FREEPIK_SEARCH_ENDPOINT + 
                "?term=" + java.net.URLEncoder.encode(query, "UTF-8") + 
                "&per_page=" + Math.min(maxResults, 100) + // API likely has limits
                "&order=relevance";
            
            logger.debug("Freepik API search URL: {}", searchUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("x-freepik-api-key", freepikApiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "Excudo/1.0")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            logger.debug("Freepik API response status: {}", response.statusCode());
            
            if (response.statusCode() == 200) {
                return parseFreepikSearchResponse(response.body(), query);
            } else {
                logger.warn("Freepik API search failed with status: {} - {}", response.statusCode(), response.body());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Freepik API search error: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private List<IconAsset> parseFreepikSearchResponse(String jsonResponse, String query) {
        List<IconAsset> results = new ArrayList<>();
        
        try {
            logger.debug("Freepik API response: {}...", jsonResponse.substring(0, Math.min(500, jsonResponse.length())));
            
            // Parse using Gson
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (root.has("data")) {
                JsonArray dataArray = root.getAsJsonArray("data");
                if (dataArray != null) {
                    for (JsonElement el : dataArray) {
                        if (!el.isJsonObject()) continue;
                        IconAsset icon = parseFreepikIcon(el.getAsJsonObject(), query);
                        if (icon != null) {
                            results.add(icon);
                        }
                    }
                }
            }
            
            logger.debug("Parsed {} icons from Freepik API response", results.size());

        } catch (Exception e) {
            logger.error("Failed to parse Freepik API response: {}", e.getMessage());
        }
        
        return results;
    }
    
    private IconAsset parseFreepikIcon(JsonObject iconObj, String query) {
        try {
            String id = iconObj.has("id") ? iconObj.get("id").getAsString() : null;
            String name = iconObj.has("name") ? iconObj.get("name").getAsString() : null;

            if (id == null) {
                return null;
            }

            // Extract author information
            String authorName = "Unknown Artist";
            String sourceUrl = "https://www.freepik.com";

            if (iconObj.has("author") && iconObj.get("author").isJsonObject()) {
                JsonObject authorObj = iconObj.getAsJsonObject("author");
                if (authorObj.has("name")) {
                    authorName = authorObj.get("name").getAsString();
                }
                if (authorObj.has("slug")) {
                    String authorSlug = authorObj.get("slug").getAsString();
                    sourceUrl = "https://www.freepik.com/author/" + authorSlug;
                }
            }

            // Extract tags if available
            Set<String> tags = new HashSet<>();
            if (iconObj.has("tags")) {
                tags.add(query.toLowerCase());
                if (name != null) {
                    tags.add(name.toLowerCase());
                }
            }

            // Calculate relevance score based on name match
            double relevanceScore = calculateFreepikRelevance(query, name, tags);

            // Detect file format from thumbnails
            String fileExtension = ".svg"; // default fallback
            if (iconObj.has("thumbnails") && iconObj.get("thumbnails").isJsonArray()) {
                JsonArray thumbnails = iconObj.getAsJsonArray("thumbnails");
                if (thumbnails.size() > 0) {
                    JsonObject firstThumb = thumbnails.get(0).getAsJsonObject();
                    if (firstThumb.has("url")) {
                        String thumbnailUrl = firstThumb.get("url").getAsString();
                        if (thumbnailUrl.toLowerCase().contains(".png")) {
                            fileExtension = ".png";
                        } else if (thumbnailUrl.toLowerCase().contains(".svg")) {
                            fileExtension = ".svg";
                        } else if (thumbnailUrl.toLowerCase().contains(".jpg") || thumbnailUrl.toLowerCase().contains(".jpeg")) {
                            fileExtension = ".jpg";
                        }
                    }
                }
            }

            // Create icon asset with proper file extension
            String iconPath = Paths.get(cacheDirectory, "freepik", "icon-" + id + fileExtension).toString();
            String iconDescription = name != null ? name : ("Icon #" + id);

            // Create detailed attribution with actual artist name
            String attribution = String.format("Icon '%s' by %s (%s) - Freepik Premium License",
                iconDescription, authorName, sourceUrl);

            return new IconAsset(
                "freepik-" + id,
                "freepik",
                iconPath,
                tags,
                attribution,
                relevanceScore,
                id,                // resourceId for downloading
                authorName,        // actual artist name
                iconDescription,   // icon description
                sourceUrl          // link to artist profile
            );

        } catch (Exception e) {
            logger.debug("Failed to parse individual Freepik icon: {}", e.getMessage());
            return null;
        }
    }
    
    // ------------------------------------------------------------------
    // Iconify API (free, no auth required)
    // ------------------------------------------------------------------

    private List<IconAsset> searchIconifyAPI(String query, int maxResults) {
        try {
            String searchUrl = ICONIFY_API_BASE + ICONIFY_SEARCH_ENDPOINT
                + "?query=" + java.net.URLEncoder.encode(query, "UTF-8")
                + "&limit=" + Math.min(maxResults, 64);

            logger.debug("Iconify search URL: {}", searchUrl);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("Accept", "application/json")
                .header("User-Agent", "Excudo/1.0")
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                logger.debug("Iconify raw response (first 200 chars): {}",
                    body.substring(0, Math.min(200, body.length())));
                List<IconAsset> results = parseIconifySearchResponse(body, query);
                logger.debug("Iconify returned {} icons for '{}'", results.size(), query);
                return results;
            } else {
                logger.warn("Iconify API search failed with status: {}", response.statusCode());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.warn("Iconify API search error for '{}': {}", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse Iconify search response.
     * Format: {"icons":["mdi:database","mdi:database-outline",...],"total":42}
     * Each icon ID is "prefix:name" -- the SVG URL is /prefix/name.svg
     */
    private List<IconAsset> parseIconifySearchResponse(String jsonResponse, String query) {
        List<IconAsset> results = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!root.has("icons")) return results;

            JsonArray icons = root.getAsJsonArray("icons");
            String lowerQuery = query.toLowerCase();

            for (int i = 0; i < icons.size() && results.size() < 10; i++) {
                String iconId = icons.get(i).getAsString();
                int colonIdx = iconId.indexOf(':');
                if (colonIdx <= 0) continue;

                String prefix = iconId.substring(0, colonIdx);
                String name = iconId.substring(colonIdx + 1);

                // Compute relevance -- Iconify results are already relevance-ranked,
                // but we score name-match quality for inter-source ranking
                double score = 0.5;
                String lowerName = name.toLowerCase().replace('-', ' ');
                if (lowerName.equals(lowerQuery)) {
                    score = 0.95;
                } else if (lowerName.startsWith(lowerQuery)) {
                    score = 0.85;
                } else if (lowerName.contains(lowerQuery)) {
                    score = 0.75;
                }

                // Preferred icon sets (higher-quality, PowerPoint-style icons) get a boost
                if (prefix.equals("mdi") || prefix.equals("ph") || prefix.equals("tabler")
                        || prefix.equals("lucide") || prefix.equals("carbon")) {
                    score = Math.min(1.0, score + 0.05);
                }

                String iconPath = Paths.get(cacheDirectory, "iconify",
                    prefix + "_" + name + ".svg").toString();

                String humanName = name.replace('-', ' ').replace('_', ' ');

                Set<String> tags = new HashSet<>();
                tags.add(query.toLowerCase());
                tags.add(humanName);
                tags.add(prefix);

                results.add(new IconAsset(
                    "iconify-" + iconId,
                    "iconify",
                    iconPath,
                    tags,
                    "Icon '" + humanName + "' from " + prefix + " (Iconify) - Open Source",
                    score,
                    iconId,       // resourceId = "prefix:name"
                    prefix,       // authorName = icon set name
                    humanName,    // description
                    "https://icon-sets.iconify.design/" + prefix + "/" + name
                ));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse Iconify response: {}", e.getMessage());
        }
        return results;
    }

    /**
     * Download an Iconify icon as SVG.
     * URL: https://api.iconify.design/{prefix}/{name}.svg
     */
    private ExecutionResult<IconAsset> downloadIconifyIcon(IconAsset iconAsset) {
        try {
            String resourceId = iconAsset.getResourceId();
            if (resourceId == null) {
                return ExecutionResult.failure("icon-download", "No resource ID for Iconify icon");
            }
            int colonIdx = resourceId.indexOf(':');
            if (colonIdx <= 0) {
                return ExecutionResult.failure("icon-download", "Invalid Iconify icon ID: " + resourceId);
            }
            String prefix = resourceId.substring(0, colonIdx);
            String name = resourceId.substring(colonIdx + 1);

            String svgUrl = ICONIFY_API_BASE + "/" + prefix + "/" + name + ".svg";
            return downloadFileFromUrl(svgUrl, iconAsset);
        } catch (Exception e) {
            return ExecutionResult.failure("icon-download",
                "Failed to download Iconify icon: " + e.getMessage(), e);
        }
    }

    private double calculateFreepikRelevance(String query, String title, Set<String> tags) {
        // Freepik results are already relevance-ranked by the API, so a match here
        // is a purpose-built icon, not a brand logo that happens to share a tag.
        // Score accordingly: a Freepik icon titled "database" should beat a Redis
        // logo that merely has "database" as one of eight tags.
        double score = 0.5;

        if (title != null) {
            String lowerTitle = title.toLowerCase();
            String lowerQuery = query.toLowerCase();

            if (lowerTitle.equals(lowerQuery)) {
                score = 1.0;
            } else if (lowerTitle.startsWith(lowerQuery)) {
                score = 0.95;
            } else if (lowerTitle.contains(lowerQuery)) {
                score = 0.85;
            }
        }

        // Small tag bonus on top of title score
        for (String tag : tags) {
            if (tag.toLowerCase().contains(query.toLowerCase())) {
                score = Math.min(1.0, score + 0.05);
                break;
            }
        }

        return score;
    }
    
    private double calculateRelevanceScore(String query, IconAsset asset) {
        double score = 0.0;
        
        // Enhanced scoring with proper 0-1 normalization for test compatibility
        // Exact name match gets highest score
        if (asset.getName().toLowerCase().equals(query)) {
            score = 1.0; // Perfect match = 1.0
        } else if (asset.getName().toLowerCase().contains(query)) {
            // Partial name match: higher score if query is at start, lower if in middle
            String lowerName = asset.getName().toLowerCase();
            if (lowerName.startsWith(query)) {
                score = 0.9; // "java" in "javascript" gets lower priority than exact "java"
            } else {
                score = 0.7; // "java" somewhere in middle gets even lower priority
            }
        }
        
        // Tag matches - boost score but keep within 0-1 range
        double tagBonus = 0.0;
        for (String tag : asset.getTags()) {
            if (tag.toLowerCase().equals(query)) {
                tagBonus = Math.max(tagBonus, 0.8); // High bonus for exact tag match
            } else if (tag.toLowerCase().contains(query)) {
                if (tag.toLowerCase().startsWith(query)) {
                    tagBonus = Math.max(tagBonus, 0.6); // Tag starts with query
                } else {
                    tagBonus = Math.max(tagBonus, 0.4); // Tag contains query somewhere
                }
            } else if (query.contains(tag.toLowerCase())) {
                tagBonus = Math.max(tagBonus, 0.2); // Query contains the tag (lowest priority)
            }
        }
        
        // Combine name and tag scores, ensuring exact name matches still rank highest
        if (score < tagBonus && score < 0.9) {
            score = tagBonus;
        } else if (score > 0.0 && tagBonus > 0.0) {
            // Add small tag bonus to existing name score without exceeding 1.0
            score = Math.min(1.0, score + (tagBonus * 0.1));
        }
        
        return Math.max(0.0, Math.min(1.0, score)); // Ensure range [0, 1]
    }
    
    public ExecutionResult<IconAsset> downloadIcon(IconAsset iconAsset) {
        try {
            File iconFile = new File(iconAsset.getPath());
            
            // If file already exists, return it
            if (iconFile.exists()) {
                return ExecutionResult.success("icon-download", iconAsset);
            }
            
            // Download based on source
            if ("devicon".equals(iconAsset.getSource())) {
                return downloadDeviconIcon(iconAsset);
            } else if ("local".equals(iconAsset.getSource())) {
                // Local files should already exist
                if (iconFile.exists()) {
                    return ExecutionResult.success("icon-download", iconAsset);
                } else {
                    return ExecutionResult.failure("icon-download", "Local icon file not found: " + iconAsset.getPath());
                }
            } else if ("iconify".equals(iconAsset.getSource())) {
                return downloadIconifyIcon(iconAsset);
            } else if ("freepik".equals(iconAsset.getSource())) {
                return downloadFreepikIcon(iconAsset);
            }

            return ExecutionResult.failure("icon-download", "Unknown icon source: " + iconAsset.getSource());
            
        } catch (Exception e) {
            return ExecutionResult.failure("icon-download", "Download failed: " + e.getMessage(), e);
        }
    }
    
    private ExecutionResult<IconAsset> downloadDeviconIcon(IconAsset iconAsset) {
        try {
            String iconName = iconAsset.getName();
            Path targetPath = Paths.get(iconAsset.getPath());
            
            // Ensure parent directory exists
            Files.createDirectories(targetPath.getParent());
            
            // Try Devicon naming patterns: prefer original, fallback to plain, then line
            String[] urlPatterns = {
                "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/" + iconName + "/" + iconName + "-original.svg",
                "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/" + iconName + "/" + iconName + "-plain.svg",
                "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/" + iconName + "/" + iconName + "-line.svg"
            };
            
            for (String urlPattern : urlPatterns) {
                try {
                    URL url = new URL(urlPattern);
                    try (InputStream in = url.openStream()) {
                        Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Downloaded Devicon icon: {} from {}", iconName, urlPattern);
                        return ExecutionResult.success("icon-download", iconAsset);
                    }
                } catch (IOException e) {
                    // Try next pattern
                    continue;
                }
            }
            
            return ExecutionResult.failure("icon-download", 
                "Failed to download Devicon icon '" + iconName + "' - none of the URL patterns worked");
            
        } catch (Exception e) {
            return ExecutionResult.failure("icon-download", 
                "Failed to download Devicon icon '" + iconAsset.getName() + "': " + e.getMessage(), e);
        }
    }
    
    private ExecutionResult<IconAsset> downloadFreepikIcon(IconAsset iconAsset) {
        if (freepikApiKey == null || freepikApiKey.trim().isEmpty()) {
            return ExecutionResult.failure("icon-download", "Freepik API key not provided");
        }
        
        try {
            // Use the stored resourceId from the IconAsset
            String resourceId = iconAsset.getResourceId();
            if (resourceId == null) {
                return ExecutionResult.failure("icon-download", "No resource ID available for Freepik icon");
            }
            
            String downloadUrl = FREEPIK_API_BASE + FREEPIK_DOWNLOAD_ENDPOINT.replace("{id}", resourceId);
            
            logger.debug("Requesting Freepik download URL for resource ID: {}", resourceId);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("x-freepik-api-key", freepikApiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "Excudo/1.0")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            logger.debug("Freepik download API response status: {}", response.statusCode());
            
            if (response.statusCode() == 200) {
                // Parse response to get actual download URL
                String responseBody = response.body();
                JsonObject downloadObj = JsonParser.parseString(responseBody).getAsJsonObject();
                String actualDownloadUrl = downloadObj.has("url") ? downloadObj.get("url").getAsString() : null;

                if (actualDownloadUrl == null && downloadObj.has("download_url")) {
                    actualDownloadUrl = downloadObj.get("download_url").getAsString();
                }
                if (actualDownloadUrl == null && downloadObj.has("file_url")) {
                    actualDownloadUrl = downloadObj.get("file_url").getAsString();
                }
                
                if (actualDownloadUrl != null) {
                    // Download the actual file
                    return downloadFileFromUrl(actualDownloadUrl, iconAsset);
                } else {
                    logger.warn("No download URL found in response: {}", responseBody);
                    return ExecutionResult.failure("icon-download", 
                        "No download URL provided by Freepik API");
                }
            } else {
                logger.warn("Freepik download failed with response: {}", response.body());
                return ExecutionResult.failure("icon-download", 
                    "Freepik download failed with status: " + response.statusCode() + " - " + response.body());
            }
            
        } catch (Exception e) {
            logger.error("Freepik download error: {}", e.getMessage());
            return ExecutionResult.failure("icon-download", 
                "Failed to download Freepik icon '" + iconAsset.getName() + "': " + e.getMessage(), e);
        }
    }
    
    private ExecutionResult<IconAsset> downloadFileFromUrl(String fileUrl, IconAsset iconAsset) {
        try {
            logger.debug("Downloading file from URL: {}", fileUrl);
            
            Path targetPath = Paths.get(iconAsset.getPath());
            
            // Ensure parent directory exists
            Files.createDirectories(targetPath.getParent());
            
            // Fix escaped slashes in URL from Freepik API
            String cleanUrl = fileUrl.replace("\\/", "/");
            logger.debug("Clean URL: {}", cleanUrl);
            
            // Use HttpClient for consistent proxy handling
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cleanUrl))
                .GET()
                .build();
            
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() == 200) {
                try (InputStream in = response.body()) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                
                logger.info("Downloaded Freepik icon: {} to {}", iconAsset.getName(), targetPath);
                return ExecutionResult.success("icon-download", iconAsset);
            } else {
                logger.warn("Download failed with HTTP status: {}", response.statusCode());
                return ExecutionResult.failure("icon-download", 
                    "Download failed with HTTP status: " + response.statusCode());
            }
            
        } catch (Exception e) {
            logger.error("Failed to download file from URL: {}", e.getMessage());
            return ExecutionResult.failure("icon-download", 
                "Failed to download file from URL '" + fileUrl + "': " + e.getMessage(), e);
        }
    }
    
    public ExecutionResult<IconAsset> uploadLocalIcon(String filePath, String iconName, Set<String> tags) {
        try {
            Path sourcePath = Paths.get(filePath);
            if (!Files.exists(sourcePath)) {
                return ExecutionResult.failure("icon-upload", "Source file does not exist: " + filePath);
            }
            
            String extension = getFileExtension(sourcePath.toString());
            String targetFileName = iconName + "." + extension;
            Path targetPath = Paths.get(cacheDirectory, "uploads", targetFileName);
            
            Files.createDirectories(targetPath.getParent());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            IconAsset uploadedIcon = new IconAsset(
                iconName,
                "local",
                targetPath.toString(),
                tags,
                "Local upload",
                1.0
            );
            
            localUploadedIcons.put(targetFileName, uploadedIcon);
            
            logger.info("Uploaded local icon: {} to {}", iconName, targetPath);
            return ExecutionResult.success("icon-upload", uploadedIcon);
            
        } catch (Exception e) {
            return ExecutionResult.failure("icon-upload", "Upload failed: " + e.getMessage(), e);
        }
    }
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1).toLowerCase() : "svg";
    }
    
    public List<IconAsset> getAllAvailableIcons() {
        List<IconAsset> allIcons = new ArrayList<>();
        allIcons.addAll(deviconIndex.values());
        
        // Add local uploads
        for (IconAsset asset : localUploadedIcons.values()) {
            allIcons.add(asset);
        }
        
        return allIcons;
    }
    
    public String getCacheDirectory() {
        return cacheDirectory;
    }
    
    public int getDeviconIconCount() {
        return deviconIndex.size();
    }
    
    public int getLocalIconCount() {
        return localUploadedIcons.size();
    }
    
    public boolean isFreepikAPIEnabled() {
        return enableFreepikAPI;
    }
    
    public void setFreepikApiKey(String apiKey) {
        this.freepikApiKey = apiKey;
    }
    
    public boolean hasFreepikApiKey() {
        return freepikApiKey != null && !freepikApiKey.trim().isEmpty();
    }
}