package ui;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import config.ThemeManager;
import config.ThemeManager.SyntaxTheme;
import utils.FrontMatter;
import utils.PlantUmlEncoder;

import javafx.concurrent.Worker;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;

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
    
    // Historique de navigation
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String currentMarkdown = "";
    private boolean navigating = false;
    
    private final Button prevButton;
    private final Button nextButton;

    /** Thème highlight.js courant, synchronisé avec le thème applicatif. */
    private SyntaxTheme syntaxTheme = new SyntaxTheme("github", "#f6f8fa", "#24292e");

    /** Pattern pour détecter les blocs PlantUML dans le HTML généré par Flexmark. */
    private static final Pattern PLANTUML_BLOCK = Pattern.compile(
            "<pre><code\\s+class=\"language-plantuml\">(.*?)</code></pre>",
            Pattern.DOTALL);

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
        
        // Insérer les boutons avant le bouton de fermeture
        HBox header = getHeader();
        int closeIndex = header.getChildren().indexOf(getCloseButton());
        header.getChildren().add(closeIndex, prevButton);
        header.getChildren().add(closeIndex + 1, nextButton);
        header.getChildren().add(closeIndex + 2, refreshButton);
        
        // Intercepter les clics sur les liens
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
                    }
                }
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
        
        String html = htmlRenderer.render(markdownParser.parse(body));

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
                </script>
                </body>
                </html>
                """.formatted(baseTag, hljsStyle, preBg, codeFg, frontMatterHtml, html, mermaidTheme);
        webView.getEngine().loadContent(htmlPage);
    }

    /**
     * Remplace les blocs {@code <pre><code class="language-plantuml">...}
     * par des balises {@code <img>} pointant vers le serveur PlantUML en ligne.
     * Le texte est décodé des entités HTML avant l'encodage.
     */
    private String processPlantUmlBlocks(String html) {
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
            // Si le texte ne commence pas par @startuml, l'ajouter
            if (!puml.startsWith("@start")) {
                puml = "@startuml\n" + puml + "\n@enduml";
            }
            String url = PlantUmlEncoder.toSvgUrl(puml);
            m.appendReplacement(sb,
                    Matcher.quoteReplacement(
                            "<div class=\"plantuml-diagram\"><img src=\"" + url + "\" alt=\"PlantUML diagram\"></div>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Génère le HTML pour afficher les métadonnées Front Matter sous forme
     * de bloc stylisé en tête de page.
     */
    private String renderFrontMatterHtml(FrontMatter fm) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"front-matter\">\n");
        if (!fm.getTitle().isBlank()) {
            sb.append("  <h1>").append(escapeHtml(fm.getTitle())).append("</h1>\n");
        }
        if (fm.isDraft()) {
            sb.append("  <div class=\"fm-field fm-draft\">\u270E Draft</div>\n");
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
        sb.append("</div>\n");
        return sb.toString();
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
    private void refresh() {
        if (!currentMarkdown.isEmpty()) {
            updatePreview(currentMarkdown, false);
        }
    }
    
    /**
     * Met à jour l'état des boutons de navigation.
     */
    private void updateNavigationButtons() {
        prevButton.setDisable(historyIndex <= 0);
        nextButton.setDisable(historyIndex >= history.size() - 1);
    }
}
