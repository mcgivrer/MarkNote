package ui;

import java.util.Map;

import org.fxmisc.richtext.StyleClassedTextArea;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;

/**
 * Barre d'outils flottante qui apparaît au-dessus de la sélection dans
 * l'éditeur Markdown.
 *
 * <p>Les boutons déclenchent les actions de formatage transmises via la map
 * {@code actions} et masquent automatiquement la barre après exécution.</p>
 */
public class EditorFloatingToolbar extends Popup {

    private final StyleClassedTextArea editor;

    /**
     * Crée la barre d'outils flottante.
     *
     * @param editor  l'éditeur cible (utilisé pour obtenir la fenêtre propriétaire)
     * @param actions map action-clé → Runnable ; clés attendues :
     *                {@code bold}, {@code italic}, {@code link}, {@code image},
     *                {@code code}, {@code h1}…{@code h6}
     */
    public EditorFloatingToolbar(StyleClassedTextArea editor, Map<String, Runnable> actions) {
        this.editor = editor;
        setAutoHide(true);
        setHideOnEscape(true);

        HBox root = new HBox();
        root.getStyleClass().add("editor-floating-toolbar");
        // Le Popup possède sa propre scène : le CSS doit être chargé explicitement.
        root.getStylesheets().add(
                EditorFloatingToolbar.class.getResource("/css/markdown-editor.css").toExternalForm());

        Button boldBtn   = makeButton("B",    actions, "bold");
        Button italicBtn = makeButton("I",    actions, "italic");
        Button linkBtn   = makeButton("Lien", actions, "link");
        Button imageBtn  = makeButton("Img",  actions, "image");
        Button codeBtn   = makeButton("</>",  actions, "code");
        Button h1Btn     = makeButton("H1",   actions, "h1");
        Button h2Btn     = makeButton("H2",   actions, "h2");
        Button h3Btn     = makeButton("H3",   actions, "h3");

        // H4–H6 regroupés dans un MenuButton
        MenuItem h4Item = new MenuItem("H4");
        h4Item.setOnAction(e -> { actions.get("h4").run(); hide(); });
        MenuItem h5Item = new MenuItem("H5");
        h5Item.setOnAction(e -> { actions.get("h5").run(); hide(); });
        MenuItem h6Item = new MenuItem("H6");
        h6Item.setOnAction(e -> { actions.get("h6").run(); hide(); });
        MenuButton moreBtn = new MenuButton("H4\u25be", null, h4Item, h5Item, h6Item);
        moreBtn.getStyleClass().add("button");

        root.getChildren().addAll(boldBtn, italicBtn, linkBtn, imageBtn, codeBtn,
                                  h1Btn, h2Btn, h3Btn, moreBtn);
        getContent().add(root);
    }

    /**
     * Affiche la barre d'outils centrée horizontalement et positionnée
     * juste au-dessus du point ({@code anchorX}, {@code anchorY}).
     *
     * @param anchorX abscisse écran du centre de la sélection
     * @param anchorY ordonnée écran du bord supérieur de la sélection (moins la marge)
     */
    public void show(double anchorX, double anchorY) {
        if (editor.getScene() == null || editor.getScene().getWindow() == null) return;
        super.show(editor.getScene().getWindow(), anchorX, anchorY);
        // Ajustement après layout pour centrer horizontalement et positionner au-dessus
        Platform.runLater(() -> {
            setX(anchorX - getWidth() / 2.0);
            setY(anchorY - getHeight());
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Button makeButton(String label, Map<String, Runnable> actions, String key) {
        Button btn = new Button(label);
        btn.setOnAction(e -> { actions.get(key).run(); hide(); });
        return btn;
    }
}
