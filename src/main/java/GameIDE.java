import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

public class GameIDE extends Application {

    private Parser markdownParser;
    private HtmlRenderer htmlRenderer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // Flexmark init
        markdownParser = Parser.builder().build();
        htmlRenderer = HtmlRenderer.builder().build();

        BorderPane root = new BorderPane();

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Preview globale à droite
        WebView preview = new WebView();

        // SplitPane : onglets à gauche, preview à droite
        SplitPane splitPane = new SplitPane(tabPane, preview);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.5);

        root.setCenter(splitPane);

        // Menu / toolbar minimal pour créer un nouvel onglet
        Button newTabButton = new Button("Nouvel onglet");
        newTabButton.setOnAction(e -> addMarkdownTab(tabPane, preview));
        ToolBar toolBar = new ToolBar(newTabButton);
        root.setTop(toolBar);

        // Premier onglet
        addMarkdownTab(tabPane, preview);

        Scene scene = new Scene(root, 1000, 600);
        stage.setTitle("Éditeur Markdown JavaFX");
        stage.setScene(scene);
        stage.show();
    }

    private void addMarkdownTab(TabPane tabPane, WebView preview) {
        Tab tab = new Tab("Doc " + (tabPane.getTabs().size() + 1));

        TextArea editor = new TextArea("# Nouveau document\n\nTape du *Markdown* ici.");
        editor.setWrapText(true);

        tab.setContent(editor);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        // Listener : met à jour la preview quand le texte change (onglet actif)
        ChangeListener<String> textListener = (obs, oldText, newText) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                updatePreview(preview, newText);
            }
        };
        editor.textProperty().addListener(textListener);

        // Quand on change d’onglet, on rafraîchit la preview avec le texte de l’onglet courant
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() instanceof TextArea ta) {
                updatePreview(preview, ta.getText());
            }
        });

        // Init preview
        updatePreview(preview, editor.getText());
    }

    private void updatePreview(WebView preview, String markdown) {
        String html = htmlRenderer.render(markdownParser.parse(markdown));
        String htmlPage = """
                <html>
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    pre { background: #f0f0f0; padding: 0.5em; }
                    code { font-family: monospace; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(html);
        preview.getEngine().loadContent(htmlPage);
    }
}
