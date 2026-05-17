package ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import config.AppConfig;
import config.ThemeManager;
import config.ThemeManager.SyntaxTheme;
import utils.FrontMatter;
import utils.PlantUmlEncoder;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

/**
 * Panel de prévisualisation Markdown.
 * Affiche le rendu HTML du contenu Markdown.
 */
public class PreviewPanel extends BasePanel {

    private final WebView webView;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private File baseDirectory;
    private Consumer<File> onMarkdownLinkClick;
    private AppConfig appConfig;
    /** Callback fired with {@code true} when local-jar PlantUML rendering starts, {@code false} when all blocks are done. */
    private Consumer<Boolean> onPlantUmlRenderingChanged;
    /** Global ID sequence for placeholder divs; never resets so stale threads find no element in a new page. */
    private final AtomicInteger blockIdSequence = new AtomicInteger(0);
    /** Blocks queued for the next page load (local jar mode). Thread-safe because populated on FX thread, consumed on FX thread after load. */
    private final CopyOnWriteArrayList<String[]> pendingLocalPumlBlocks = new CopyOnWriteArrayList<>();
    /** Number of background PlantUML render threads still running. */
    private final AtomicInteger pendingPumlCount = new AtomicInteger(0);
    
    // Historique de navigation
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String currentMarkdown = "";
    private boolean navigating = false;
    
    private final Button prevButton;
    private final Button nextButton;

    /** Dernier HTML généré, utilisé pour l'export. */
    private String currentHtml = "";

    /** Fichier source Markdown courant (peut être null pour les documents non sauvegardés). */
    private File currentFile;

    /** Thème highlight.js courant, synchronisé avec le thème applicatif. */
    private SyntaxTheme syntaxTheme = new SyntaxTheme("github", "#f6f8fa", "#24292e");

    /** Pattern pour détecter les blocs PlantUML dans le HTML généré par Flexmark. */
    private static final Pattern PLANTUML_BLOCK = Pattern.compile(
            "<pre><code\\s+class=\"language-plantuml\">(.*?)</code></pre>",
            Pattern.DOTALL);

    /** Cache des SVG PlantUML générés (clé = hash SHA-256 du code source). */
    private static final Map<String, String> pumlSvgCache = new ConcurrentHashMap<>();

    /**
     * Pattern pour détecter la syntaxe d'image étendue avec dimensions.
     * Formats supportés :
     *   ![alt](url "title" =100x20)
     *   ![alt](url =200x)
     *   ![alt](url =x120)
     */
    private static final Pattern IMAGE_SIZE_PATTERN = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(([^\\s)]+)(?:\\s+\"([^\"]*)\")?\\s+=([0-9]*)x([0-9]*)\\)");

    public PreviewPanel() {
        super("preview.title", "preview.close.tooltip");

        // Initialiser Flexmark avec l'extension Tables
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
        markdownParser = Parser.builder(options).build();
        htmlRenderer = HtmlRenderer.builder(options).build();

        // Créer le WebView
        webView = new WebView();
        
        // Autoriser JavaScript et les accès réseau
        webView.getEngine().setJavaScriptEnabled(true);
        
        // Activer le debug JavaScript : capturer les messages console et erreurs
        webView.getEngine().setOnAlert(event -> 
            System.out.println("[JS Alert] " + event.getData()));
        
        webView.getEngine().setOnError(event -> 
            System.err.println("[JS Error] " + event.getMessage()));
        
        // Capturer les exceptions JavaScript via le gestionnaire d'état
        webView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                System.err.println("[WebView Exception] " + newEx.getMessage());
                newEx.printStackTrace();
            }
        });
        
        // Ajouter les boutons de navigation dans le header
        prevButton = new Button("\u00AB");
        prevButton.getStyleClass().add("panel-nav-button");
        prevButton.setTooltip(new Tooltip(getMessages().getString("preview.prev.tooltip")));
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> navigateBack());
        
        nextButton = new Button("\u00BB");
        nextButton.getStyleClass().add("panel-nav-button");
        nextButton.setTooltip(new Tooltip(getMessages().getString("preview.next.tooltip")));
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> navigateForward());
        
        Button refreshButton = new Button("\u21BB");
        refreshButton.getStyleClass().add("panel-nav-button");
        refreshButton.setTooltip(new Tooltip(getMessages().getString("preview.refresh.tooltip")));
        refreshButton.setOnAction(e -> refresh());

        // Bouton d'export (menu déroulant)
        MenuButton exportButton = new MenuButton("\u2913");
        exportButton.getStyleClass().add("panel-nav-button");
        exportButton.setTooltip(new Tooltip(getMessages().getString("preview.export.tooltip")));

        MenuItem pdfItem = new MenuItem(getMessages().getString("preview.export.pdf"));
        pdfItem.setOnAction(e -> exportToPdf());

        MenuItem htmlZipItem = new MenuItem(getMessages().getString("preview.export.html.zip"));
        htmlZipItem.setOnAction(e -> exportToHtmlZip());

        exportButton.getItems().addAll(pdfItem, new SeparatorMenuItem(), htmlZipItem);

        // Insérer les boutons avant le bouton de fermeture
        HBox header = getHeader();
        int closeIndex = header.getChildren().indexOf(getCloseButton());
        header.getChildren().add(closeIndex, prevButton);
        header.getChildren().add(closeIndex + 1, nextButton);
        header.getChildren().add(closeIndex + 2, refreshButton);
        header.getChildren().add(closeIndex + 3, exportButton);
        
        // Intercepter les clics sur les liens + déclencher le rendu async PlantUML local
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SCHEDULED) {
                String location = webView.getEngine().getLocation();
                if (location != null && !location.isEmpty() && !location.equals("about:blank")) {
                    // Ancre interne (#fragment) : défiler vers l'élément sans recharger la page
                    if (location.contains("#")) {
                        String fragment = location.substring(location.indexOf('#') + 1);
                        if (!fragment.isEmpty()) {
                            webView.getEngine().getLoadWorker().cancel();
                            webView.getEngine().executeScript(
                                "var el = document.getElementById('" + fragment.replace("'", "\\'") + "');" +
                                "if (el) el.scrollIntoView({behavior: 'smooth', block: 'start'});"
                            );
                        }
                        return;
                    }
                    // Vérifier si c'est un lien vers un fichier .md
                    if (location.toLowerCase().endsWith(".md") || location.toLowerCase().endsWith(".markdown")) {
                        // Annuler la navigation
                        webView.getEngine().getLoadWorker().cancel();
                        
                        // Convertir l'URL en fichier
                        try {
                            File mdFile = null;
                            if (location.startsWith("file:")) {
                                mdFile = new File(new URI(location));
                            } else if (baseDirectory != null) {
                                // Lien relatif
                                mdFile = new File(baseDirectory, location);
                            }
                            
                            if (mdFile != null && mdFile.exists() && onMarkdownLinkClick != null) {
                                onMarkdownLinkClick.accept(mdFile);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (location.startsWith("marknote-link:")) {
                        // Lien vers un document par UUID
                        webView.getEngine().getLoadWorker().cancel();
                        String uuid = location.substring("marknote-link:".length());
                        File target = findFileByUuid(uuid);
                        if (target != null && onMarkdownLinkClick != null) {
                            onMarkdownLinkClick.accept(target);
                        }
                    }
                }
            } else if (newState == Worker.State.SUCCEEDED && !pendingLocalPumlBlocks.isEmpty()) {
                // Page chargée : déclencher le rendu async des blocs PlantUML locaux
                dispatchLocalPumlRendering();
            }
        });
        
        setContent(webView);
    }

    /**
     * Met à jour la prévisualisation avec le contenu Markdown donné.
     *
     * @param markdown Le contenu Markdown à afficher
     */
    public void updatePreview(String markdown) {
        updatePreview(markdown, true);
    }
    
    /**
     * Met à jour la prévisualisation avec le contenu Markdown donné.
     *
     * @param markdown Le contenu Markdown à afficher
     * @param addToHistory Ajouter à l'historique de navigation
     */
    private void updatePreview(String markdown, boolean addToHistory) {
        if (markdown == null) {
            markdown = "";
        }
        
        // Ajouter à l'historique si nécessaire
        if (addToHistory && !navigating && !markdown.equals(currentMarkdown)) {
            // Supprimer l'historique après l'index actuel
            while (history.size() > historyIndex + 1) {
                history.remove(history.size() - 1);
            }
            history.add(markdown);
            historyIndex = history.size() - 1;
            updateNavigationButtons();
        }
        currentMarkdown = markdown;

        // ── Front Matter : extraire et rendre séparément
        FrontMatter fm = FrontMatter.parse(markdown);
        String body = fm != null ? FrontMatter.stripFrontMatter(markdown) : markdown;
        String frontMatterHtml = fm != null && !fm.isEmpty() ? renderFrontMatterHtml(fm) : "";

        // ── Images avec dimensions : pré-traiter la syntaxe =WxH
        Map<String, int[]> imageSizes = new HashMap<>();
        body = preprocessImageSizes(body, imageSizes);
        
        String html = htmlRenderer.render(markdownParser.parse(body));

        // ── IDs sur les titres : nécessaires pour la navigation par ancres (#fragment)
        html = processHeadingIds(html);

        // ── Checkboxes : convertir [ ] et [x] en éléments checkbox HTML
        html = processCheckboxes(html);

        // ── GitHub Alerts : convertir les blockquotes [!NOTE], [!WARNING], etc.
        html = processGitHubAlerts(html);

        // ── Images : injecter les attributs width/height
        if (!imageSizes.isEmpty()) {
            html = applyImageSizes(html, imageSizes);
        }

        // ── PlantUML : remplacer les blocs <pre><code class="language-plantuml">
        //    par des <img> pointant vers le serveur PlantUML en ligne.
        html = processPlantUmlBlocks(html);

        // Construire le tag base si un répertoire de base est défini
        String baseTag = "";
        if (baseDirectory != null && baseDirectory.exists()) {
            String baseUrl = baseDirectory.toURI().toString();
            baseTag = "<base href=\"" + baseUrl + "\">";
        }
        
        String hljsStyle = syntaxTheme.highlightStyle();
        String preBg = syntaxTheme.preBackground();
        String codeFg = syntaxTheme.codeForeground();

        // Choisir le thème Mermaid en fonction du thème applicatif
        String mermaidTheme = syntaxTheme.highlightStyle().contains("dark")
                || syntaxTheme.highlightStyle().contains("a11y-dark")
                ? "dark" : "default";

        // Charger les ressources CSS (petites, peuvent être inline)
        String hljsCss = loadResourceAsString("/js/hljs/styles/" + hljsStyle + ".min.css");
        String katexCss = loadResourceAsString("/js/katex/katex.min.css");
        String previewJs = loadResourceAsString("/js/preview.js");
        
        // Extraire les fichiers JS volumineux vers des fichiers temporaires
        // (loadContent() ne supporte pas les contenus > ~2MB)
        File tempDir = ensureTempResourceDir();
        String hljsJsUrl = extractResourceToTemp(tempDir, "/js/hljs/highlight.min.js", "hljs.min.js");
        String mermaidJsUrl = extractResourceToTemp(tempDir, "/js/mermaid.min.js", "mermaid.min.js");
        String katexJsUrl = extractResourceToTemp(tempDir, "/js/katex/katex.min.js", "katex.min.js");
        
        System.out.println("[Preview] Resources extracted to: " + tempDir.getAbsolutePath());
        
        // Script de redirection console vers Java (via alert intercepté par setOnAlert)
        String consoleRedirect = """
            (function() {
              var originalLog = console.log;
              var originalError = console.error;
              var originalWarn = console.warn;
              console.log = function() {
                var msg = Array.prototype.slice.call(arguments).join(' ');
                alert('[LOG] ' + msg);
                originalLog.apply(console, arguments);
              };
              console.error = function() {
                var msg = Array.prototype.slice.call(arguments).join(' ');
                alert('[ERROR] ' + msg);
                originalError.apply(console, arguments);
              };
              console.warn = function() {
                var msg = Array.prototype.slice.call(arguments).join(' ');
                alert('[WARN] ' + msg);
                originalWarn.apply(console, arguments);
              };
              window.onerror = function(msg, url, line, col, error) {
                alert('[UNCAUGHT] ' + msg + ' at line ' + line + ':' + col);
                return false;
              };
            })();
            """;
        
        // Construire le HTML avec les scripts chargés depuis des fichiers temporaires
        String htmlPage = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta http-equiv="Content-Security-Policy" content="default-src * 'unsafe-inline' 'unsafe-eval' data: blob:; img-src * data: blob: http: https:;">
                  %s
                  <script>%s</script>
                  <style>%s</style>
                  <script src="%s"></script>
                  <script src="%s"></script>
                  <style>%s</style>
                  <script src="%s"></script>
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    pre { background: %s; padding: 0.8em; border-radius: 6px; overflow-x: auto; position: relative; }
                    pre code { font-family: 'Source Code Pro', 'Fira Code', 'Consolas', monospace;
                               font-size: 0.9em; color: %s; }
                    code { font-family: monospace; }
                    img { max-width: 100%%; height: auto; }
                    .plantuml-diagram { text-align: center; margin: 1em 0; }
                    .plantuml-diagram img { max-width: 100%%; height: auto; }
                    .mermaid { text-align: center; margin: 1em 0; }
                    table { border-collapse: collapse; width: auto; margin: 1em 0; }
                    th, td { border: 1px solid #888; padding: 6px 12px; text-align: left; }
                    th { background: rgba(128,128,128,0.15); font-weight: bold; }
                    tr:nth-child(even) { background: rgba(128,128,128,0.06); }
                    input[type="checkbox"] { width: 1.1em; height: 1.1em; margin-right: 0.4em; 
                                             vertical-align: middle; cursor: default; accent-color: #0078d7; }
                    li:has(input[type="checkbox"]) { list-style: none; margin-left: -1.2em; }
                    .front-matter { background: rgba(128,128,128,0.08); border: 1px solid rgba(128,128,128,0.25);
                                    border-radius: 6px; padding: 0.6em 1em; margin-bottom: 1.2em;
                                    font-size: 0.9em; color: #555; }
                    .front-matter h1 { font-size: 1.4em; margin: 0 0 0.3em 0; color: #333; }
                    .front-matter .fm-field { margin: 0.15em 0; }
                    .front-matter .fm-label { font-weight: bold; }
                    .front-matter .fm-tag { display: inline-block; background: rgba(0,120,215,0.12);
                                            border-radius: 3px; padding: 1px 6px; margin: 1px 2px; font-size: 0.85em; }
                    .front-matter .fm-draft { color: #d9534f; font-weight: bold; }
                    .front-matter .fm-link { display: inline-block; background: rgba(0,120,215,0.12);
                                             border-radius: 3px; padding: 1px 6px; margin: 1px 2px;
                                             font-size: 0.85em; text-decoration: none; color: #0078d7; }
                    .front-matter .fm-link:hover { text-decoration: underline; background: rgba(0,120,215,0.2); }
                    .front-matter .fm-summary { cursor: pointer; font-weight: bold; font-size: 0.9em; color: #666; padding: 0.2em 0; }
                    .front-matter .fm-summary:hover { color: #333; }
                    pre .copy-btn { position: absolute; top: 4px; right: 4px; padding: 2px 8px;
                                    font-size: 0.75em; cursor: pointer; background: rgba(128,128,128,0.2);
                                    border: 1px solid rgba(128,128,128,0.3); border-radius: 4px;
                                    color: inherit; opacity: 0; transition: opacity 0.2s; }
                    pre:hover .copy-btn { opacity: 1; }
                    pre .copy-btn:hover { background: rgba(128,128,128,0.35); }
                    pre .copy-btn.copied { background: rgba(76,175,80,0.3); border-color: rgba(76,175,80,0.5); }
                    .markdown-alert { padding: 0.8em 1em; margin: 1em 0; border-left: 4px solid; border-radius: 6px; }
                    .markdown-alert-title { display: flex; align-items: center; font-weight: 600; margin-bottom: 0.4em; }
                    .markdown-alert-title svg { margin-right: 0.5em; }
                    .markdown-alert p { margin: 0.3em 0; }
                    .markdown-alert-note { background: rgba(9, 105, 218, 0.1); border-color: #0969da; }
                    .markdown-alert-note .markdown-alert-title { color: #0969da; }
                    .markdown-alert-tip { background: rgba(26, 127, 55, 0.1); border-color: #1a7f37; }
                    .markdown-alert-tip .markdown-alert-title { color: #1a7f37; }
                    .markdown-alert-important { background: rgba(130, 80, 223, 0.1); border-color: #8250df; }
                    .markdown-alert-important .markdown-alert-title { color: #8250df; }
                    .markdown-alert-warning { background: rgba(154, 103, 0, 0.1); border-color: #9a6700; }
                    .markdown-alert-warning .markdown-alert-title { color: #9a6700; }
                    .markdown-alert-caution { background: rgba(207, 34, 46, 0.1); border-color: #cf222e; }
                    .markdown-alert-caution .markdown-alert-title { color: #cf222e; }
                  </style>
                </head>
                <body>%s%s
                <script>%s</script>
                <script>
                  // Vérifications de chargement des bibliothèques
                  alert('[CHECK] hljs: ' + (typeof hljs !== 'undefined' ? 'OK' : 'MISSING'));
                  alert('[CHECK] mermaid: ' + (typeof mermaid !== 'undefined' ? 'OK' : 'MISSING'));
                  alert('[CHECK] katex: ' + (typeof katex !== 'undefined' ? 'OK' : 'MISSING'));
                  alert('[CHECK] initializePreview: ' + (typeof initializePreview !== 'undefined' ? 'OK' : 'MISSING'));
                  try {
                    initializePreview('%s');
                    alert('[INIT] Preview initialized successfully');
                  } catch(e) {
                    alert('[INIT ERROR] ' + e.message + '\\n' + e.stack);
                  }
                </script>
                </body>
                </html>
                """.formatted(baseTag, consoleRedirect, hljsCss, hljsJsUrl, mermaidJsUrl, katexCss, katexJsUrl, 
                              preBg, codeFg, frontMatterHtml, html, previewJs, mermaidTheme);

        // Sauvegarder le HTML dans un fichier temporaire et le charger via load()
        // (loadContent() bloque le chargement des scripts externes file://)
        try {
            File htmlFile = new File(tempDir, "preview.html");
            Files.writeString(htmlFile.toPath(), htmlPage, StandardCharsets.UTF_8);
            htmlFile.deleteOnExit();
            webView.getEngine().load(htmlFile.toURI().toString());
            System.out.println("[Preview] HTML file: " + htmlFile.toURI() + " - size: " + htmlPage.length() + " bytes");
        } catch (Exception e) {
            System.err.println("[Preview] Failed to write HTML file: " + e.getMessage());
            // Fallback sur loadContent (sans scripts externes)
            webView.getEngine().loadContent(htmlPage, "text/html; charset=UTF-8");
        }
        System.out.println("[Preview] Content preview: " + 
                           (html.length() > 100 ? html.substring(0, 100) + "..." : html));
        this.currentHtml = htmlPage;
    }

    /** Répertoire temporaire pour les ressources JS volumineuses. */
    private File tempResourceDir;
    
    /**
     * Assure l'existence du répertoire temporaire pour les ressources JS.
     * @return Le répertoire temporaire
     */
    private File ensureTempResourceDir() {
        if (tempResourceDir == null || !tempResourceDir.exists()) {
            try {
                tempResourceDir = Files.createTempDirectory("marknote-preview-").toFile();
                tempResourceDir.deleteOnExit();
                System.out.println("[Preview] Created temp dir: " + tempResourceDir.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[Preview] Failed to create temp dir: " + e.getMessage());
                // Fallback vers le répertoire tmp système
                tempResourceDir = new File(System.getProperty("java.io.tmpdir"), "marknote-preview");
                tempResourceDir.mkdirs();
            }
        }
        return tempResourceDir;
    }
    
    /** Cache des URLs de fichiers temporaires déjà extraits. */
    private final Map<String, String> tempFileUrlCache = new HashMap<>();
    
    /**
     * Extrait une ressource du classpath vers un fichier temporaire.
     * Le fichier est mis en cache pour éviter les extractions répétées.
     * 
     * @param tempDir Le répertoire temporaire
     * @param resourcePath Chemin de la ressource dans le classpath
     * @param fileName Nom du fichier de destination
     * @return URL file:// vers le fichier extrait
     */
    private String extractResourceToTemp(File tempDir, String resourcePath, String fileName) {
        String cacheKey = resourcePath + ":" + fileName;
        return tempFileUrlCache.computeIfAbsent(cacheKey, k -> {
            File targetFile = new File(tempDir, fileName);
            if (!targetFile.exists()) {
                try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Files.copy(is, targetFile.toPath());
                        targetFile.deleteOnExit();
                        System.out.println("[Preview] Extracted: " + resourcePath + " -> " + targetFile.getAbsolutePath());
                    } else {
                        System.err.println("[Preview] Resource not found: " + resourcePath);
                        return "";
                    }
                } catch (Exception e) {
                    System.err.println("[Preview] Failed to extract " + resourcePath + ": " + e.getMessage());
                    return "";
                }
            }
            return targetFile.toURI().toString();
        });
    }

    /**
     * Charge une ressource comme String depuis le classpath.
     * Le contenu est mis en cache après le premier chargement.
     */
    private final Map<String, String> resourceCache = new HashMap<>();
    
    private String loadResourceAsString(String resourcePath) {
        return resourceCache.computeIfAbsent(resourcePath, path -> {
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } else {
                    System.err.println("[ERROR] Resource not found: " + path);
                    return "";
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Error loading resource " + path + ": " + e.getMessage());
                return "";
            }
        });
    }

    /**
     * Injecte un attribut {@code id} GitHub-compatible sur chaque titre HTML ({@code <h1>}–{@code <h6>})
     * qui n'en possède pas encore. Requis pour que la navigation par ancres (#fragment) fonctionne.
     */
    private String processHeadingIds(String html) {
        Pattern p = Pattern.compile(
                "<(h[1-6])(\\s[^>]*)?>(.+?)</h[1-6]>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        StringBuffer sb = new StringBuffer();
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        while (m.find()) {
            String tag     = m.group(1);
            String attrs   = m.group(2) != null ? m.group(2) : "";
            String content = m.group(3);
            if (attrs.contains("id=")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String text = content.replaceAll("<[^>]+>", "").trim();
            String id = text.toLowerCase()
                    .replaceAll("[^\\w\\s-]", "")
                    .trim()
                    .replaceAll("\\s+", "-");
            int count = seen.getOrDefault(id, 0);
            seen.put(id, count + 1);
            String uid = count == 0 ? id : id + "-" + count;
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<" + tag + attrs + " id=\"" + uid + "\">" + content + "</" + tag + ">"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Convertit les marqueurs de checkbox Markdown ([ ] et [x]) en éléments HTML checkbox.
     * 
     * <p>Patterns reconnus :</p>
     * <ul>
     *   <li>{@code [ ]} → checkbox non cochée</li>
     *   <li>{@code [x]} ou {@code [X]} → checkbox cochée</li>
     * </ul>
     *
     * @param html le HTML généré par Flexmark
     * @return le HTML avec les checkboxes converties
     */
    private String processCheckboxes(String html) {
        // Remplacer [ ] par une checkbox non cochée
        html = html.replaceAll(
            "\\[ \\]",
            "<input type=\"checkbox\" disabled>"
        );
        // Remplacer [x] ou [X] par une checkbox cochée
        html = html.replaceAll(
            "\\[[xX]\\]",
            "<input type=\"checkbox\" checked disabled>"
        );
        return html;
    }

    /** Pattern pour détecter les blockquotes GitHub Alerts. */
    private static final Pattern GITHUB_ALERT_PATTERN = Pattern.compile(
            "<blockquote>\\s*<p>\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\]\\s*(.*?)</p>(.*?)</blockquote>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Icônes SVG pour les alertes GitHub. */
    private static final Map<String, String> ALERT_ICONS = Map.of(
            "NOTE", "<svg viewBox='0 0 16 16' width='16' height='16' fill='currentColor'><path d='M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z'/></svg>",
            "TIP", "<svg viewBox='0 0 16 16' width='16' height='16' fill='currentColor'><path d='M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 0 1-1.484.211c-.04-.282-.163-.547-.37-.847a8.456 8.456 0 0 0-.542-.68c-.084-.1-.173-.205-.268-.32C3.201 7.75 2.5 6.766 2.5 5.25 2.5 2.31 4.863 0 8 0s5.5 2.31 5.5 5.25c0 1.516-.701 2.5-1.328 3.259-.095.115-.184.22-.268.319-.207.245-.383.453-.541.681-.208.3-.33.565-.37.847a.751.751 0 0 1-1.485-.212c.084-.593.337-1.078.621-1.489.203-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637-3.75-4-3.75ZM5.75 12h4.5a.75.75 0 0 1 0 1.5h-4.5a.75.75 0 0 1 0-1.5ZM6 15.25a.75.75 0 0 1 .75-.75h2.5a.75.75 0 0 1 0 1.5h-2.5a.75.75 0 0 1-.75-.75Z'/></svg>",
            "IMPORTANT", "<svg viewBox='0 0 16 16' width='16' height='16' fill='currentColor'><path d='M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0 1 14.25 13H8.06l-2.573 2.573A1.458 1.458 0 0 1 3 14.543V13H1.75A1.75 1.75 0 0 1 0 11.25Zm1.75-.25a.25.25 0 0 0-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h6.5a.25.25 0 0 0 .25-.25v-9.5a.25.25 0 0 0-.25-.25Zm7 2.25v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 9a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z'/></svg>",
            "WARNING", "<svg viewBox='0 0 16 16' width='16' height='16' fill='currentColor'><path d='M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0 1 14.082 15H1.918a1.75 1.75 0 0 1-1.543-2.575Zm1.763.707a.25.25 0 0 0-.44 0L1.698 13.132a.25.25 0 0 0 .22.368h12.164a.25.25 0 0 0 .22-.368Zm.53 3.996v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z'/></svg>",
            "CAUTION", "<svg viewBox='0 0 16 16' width='16' height='16' fill='currentColor'><path d='M4.47.22A.749.749 0 0 1 5 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 0 1-.22.53l-4.25 4.25A.749.749 0 0 1 11 16H5a.749.749 0 0 1-.53-.22L.22 11.53A.749.749 0 0 1 0 11V5c0-.199.079-.389.22-.53Zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5ZM8 4a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z'/></svg>"
    );

    /** Labels traduits pour les alertes GitHub. */
    private static final Map<String, String> ALERT_LABELS = Map.of(
            "NOTE", "Note",
            "TIP", "Tip",
            "IMPORTANT", "Important",
            "WARNING", "Warning",
            "CAUTION", "Caution"
    );

    /**
     * Convertit les blockquotes GitHub Alerts en éléments stylisés.
     * 
     * <p>Patterns reconnus :</p>
     * <ul>
     *   <li>{@code > [!NOTE]} → bloc note bleu</li>
     *   <li>{@code > [!TIP]} → bloc tip vert</li>
     *   <li>{@code > [!IMPORTANT]} → bloc important violet</li>
     *   <li>{@code > [!WARNING]} → bloc warning jaune</li>
     *   <li>{@code > [!CAUTION]} → bloc caution rouge</li>
     * </ul>
     *
     * @param html le HTML généré par Flexmark
     * @return le HTML avec les alertes converties
     */
    private String processGitHubAlerts(String html) {
        Matcher m = GITHUB_ALERT_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String type = m.group(1).toUpperCase();
            String firstLineContent = m.group(2).trim();
            String restContent = m.group(3).trim();
            
            String icon = ALERT_ICONS.getOrDefault(type, "");
            String label = ALERT_LABELS.getOrDefault(type, type);
            String cssClass = "markdown-alert markdown-alert-" + type.toLowerCase();
            
            StringBuilder replacement = new StringBuilder();
            replacement.append("<div class=\"").append(cssClass).append("\">")
                       .append("<p class=\"markdown-alert-title\">")
                       .append(icon).append(label).append("</p>");
            
            // Ajouter le contenu de la première ligne s'il existe
            if (!firstLineContent.isEmpty()) {
                // Nettoyer les <br> ou <br/> en début de contenu
                firstLineContent = firstLineContent.replaceFirst("^<br\\s*/?>\\s*", "");
                if (!firstLineContent.isEmpty()) {
                    replacement.append("<p>").append(firstLineContent).append("</p>");
                }
            }
            
            // Ajouter le reste du contenu (autres paragraphes)
            if (!restContent.isEmpty()) {
                replacement.append(restContent);
            }
            
            replacement.append("</div>");
            
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Remplace les blocs {@code <pre><code class="language-plantuml">...}
     * par des balises {@code <img>} (serveur en ligne) ou par des placeholders
     * ({@code <div id="puml-N">}) quand le rendu local est activé.
     * Dans ce dernier cas, les blocs sont ajoutés à {@link #pendingLocalPumlBlocks}
     * pour être rendus de façon asynchrone une fois la page chargée.
     */
    private String processPlantUmlBlocks(String html) {
        // Vider les blocs en attente du rendu précédent (sécurité)
        pendingLocalPumlBlocks.clear();

        boolean useLocal = appConfig != null
                && appConfig.isUseLocalPlantUml()
                && appConfig.getPlantUmlJarPath() != null
                && !appConfig.getPlantUmlJarPath().isBlank();

        Matcher m = PLANTUML_BLOCK.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String raw = m.group(1);
            // Décoder les entités HTML courantes produites par Flexmark
            String puml = raw
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .trim();
            if (!puml.startsWith("@start")) {
                puml = "@startuml\n" + puml + "\n@enduml";
            }

            String diagramHtml;
            if (useLocal) {
                // Insérer un placeholder: rendu différé dans un thread de fond
                String id = "puml-" + blockIdSequence.getAndIncrement();
                pendingLocalPumlBlocks.add(new String[]{id, puml});
                diagramHtml = "<div id=\"" + id + "\" class=\"plantuml-diagram plantuml-pending\">" +
                        "<em style=\"opacity:0.45;\">&#8987; Rendering diagram…</em></div>";
            } else {
                String url = PlantUmlEncoder.toSvgUrl(puml);
                diagramHtml = "<div class=\"plantuml-diagram\"><img src=\"" + url + "\" alt=\"PlantUML diagram\"></div>";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(diagramHtml));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Démarre un thread de fond par bloc PlantUML en attente.
     * Chaque thread génère le SVG via le jar local (ou utilise le cache),
     * puis l'injecte dans la page via {@code executeScript}.
     * Quand tous les blocs sont rendus, le callback
     * {@link #onPlantUmlRenderingChanged} est appelé avec {@code false}.
     */
    private void dispatchLocalPumlRendering() {
        List<String[]> blocks = new ArrayList<>(pendingLocalPumlBlocks);
        pendingLocalPumlBlocks.clear();
        if (blocks.isEmpty()) return;

        pendingPumlCount.set(blocks.size());
        if (onPlantUmlRenderingChanged != null) {
            onPlantUmlRenderingChanged.accept(true);
        }

        String jarPath = appConfig.getPlantUmlJarPath();
        for (String[] block : blocks) {
            String id    = block[0];
            String puml  = block[1];
            String cacheKey = computePumlHash(puml);
            
            // Vérifier le cache d'abord
            String cachedSvg = pumlSvgCache.get(cacheKey);
            if (cachedSvg != null) {
                // Utiliser le SVG mis en cache directement sur le thread FX
                injectSvgIntoDom(id, cachedSvg, puml);
                continue;
            }
            
            // Pas en cache : lancer le rendu en arrière-plan
            Thread t = new Thread(() -> {
                String svg = renderWithLocalJar(puml, jarPath);
                if (svg != null) {
                    // Stocker dans le cache
                    pumlSvgCache.put(cacheKey, svg);
                }
                Platform.runLater(() -> injectSvgIntoDom(id, svg, puml));
            });
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Injecte un SVG PlantUML dans le DOM, ou utilise le serveur en ligne en cas d'échec.
     */
    private void injectSvgIntoDom(String id, String svg, String puml) {
        try {
            if (svg != null) {
                // Encoder en base64 pour éviter tout problème d'échappement JS
                String b64 = Base64.getEncoder().encodeToString(
                        svg.getBytes(StandardCharsets.UTF_8));
                String js = "var el=document.getElementById('" + id + "');"
                        + "if(el)el.outerHTML='<div class=\"plantuml-diagram\">"
                        + "<img src=\"data:image/svg+xml;base64," + b64 + "\""
                        + " alt=\"PlantUML diagram\"></div>';";
                webView.getEngine().executeScript(js);
            } else {
                // Repli : serveur en ligne
                String url = PlantUmlEncoder.toSvgUrl(puml);
                String js = "var el=document.getElementById('" + id + "');"
                        + "if(el)el.outerHTML='<div class=\"plantuml-diagram\">"
                        + "<img src=\"" + url + "\""
                        + " alt=\"PlantUML diagram\"></div>';";
                webView.getEngine().executeScript(js);
            }
        } catch (Exception ignored) {
        } finally {
            if (pendingPumlCount.decrementAndGet() == 0
                    && onPlantUmlRenderingChanged != null) {
                onPlantUmlRenderingChanged.accept(false);
            }
        }
    }

    /**
     * Calcule un hash SHA-256 du code PlantUML pour servir de clé de cache.
     */
    private String computePumlHash(String puml) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(puml.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback : utiliser le code lui-même comme clé
            return puml;
        }
    }

    /**
     * Génère un SVG en exécutant un jar PlantUML local via un sous-processus.
     * Retourne le SVG inline (chaîne commençant par {@code <svg}),
     * ou {@code null} si l'exécution échoue.
     *
     * @param pumlSource texte PlantUML complet (avec {@code @startuml/@enduml})
     * @param jarPath    chemin absolu vers {@code plantuml.jar}
     * @return SVG inline ou null en cas d'erreur
     */
    private String renderWithLocalJar(String pumlSource, String jarPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", jarPath, "-pipe", "-tsvg");
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Écrire la source PlantUML sur stdin
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(pumlSource.getBytes(StandardCharsets.UTF_8));
            }

            // Lire stdout (SVG)
            byte[] svgBytes;
            try (InputStream stdout = process.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                svgBytes = baos.toByteArray();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }

            String svg = new String(svgBytes, StandardCharsets.UTF_8).trim();
            // Extraire uniquement le contenu <svg>...</svg> (éliminer le prologue XML)
            int svgStart = svg.indexOf("<svg");
            if (svgStart >= 0) {
                return svg.substring(svgStart);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Pré-traite le Markdown pour extraire la syntaxe d'image étendue
     * {@code ![alt](url "title" =WxH)} et la convertir en syntaxe standard.
     * Les dimensions sont stockées dans la map {@code sizes} (clé = URL).
     *
     * @param markdown le corps Markdown
     * @param sizes    map de sortie : URL → {width, height} (0 = non spécifié)
     * @return le Markdown sans les suffixes {@code =WxH}
     */
    private String preprocessImageSizes(String markdown, Map<String, int[]> sizes) {
        Matcher m = IMAGE_SIZE_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String alt = m.group(1);
            String url = m.group(2);
            String title = m.group(3); // peut être null
            String wStr = m.group(4);  // peut être vide
            String hStr = m.group(5);  // peut être vide

            int w = wStr != null && !wStr.isEmpty() ? Integer.parseInt(wStr) : 0;
            int h = hStr != null && !hStr.isEmpty() ? Integer.parseInt(hStr) : 0;
            sizes.put(url, new int[]{w, h});

            // Reconstruire la syntaxe standard (sans =WxH)
            String replacement;
            if (title != null) {
                replacement = "![" + alt + "](" + url + " \"" + title + "\")";
            } else {
                replacement = "![" + alt + "](" + url + ")";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Post-traite le HTML pour injecter les attributs {@code width} et/ou
     * {@code height} sur les balises {@code <img>} dont l'URL correspond
     * à une entrée de la map {@code sizes}.
     */
    private String applyImageSizes(String html, Map<String, int[]> sizes) {
        for (var entry : sizes.entrySet()) {
            String url = entry.getKey();
            int[] dims = entry.getValue();
            StringBuilder style = new StringBuilder();
            if (dims[0] > 0) style.append("width:").append(dims[0]).append("px;");
            if (dims[1] > 0) style.append("height:").append(dims[1]).append("px;");
            if (style.length() > 0) {
                // Chercher la balise <img ... src="url" ...> et injecter un style inline
                String escaped = Pattern.quote(url);
                html = html.replaceAll(
                        "(<img\\s[^>]*src=\"" + escaped + "\")",
                        "$1 style=\"" + Matcher.quoteReplacement(style.toString()) + "\"");
            }
        }
        return html;
    }

    /**
     * Génère le HTML pour afficher les métadonnées Front Matter sous forme
     * de bloc stylisé en tête de page.
     */
    private String renderFrontMatterHtml(FrontMatter fm) {
        StringBuilder sb = new StringBuilder();
        sb.append("<details class=\"front-matter\">\n");
        // Titre du bloc repliable
        sb.append("  <summary class=\"fm-summary\">");
        if (!fm.getTitle().isBlank()) {
            sb.append(escapeHtml(fm.getTitle()));
        } else {
            sb.append("Front Matter");
        }
        sb.append("</summary>\n");
        if (!fm.getTitle().isBlank()) {
            sb.append("  <h1>").append(escapeHtml(fm.getTitle())).append("</h1>\n");
        }
        if (fm.isDraft()) {
            sb.append("  <div class=\"fm-field fm-draft\">\u270E Draft</div>\n");
        }
        if (!fm.getUuid().isBlank()) {
            sb.append("  <div class=\"fm-field\"><span class=\"fm-label\">UUID: </span>")
              .append("<code>").append(escapeHtml(fm.getUuid())).append("</code></div>\n");
        }
        if (!fm.getAuthors().isEmpty()) {
            sb.append("  <div class=\"fm-field\"><span class=\"fm-label\">Author: </span>")
              .append(escapeHtml(fm.getAuthorsAsString())).append("</div>\n");
        }
        if (!fm.getCreatedAt().isBlank()) {
            sb.append("  <div class=\"fm-field\"><span class=\"fm-label\">Date: </span>")
              .append(escapeHtml(fm.getCreatedAt())).append("</div>\n");
        }
        if (!fm.getTags().isEmpty()) {
            sb.append("  <div class=\"fm-field\"><span class=\"fm-label\">Tags: </span>");
            for (String tag : fm.getTags()) {
                sb.append("<span class=\"fm-tag\">").append(escapeHtml(tag)).append("</span>");
            }
            sb.append("</div>\n");
        }
        if (!fm.getSummary().isBlank()) {
            sb.append("  <div class=\"fm-field\"><em>").append(escapeHtml(fm.getSummary())).append("</em></div>\n");
        }
        if (!fm.getLinks().isEmpty()) {
            sb.append("  <div class=\"fm-field\"><span class=\"fm-label\">Links: </span>");
            for (String link : fm.getLinks()) {
                String title = resolveUuidTitle(link);
                if (title != null && !title.isBlank()) {
                    sb.append("<a class=\"fm-link\" href=\"marknote-link:").append(escapeHtml(link))
                      .append("\">").append(escapeHtml(title)).append("</a> ");
                } else {
                    sb.append("<a class=\"fm-link\" href=\"marknote-link:").append(escapeHtml(link))
                      .append("\">").append(escapeHtml(link)).append("</a> ");
                }
            }
            sb.append("</div>\n");
        }
        sb.append("</details>\n");
        return sb.toString();
    }

    /**
     * R\u00e9sout le titre d'un document \u00e0 partir de son UUID en cherchant dans le r\u00e9pertoire de base.
     */
    private String resolveUuidTitle(String uuid) {
        File file = findFileByUuid(uuid);
        if (file == null) return null;
        return extractFrontMatterField(file, "title");
    }

    /**
     * Recherche r\u00e9cursivement un fichier .md dont le front matter contient l'UUID donn\u00e9.
     */
    private File findFileByUuid(String uuid) {
        if (baseDirectory == null || !baseDirectory.isDirectory() || uuid == null) return null;
        return searchFileByUuid(baseDirectory, uuid);
    }

    private File searchFileByUuid(File dir, String uuid) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) {
                File found = searchFileByUuid(child, uuid);
                if (found != null) return found;
            } else if (child.getName().toLowerCase().endsWith(".md")) {
                String fileUuid = extractFrontMatterField(child, "uuid");
                if (uuid.equals(fileUuid)) return child;
            }
        }
        return null;
    }

    /**
     * Lit le front matter d'un fichier et retourne la valeur d'un champ donn\u00e9.
     */
    private String extractFrontMatterField(File file, String field) {
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(lines.get(i)).append('\n');
                if (i > 0 && lines.get(i).trim().equals("---")) break;
            }
            FrontMatter fm = FrontMatter.parse(sb.toString());
            if (fm == null) return null;
            return switch (field) {
                case "uuid" -> fm.getUuid();
                case "title" -> fm.getTitle();
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /** Échappe les caractères spéciaux HTML. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Efface le contenu de la prévisualisation.
     */
    public void clear() {
        webView.getEngine().loadContent("");
    }

    /**
     * Retourne le WebView pour des configurations supplémentaires.
     *
     * @return Le WebView
     */
    public WebView getWebView() {
        return webView;
    }

    /**
     * Définit le callback appelé quand un rendu PlantUML local démarre ({@code true})
     * ou se termine ({@code false}).
     */
    public void setOnPlantUmlRenderingChanged(Consumer<Boolean> callback) {
        this.onPlantUmlRenderingChanged = callback;
    }

    /**
     * Définit la configuration de l'application (utilisée pour PlantUML local).
     *
     * @param appConfig La configuration de l'application
     */
    public void setAppConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Définit le répertoire de base pour résoudre les chemins relatifs (images, etc.).
     *
     * @param directory Le répertoire de base du projet
     */
    public void setBaseDirectory(File directory) {
        this.baseDirectory = directory;
    }

    /**
     * Retourne le répertoire de base actuel.
     *
     * @return Le répertoire de base ou null
     */
    public File getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * Définit le callback appelé lors d'un clic sur un lien vers un fichier .md.
     *
     * @param callback Le callback recevant le fichier .md
     */
    public void setOnMarkdownLinkClick(Consumer<File> callback) {
        this.onMarkdownLinkClick = callback;
    }

    /**
     * Met à jour le thème de coloration syntaxique utilisé dans la preview.
     * La preview est automatiquement rafraîchie.
     *
     * @param appTheme nom du thème applicatif courant (ex. "dark", "solarized-light")
     */
    public void applySyntaxTheme(String appTheme) {
        this.syntaxTheme = ThemeManager.getInstance().getSyntaxTheme(appTheme);
        refresh();
    }
    
    /**
     * Navigue vers la page précédente dans l'historique.
     */
    private void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            navigating = true;
            updatePreview(history.get(historyIndex), false);
            navigating = false;
            updateNavigationButtons();
        }
    }
    
    /**
     * Navigue vers la page suivante dans l'historique.
     */
    private void navigateForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            navigating = true;
            updatePreview(history.get(historyIndex), false);
            navigating = false;
            updateNavigationButtons();
        }
    }
    
    /**
     * Rafraîchit la prévisualisation actuelle.
     */
    public void refresh() {
        if (!currentMarkdown.isEmpty()) {
            updatePreview(currentMarkdown, false);
        }
    }

    /**
     * Fait défiler la prévisualisation jusqu'à la position correspondant à la
     * fraction normalisée donnée (0.0 = haut, 1.0 = bas).
     *
     * @param fraction La fraction de défilement dans [0.0, 1.0]
     */
    public void scrollToFraction(double fraction) {
        if (webView.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED) {
            webView.getEngine().executeScript(
                "window.scrollTo(0, (document.body.scrollHeight - window.innerHeight) * " + fraction + ")"
            );
        }
    }
    
    /**
     * Définit le fichier Markdown source actuellement affiché.
     * Utilisé pour nommer les fichiers exportés.
     *
     * @param file Le fichier source, ou null pour un document non sauvegardé
     */
    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    /**
     * Exporte le contenu courant de la preview vers un PDF via la boîte de dialogue d'impression.
     * Sur Linux, l'utilisateur peut choisir "Imprimer dans un fichier" pour obtenir un PDF.
     */
    private void exportToPdf() {
        if (currentHtml.isEmpty()) return;

        // createPrinterJob() sans argument n'utilise que l'imprimante par défaut
        // et retourne null si aucune n'est configurée (fréquent sur Linux).
        // On tente un repli sur n'importe quelle imprimante disponible (incl. virtuelles/PDF).
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Printer fallback = Printer.getAllPrinters()
                    .stream().findFirst().orElse(null);
            if (fallback != null) {
                job = PrinterJob.createPrinterJob(fallback);
            }
        }
        if (job == null) {
            showExportAlert(Alert.AlertType.ERROR,
                    getMessages().getString("preview.export.error.title"),
                    getMessages().getString("preview.export.error.noprinter"));
            return;
        }
        if (job.showPrintDialog(getScene().getWindow())) {
            webView.getEngine().print(job);
            job.endJob();
        }
    }

    /**
     * Exporte le contenu courant en une archive ZIP contenant la page HTML
     * et toutes les images locales référencées dans le document.
     * La structure du ZIP est : &lt;name&gt;.html + images/&lt;filename&gt;
     */
    private void exportToHtmlZip() {
        if (currentHtml.isEmpty()) return;

        // Déterminer le nom de base du fichier exporté
        String baseName = (currentFile != null)
                ? currentFile.getName().replaceFirst("\\.[^.]+$", "")
                : "export";

        // Choisir la destination
        FileChooser fc = new FileChooser();
        fc.setTitle(getMessages().getString("preview.export.chooser.html.zip"));
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP Archive", "*.zip"));
        fc.setInitialFileName(baseName + ".zip");
        if (baseDirectory != null && baseDirectory.isDirectory()) {
            fc.setInitialDirectory(baseDirectory);
        }

        File dest = fc.showSaveDialog(getScene().getWindow());
        if (dest == null) return;

        // Traitement HTML : enlever la balise <base>, réécrire les chemins d'images locales
        String html = currentHtml.replaceAll("<base\\s+href=\"[^\"]*\">", "");

        List<File> localImages = new ArrayList<>();
        Map<String, String> pathMap = new HashMap<>();
        Set<String> seen = new HashSet<>();

        Pattern imgPat = Pattern.compile("<img\\s[^>]*src=\"([^\"]+)\"");
        Matcher m = imgPat.matcher(html);
        while (m.find()) {
            String src = m.group(1);
            if (src.startsWith("http://") || src.startsWith("https://")
                    || src.startsWith("data:") || seen.contains(src)) {
                continue;
            }
            seen.add(src);

            File imgFile = null;
            try {
                if (src.startsWith("file:")) {
                    imgFile = new File(new URI(src));
                } else if (baseDirectory != null) {
                    imgFile = new File(baseDirectory, src);
                }
            } catch (Exception ignored) { }

            if (imgFile != null && imgFile.exists() && imgFile.isFile()) {
                localImages.add(imgFile);
                pathMap.put(src, "images/" + imgFile.getName());
            }
        }

        // Remplacer les chemins dans le HTML
        for (Map.Entry<String, String> entry : pathMap.entrySet()) {
            html = html.replace("src=\"" + entry.getKey() + "\"",
                               "src=\"" + entry.getValue() + "\"");
        }

        final String finalHtml = html;
        final List<File> finalImages = new ArrayList<>(localImages);
        final String finalBaseName = baseName;

        Thread t = new Thread(() -> {
            try (ZipOutputStream zos = new ZipOutputStream(
                    new FileOutputStream(dest))) {

                // Page HTML
                zos.putNextEntry(new ZipEntry(finalBaseName + ".html"));
                zos.write(finalHtml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Images locales
                for (File img : finalImages) {
                    zos.putNextEntry(new ZipEntry("images/" + img.getName()));
                    Files.copy(img.toPath(), zos);
                    zos.closeEntry();
                }

                Platform.runLater(() -> showExportAlert(Alert.AlertType.INFORMATION,
                        getMessages().getString("preview.export.success.title"),
                        getMessages().getString("preview.export.success.html")));

            } catch (Exception e) {
                Platform.runLater(() -> showExportAlert(Alert.AlertType.ERROR,
                        getMessages().getString("preview.export.error.title"),
                        MessageFormat.format(
                                getMessages().getString("preview.export.error.message"),
                                e.getMessage())));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** Affiche une boîte de dialogue simple pour les résultats d'export. */
    private void showExportAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * Met à jour l'état des boutons de navigation.
     */
    private void updateNavigationButtons() {
        prevButton.setDisable(historyIndex <= 0);
        nextButton.setDisable(historyIndex >= history.size() - 1);
    }
}
