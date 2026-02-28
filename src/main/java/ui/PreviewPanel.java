package ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // ── Checkboxes : convertir [ ] et [x] en éléments checkbox HTML
        html = processCheckboxes(html);

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

        String htmlPage = """
                <html>
                <head>
                  <meta charset="UTF-8">
                  %s
                  <link rel="stylesheet"
                        href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/%s.min.css">
                  <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                  <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
                  <link rel="stylesheet"
                        href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
                  <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    pre { background: %s; padding: 0.8em; border-radius: 6px; overflow-x: auto; }
                    pre code { font-family: 'Source Code Pro', 'Fira Code', 'Consolas', monospace;
                               font-size: 0.9em; color: %s; }
                    code { font-family: monospace; }
                    img { max-width: 100%%%%; height: auto; }
                    /* PlantUML diagrams */
                    .plantuml-diagram { text-align: center; margin: 1em 0; }
                    .plantuml-diagram img { max-width: 100%%%%; height: auto; }
                    /* Mermaid diagrams */
                    .mermaid { text-align: center; margin: 1em 0; }
                    /* Tables */
                    table { border-collapse: collapse; width: auto; margin: 1em 0; }
                    th, td { border: 1px solid #888; padding: 6px 12px; text-align: left; }
                    th { background: rgba(128,128,128,0.15); font-weight: bold; }
                    tr:nth-child(even) { background: rgba(128,128,128,0.06); }
                    /* Checkboxes (task list) */
                    input[type="checkbox"] { width: 1.1em; height: 1.1em; margin-right: 0.4em; 
                                             vertical-align: middle; cursor: default; 
                                             accent-color: #0078d7; }
                    li:has(input[type="checkbox"]) { list-style: none; margin-left: -1.2em; }
                    /* Front Matter metadata */
                    .front-matter { background: rgba(128,128,128,0.08); border: 1px solid rgba(128,128,128,0.25);
                                    border-radius: 6px; padding: 0.6em 1em; margin-bottom: 1.2em;
                                    font-size: 0.9em; color: #555; }
                    .front-matter h1 { font-size: 1.4em; margin: 0 0 0.3em 0; color: #333; }
                    .front-matter .fm-field { margin: 0.15em 0; }
                    .front-matter .fm-label { font-weight: bold; }
                    .front-matter .fm-tag { display: inline-block; background: rgba(0,120,215,0.12);
                                            border-radius: 3px; padding: 1px 6px; margin: 1px 2px;
                                            font-size: 0.85em; }
                    .front-matter .fm-draft { color: #d9534f; font-weight: bold; }
                    .front-matter .fm-link { display: inline-block; background: rgba(0,120,215,0.12);
                                             border-radius: 3px; padding: 1px 6px; margin: 1px 2px;
                                             font-size: 0.85em; text-decoration: none; color: #0078d7; }
                    .front-matter .fm-link:hover { text-decoration: underline; background: rgba(0,120,215,0.2); }
                    .front-matter .fm-summary { cursor: pointer; font-weight: bold; font-size: 0.9em;
                                                color: #666; padding: 0.2em 0; }
                    .front-matter .fm-summary:hover { color: #333; }
                    /* Copy button on code blocks */
                    pre { position: relative; }
                    pre .copy-btn { position: absolute; top: 4px; right: 4px; padding: 2px 8px;
                                    font-size: 0.75em; cursor: pointer; background: rgba(128,128,128,0.2);
                                    border: 1px solid rgba(128,128,128,0.3); border-radius: 4px;
                                    color: inherit; opacity: 0; transition: opacity 0.2s; }
                    pre:hover .copy-btn { opacity: 1; }
                    pre .copy-btn:hover { background: rgba(128,128,128,0.35); }
                    pre .copy-btn.copied { background: rgba(76,175,80,0.3); border-color: rgba(76,175,80,0.5); }
                  </style>
                </head>
                <body>%s%s
                <script>
                  // highlight.js
                  hljs.highlightAll();
                  // Mermaid : transformer les blocs <pre><code class="language-mermaid">
                  // en <div class="mermaid"> puis initialiser.
                  document.querySelectorAll('pre code.language-mermaid').forEach(function(block) {
                    var pre = block.parentElement;
                    var div = document.createElement('div');
                    div.className = 'mermaid';
                    div.textContent = block.textContent;
                    pre.parentNode.replaceChild(div, pre);
                  });
                  mermaid.initialize({ startOnLoad: true, theme: '%s' });
                  // KaTeX : rendre les expressions mathématiques
                  // Bloc $$...$$ puis inline $...$
                  (function() {
                    function renderMath(el) {
                      var html = el.innerHTML;
                      // Bloc : $$...$$
                      html = html.replace(/\\$\\$([\\s\\S]+?)\\$\\$/g, function(m, tex) {
                        try {
                          return katex.renderToString(tex.trim(), { displayMode: true, throwOnError: false });
                        } catch(e) { return m; }
                      });
                      // Inline : $...$  (pas précédé de \\, pas suivi de chiffre)
                      html = html.replace(/(?<!\\\\)\\$([^\\$\\n]+?)\\$/g, function(m, tex) {
                        try {
                          return katex.renderToString(tex.trim(), { displayMode: false, throwOnError: false });
                        } catch(e) { return m; }
                      });
                      el.innerHTML = html;
                    }
                    renderMath(document.body);
                  })();
                  // Copy buttons on code blocks
                  document.querySelectorAll('pre > code').forEach(function(codeEl) {
                    var pre = codeEl.parentElement;
                    if (pre.querySelector('.copy-btn')) return;
                    var btn = document.createElement('button');
                    btn.className = 'copy-btn';
                    btn.textContent = 'Copy';
                    btn.addEventListener('click', function() {
                      var text = codeEl.textContent;
                      if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(text).then(function() {
                          btn.textContent = '\u2713 Copied';
                          btn.classList.add('copied');
                          setTimeout(function() { btn.textContent = 'Copy'; btn.classList.remove('copied'); }, 1500);
                        });
                      } else {
                        var ta = document.createElement('textarea');
                        ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
                        document.body.appendChild(ta); ta.select();
                        document.execCommand('copy'); document.body.removeChild(ta);
                        btn.textContent = '\u2713 Copied';
                        btn.classList.add('copied');
                        setTimeout(function() { btn.textContent = 'Copy'; btn.classList.remove('copied'); }, 1500);
                      }
                    });
                    pre.appendChild(btn);
                  });
                </script>
                </body>
                </html>
                """.formatted(baseTag, hljsStyle, preBg, codeFg, frontMatterHtml, html, mermaidTheme);
        webView.getEngine().loadContent(htmlPage);
        this.currentHtml = htmlPage;
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
     * Chaque thread génère le SVG via le jar local, puis l'injecte dans la page
     * via {@code executeScript}. Quand tous les blocs sont rendus, le callback
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
            Thread t = new Thread(() -> {
                String svg = renderWithLocalJar(puml, jarPath);
                Platform.runLater(() -> {
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
                });
            });
            t.setDaemon(true);
            t.start();
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
