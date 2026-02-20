package ui;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

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

    public PreviewPanel() {
        super("preview.title", "preview.close.tooltip");

        // Initialiser Flexmark
        markdownParser = Parser.builder().build();
        htmlRenderer = HtmlRenderer.builder().build();

        // Créer le WebView
        webView = new WebView();
        
        // Ajouter les boutons de navigation dans le header
        prevButton = new Button("\u00AB");
        prevButton.getStyleClass().add("panel-nav-button");
        prevButton.setTooltip(new Tooltip(messages.getString("preview.prev.tooltip")));
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> navigateBack());
        
        nextButton = new Button("\u00BB");
        nextButton.getStyleClass().add("panel-nav-button");
        nextButton.setTooltip(new Tooltip(messages.getString("preview.next.tooltip")));
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> navigateForward());
        
        Button refreshButton = new Button("\u21BB");
        refreshButton.getStyleClass().add("panel-nav-button");
        refreshButton.setTooltip(new Tooltip(messages.getString("preview.refresh.tooltip")));
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

    /**
     * Définit le callback appelé lors d'un clic sur un lien vers un fichier .md.
     *
     * @param callback Le callback recevant le fichier .md
     */
    public void setOnMarkdownLinkClick(Consumer<File> callback) {
        this.onMarkdownLinkClick = callback;
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
