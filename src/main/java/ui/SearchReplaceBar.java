package ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.fxmisc.richtext.StyleClassedTextArea;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Barre de recherche et remplacement flottante.
 * S'affiche en superposition sur le DocumentTab via un StackPane.
 * Visible uniquement lors d'une opération de recherche/remplacement.
 */
public class SearchReplaceBar extends VBox {

    private static final PseudoClass ERROR_PSEUDO = PseudoClass.getPseudoClass("error");

    private final TextField searchField;
    private final TextField replaceField;
    private final ToggleButton regexBtn;
    private final ToggleButton fullWordBtn;
    private final ToggleButton caseBtn;
    private final Label matchLabel;
    private final HBox replaceRow;

    /** Éditeur cible */
    private StyleClassedTextArea editor;

    /** Callback pour réappliquer la coloration syntaxique après suppression des surbrillances */
    private Runnable onClearHighlights;

    /** Liste des positions de correspondances [start, end] */
    private final List<int[]> matches = new ArrayList<>();
    private int currentMatchIndex = -1;

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    public SearchReplaceBar() {
        getStyleClass().add("search-replace-bar");

        // ── Ligne 1 : recherche ─────────────────────────────────────────────
        searchField = new TextField();
        searchField.setPromptText(getMessages().getString("searchbar.field.prompt"));
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        regexBtn = buildToggle(".*", "searchbar.regex.tooltip");
        fullWordBtn = buildToggle("\\b", "searchbar.fullword.tooltip");
        caseBtn = buildToggle("Aa", "searchbar.case.tooltip");

        Button prevBtn = buildButton("▲", "searchbar.prev.tooltip");
        prevBtn.setOnAction(e -> navigatePrev());

        Button nextBtn = buildButton("▼", "searchbar.next.tooltip");
        nextBtn.setOnAction(e -> navigateNext());

        matchLabel = new Label();
        matchLabel.getStyleClass().add("search-match-label");
        matchLabel.setMinWidth(60);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("search-close-btn");
        closeBtn.setOnAction(e -> hide());

        HBox searchRow = new HBox(6,
                searchField, regexBtn, fullWordBtn, caseBtn,
                prevBtn, nextBtn, matchLabel, spacer, closeBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setPadding(new Insets(5, 8, 3, 8));

        // ── Ligne 2 : remplacement ──────────────────────────────────────────
        replaceField = new TextField();
        replaceField.setPromptText(getMessages().getString("searchbar.replace.prompt"));
        replaceField.getStyleClass().add("search-field");
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        Button replaceBtn = new Button(getMessages().getString("searchbar.replace.btn"));
        replaceBtn.getStyleClass().add("search-action-btn");
        replaceBtn.setOnAction(e -> replaceCurrent());

        Button replaceAllBtn = new Button(getMessages().getString("searchbar.replace.all.btn"));
        replaceAllBtn.getStyleClass().add("search-action-btn");
        replaceAllBtn.setOnAction(e -> replaceAll());

        replaceRow = new HBox(6, replaceField, replaceBtn, replaceAllBtn);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.setPadding(new Insets(3, 8, 5, 8));

        getChildren().addAll(searchRow, replaceRow);

        // Caché par défaut
        setVisible(false);
        setManaged(false);

        // ── Listeners ───────────────────────────────────────────────────────
        searchField.textProperty().addListener((obs, o, n) -> performSearch());
        regexBtn.selectedProperty().addListener((obs, o, n) -> performSearch());
        fullWordBtn.selectedProperty().addListener((obs, o, n) -> performSearch());
        caseBtn.selectedProperty().addListener((obs, o, n) -> performSearch());

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)        { hide(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER && e.isShiftDown()) { navigatePrev(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER)    { navigateNext(); e.consume(); }
        });
        replaceField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) { hide(); e.consume(); }
        });
    }

    // ── API publique ────────────────────────────────────────────────────────

    /** Affecte l'éditeur cible. */
    public void setEditor(StyleClassedTextArea editor) {
        this.editor = editor;
    }

    /** Callback à appeler pour réappliquer la coloration syntaxique. */
    public void setOnClearHighlights(Runnable callback) {
        this.onClearHighlights = callback;
    }

    /** Affiche la barre en mode "Recherche uniquement" (sans la ligne Remplacer). */
    public void showSearchOnly() {
        replaceRow.setVisible(false);
        replaceRow.setManaged(false);
        show();
    }

    /** Affiche la barre avec les deux lignes Recherche + Remplacer. */
    public void showSearchAndReplace() {
        replaceRow.setVisible(true);
        replaceRow.setManaged(true);
        show();
    }

    /** Ferme la barre et supprime les surbrillances. */
    public void hide() {
        setVisible(false);
        setManaged(false);
        clearHighlights();
        matches.clear();
        currentMatchIndex = -1;
        matchLabel.setText("");
        searchField.pseudoClassStateChanged(ERROR_PSEUDO, false);
        if (editor != null) editor.requestFocus();
    }

    // ── Logique interne ─────────────────────────────────────────────────────

    private void show() {
        setVisible(true);
        setManaged(true);
        searchField.requestFocus();
        searchField.selectAll();
        performSearch();
    }

    private void performSearch() {
        clearHighlights();
        matches.clear();
        currentMatchIndex = -1;
        matchLabel.setText("");
        searchField.pseudoClassStateChanged(ERROR_PSEUDO, false);

        if (editor == null) return;

        String query = searchField.getText();
        if (query == null || query.isBlank()) return;

        String text = editor.getText();
        Pattern pattern;
        try {
            String regex = regexBtn.isSelected() ? query : Pattern.quote(query);
            if (fullWordBtn.isSelected()) regex = "\\b" + regex + "\\b";
            int flags = caseBtn.isSelected() ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(regex, flags);
        } catch (PatternSyntaxException ex) {
            searchField.pseudoClassStateChanged(ERROR_PSEUDO, true);
            matchLabel.setText("⚠");
            return;
        }

        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(new int[]{ matcher.start(), matcher.end() });
        }

        // Surligner toutes les occurrences
        for (int[] range : matches) {
            editor.setStyle(range[0], range[1], Collections.singleton("search-highlight"));
        }

        if (!matches.isEmpty()) {
            currentMatchIndex = 0;
            highlightCurrent();
            updateMatchLabel();
        } else {
            matchLabel.setText(getMessages().getString("searchbar.no.match"));
        }
    }

    private void navigateNext() {
        if (matches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + 1) % matches.size();
        highlightCurrent();
        updateMatchLabel();
    }

    private void navigatePrev() {
        if (matches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex - 1 + matches.size()) % matches.size();
        highlightCurrent();
        updateMatchLabel();
    }

    /** Sélectionne l'occurrence courante et défile l'éditeur. */
    private void highlightCurrent() {
        int[] range = matches.get(currentMatchIndex);
        // Remettre toutes les occurrences en "search-highlight"
        for (int i = 0; i < matches.size(); i++) {
            int[] r = matches.get(i);
            String cls = (i == currentMatchIndex) ? "search-highlight-current" : "search-highlight";
            editor.setStyle(r[0], r[1], Collections.singleton(cls));
        }
        editor.selectRange(range[0], range[1]);
        editor.requestFollowCaret();
    }

    private void replaceCurrent() {
        if (matches.isEmpty() || currentMatchIndex < 0) return;
        int[] range = matches.get(currentMatchIndex);
        editor.replaceText(range[0], range[1], replaceField.getText());
        performSearch();
    }

    private void replaceAll() {
        if (matches.isEmpty()) return;
        // Remplacer de la fin vers le début pour préserver les indices
        List<int[]> reversed = new ArrayList<>(matches);
        Collections.reverse(reversed);
        for (int[] range : reversed) {
            editor.replaceText(range[0], range[1], replaceField.getText());
        }
        performSearch();
    }

    /**
     * Supprime les surbrillances de recherche et réapplique la coloration syntaxique.
     */
    private void clearHighlights() {
        if (editor == null || editor.getLength() == 0) return;
        editor.clearStyle(0, editor.getLength());
        if (onClearHighlights != null) onClearHighlights.run();
    }

    // ── Helpers UI ──────────────────────────────────────────────────────────

    private ToggleButton buildToggle(String text, String tooltipKey) {
        ToggleButton btn = new ToggleButton(text);
        btn.setTooltip(new Tooltip(getMessages().getString(tooltipKey)));
        btn.getStyleClass().add("search-toggle-btn");
        return btn;
    }

    private Button buildButton(String text, String tooltipKey) {
        Button btn = new Button(text);
        btn.setTooltip(new Tooltip(getMessages().getString(tooltipKey)));
        btn.getStyleClass().add("search-nav-btn");
        return btn;
    }
}
