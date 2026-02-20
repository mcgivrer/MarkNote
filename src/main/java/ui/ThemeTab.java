package ui;

import java.io.File;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.flowless.VirtualizedScrollPane;

import utils.DocumentService;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Représente un onglet d'édition de thème CSS.
 * Gère l'édition, la sauvegarde et la coloration syntaxique CSS.
 */
public class ThemeTab extends Tab {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private static final int MAX_TAB_NAME_LENGTH = 15;

    // Patterns pour la coloration syntaxique CSS
    private static final String COMMENT_PATTERN = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";
    private static final String SELECTOR_PATTERN = "(?m)^[\\s]*[.#]?[a-zA-Z][a-zA-Z0-9_-]*(?:\\s*[>,+~]\\s*[.#]?[a-zA-Z][a-zA-Z0-9_-]*)*(?=\\s*\\{)";
    private static final String PROPERTY_PATTERN = "(?<=\\{|;)[\\s]*-?[a-zA-Z-]+(?=\\s*:)";
    private static final String VALUE_PATTERN = "(?<=:)[^;{}]+(?=;|\\})";
    private static final String COLOR_HEX_PATTERN = "#[a-fA-F0-9]{3,8}\\b";
    private static final String NUMBER_PATTERN = "\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|pt|vh|vw|deg|s|ms)?\\b";
    private static final String STRING_PATTERN = "\"[^\"]*\"|'[^']*'";
    private static final String BRACE_PATTERN = "[{}]";
    private static final String PSEUDO_PATTERN = ":[a-zA-Z-]+(?:\\([^)]*\\))?";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>" + COMMENT_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<COLORHEX>" + COLOR_HEX_PATTERN + ")"
            + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
            + "|(?<PSEUDO>" + PSEUDO_PATTERN + ")"
            + "|(?<SELECTOR>" + SELECTOR_PATTERN + ")"
            + "|(?<PROPERTY>" + PROPERTY_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")",
            Pattern.MULTILINE
    );

    private final StyleClassedTextArea editor;
    private File file;
    private String savedContent;
    private Consumer<Void> onSaveCallback;

    /**
     * Crée un nouvel onglet d'édition de thème CSS.
     *
     * @param file    Le fichier CSS à éditer
     * @param content Le contenu initial
     */
    public ThemeTab(File file, String content) {
        super(truncateTabName(file.getName()));
        setTooltip(new Tooltip(file.getAbsolutePath()));

        this.file = file;
        this.savedContent = content;

        editor = new StyleClassedTextArea();
        editor.setWrapText(false);
        editor.replaceText(content);
        editor.getStyleClass().add("css-editor");
        
        // Charger le CSS pour la coloration syntaxique
        var cssResource = getClass().getResource("/css/css-editor.css");
        if (cssResource != null) {
            editor.getStylesheets().add(cssResource.toExternalForm());
        }
        
        // Appliquer la coloration syntaxique initiale
        applyHighlighting();
        
        // Coloration syntaxique avec délai pour éviter trop de recalculs
        editor.multiPlainChanges()
              .successionEnds(Duration.ofMillis(100))
              .subscribe(ignore -> applyHighlighting());
        
        VirtualizedScrollPane<StyleClassedTextArea> scrollPane = new VirtualizedScrollPane<>(editor);
        setContent(scrollPane);

        // Listener pour détecter les modifications
        editor.textProperty().addListener((obs, oldText, newText) -> {
            updateTabTitle();
        });

        // Confirmation avant fermeture si modifié
        setOnCloseRequest(e -> {
            if (isModified()) {
                e.consume();
                handleCloseConfirmation();
            }
        });
    }

    /**
     * Applique la coloration syntaxique au texte.
     */
    private void applyHighlighting() {
        editor.setStyleSpans(0, computeHighlighting(editor.getText()));
    }

    /**
     * Calcule les styles à appliquer au texte CSS.
     */
    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("COMMENT") != null ? "css-comment" :
                    matcher.group("STRING") != null ? "css-string" :
                    matcher.group("COLORHEX") != null ? "css-color" :
                    matcher.group("NUMBER") != null ? "css-number" :
                    matcher.group("PSEUDO") != null ? "css-pseudo" :
                    matcher.group("SELECTOR") != null ? "css-selector" :
                    matcher.group("PROPERTY") != null ? "css-property" :
                    matcher.group("BRACE") != null ? "css-brace" :
                    null;
            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastKwEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Vérifie si le document a été modifié depuis la dernière sauvegarde.
     *
     * @return true si le document est modifié
     */
    public boolean isModified() {
        return savedContent == null || !savedContent.equals(editor.getText());
    }

    /**
     * Retourne le contenu actuel du document.
     *
     * @return Le contenu du document
     */
    public String getTextContent() {
        return editor.getText();
    }

    /**
     * Retourne le fichier associé au document.
     *
     * @return Le fichier
     */
    public File getFile() {
        return file;
    }

    /**
     * Définit le callback appelé après une sauvegarde réussie.
     */
    public void setOnSaveCallback(Consumer<Void> callback) {
        this.onSaveCallback = callback;
    }

    /**
     * Sauvegarde le thème CSS.
     *
     * @return true si la sauvegarde a réussi
     */
    public boolean save() {
        if (file == null) {
            return false;
        }

        if (DocumentService.writeFile(file, editor.getText())) {
            this.savedContent = editor.getText();
            updateTabTitle();
            if (onSaveCallback != null) {
                onSaveCallback.accept(null);
            }
            return true;
        } else {
            showError(getMessages().getString("error.save.title"), file.getAbsolutePath());
            return false;
        }
    }

    /**
     * Met à jour le titre de l'onglet avec l'indicateur de modification.
     */
    private void updateTabTitle() {
        String baseName = file.getName();
        String truncated = truncateTabName(baseName);
        if (isModified()) {
            if (!getText().startsWith("*")) {
                setText("*" + truncated);
            }
        } else {
            setText(truncated);
        }
        setTooltip(new Tooltip(file.getAbsolutePath()));
    }

    /**
     * Gère la confirmation de fermeture d'un document modifié.
     */
    private void handleCloseConfirmation() {
        String docName = getText().replaceFirst("^\\*", "");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(getMessages().getString("modified.title"));
        alert.setHeaderText(MessageFormat.format(getMessages().getString("modified.header"), docName));
        alert.setContentText(getMessages().getString("modified.content"));

        ButtonType saveBtn = new ButtonType(getMessages().getString("modified.save"));
        ButtonType discardBtn = new ButtonType(getMessages().getString("modified.discard"));
        ButtonType cancelBtn = new ButtonType(getMessages().getString("modified.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == saveBtn) {
                if (save()) {
                    closeTab();
                }
            } else if (result.get() == discardBtn) {
                closeTab();
            }
            // Annuler : ne rien faire
        }
    }

    /**
     * Force la fermeture de l'onglet sans confirmation.
     */
    public void closeTab() {
        TabPane tabPane = getTabPane();
        if (tabPane != null) {
            tabPane.getTabs().remove(this);
        }
    }

    /**
     * Marque le contenu actuel comme sauvegardé.
     */
    public void markAsSaved() {
        this.savedContent = editor.getText();
        updateTabTitle();
    }

    private static String truncateTabName(String name) {
        if (name.length() <= MAX_TAB_NAME_LENGTH) return name;
        return name.substring(0, MAX_TAB_NAME_LENGTH - 1) + "\u2026";
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
