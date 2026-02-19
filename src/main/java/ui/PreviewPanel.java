package ui;

import java.io.File;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

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

    public PreviewPanel() {
        super("preview.title", "preview.close.tooltip");

        // Initialiser Flexmark
        markdownParser = Parser.builder().build();
        htmlRenderer = HtmlRenderer.builder().build();

        // Créer le WebView
        webView = new WebView();
        setContent(webView);
    }

    /**
     * Met à jour la prévisualisation avec le contenu Markdown donné.
     *
     * @param markdown Le contenu Markdown à afficher
     */
    public void updatePreview(String markdown) {
        if (markdown == null) {
            markdown = "";
        }
        String html = htmlRenderer.render(markdownParser.parse(markdown));
        
        // Construire le tag base si un répertoire de base est défini
        String baseTag = "";
        if (baseDirectory != null && baseDirectory.exists()) {
            String baseUrl = baseDirectory.toURI().toString();
            baseTag = "<base href=\"" + baseUrl + "\">";
        }
        
        String htmlPage = """
                <html>
                <head>
                  <meta charset="UTF-8">
                  %s
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    pre { background: #f0f0f0; padding: 0.5em; }
                    code { font-family: monospace; }
                    img { max-width: 100%%; height: auto; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(baseTag, html);
        webView.getEngine().loadContent(htmlPage);
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
}
