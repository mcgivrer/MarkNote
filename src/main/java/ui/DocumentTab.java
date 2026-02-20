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
import utils.FrontMatter;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Représente un onglet de document Markdown.
 * Gère l'édition, la sauvegarde et la détection des modifications.
 */
public class DocumentTab extends Tab {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private static final int MAX_TAB_NAME_LENGTH = 15;

    // Patterns pour la coloration syntaxique Markdown
    private static final String HEADING_PATTERN = "^#{1,6}\\s.*$";
    private static final String BOLD_PATTERN = "(\\*\\*|__).+?(\\*\\*|__)";
    private static final String ITALIC_PATTERN = "(\\*|_)(?!\\*|_).+?(\\*|_)";
    private static final String CODE_INLINE_PATTERN = "`[^`\\n]+`";
    private static final String CODE_BLOCK_PATTERN = "```[\\s\\S]*?```";
    private static final String LINK_PATTERN = "\\[([^\\[\\]]*?)\\]\\(([^\\(\\)]*?)\\)";
    private static final String IMAGE_PATTERN = "!\\[([^\\[\\]]*?)\\]\\(([^\\(\\)]*?)\\)";
    private static final String BLOCKQUOTE_PATTERN = "^>\\s.*$";
    private static final String LIST_PATTERN = "^\\s*[-*+]\\s.*$|^\\s*\\d+\\.\\s.*$";
    private static final String HORIZONTAL_RULE_PATTERN = "^([-*_]\\s*){3,}$";
    private static final String STRIKETHROUGH_PATTERN = "~~.+?~~";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<CODEBLOCK>" + CODE_BLOCK_PATTERN + ")"
            + "|(?<HEADING>" + HEADING_PATTERN + ")"
            + "|(?<BOLD>" + BOLD_PATTERN + ")"
            + "|(?<ITALIC>" + ITALIC_PATTERN + ")"
            + "|(?<CODEINLINE>" + CODE_INLINE_PATTERN + ")"
            + "|(?<IMAGE>" + IMAGE_PATTERN + ")"
            + "|(?<LINK>" + LINK_PATTERN + ")"
            + "|(?<BLOCKQUOTE>" + BLOCKQUOTE_PATTERN + ")"
            + "|(?<LIST>" + LIST_PATTERN + ")"
            + "|(?<HORIZONTALRULE>" + HORIZONTAL_RULE_PATTERN + ")"
            + "|(?<STRIKETHROUGH>" + STRIKETHROUGH_PATTERN + ")",
            Pattern.MULTILINE
    );

    private final StyleClassedTextArea editor;
    private final FrontMatterPanel frontMatterPanel;
    private File file;
    private String savedContent;
    private Consumer<String> onTextChanged;

    /**
     * Crée un nouvel onglet de document.
     *
     * @param title   Le titre de l'onglet
     * @param content Le contenu initial
     * @param file    Le fichier associé (peut être null pour un nouveau document)
     */
    public DocumentTab(String title, String content, File file) {
        super(truncateTabName(title));
        setTooltip(new Tooltip(title));

        this.file = file;
        this.savedContent = content;

        // Parser le front matter s'il existe
        FrontMatter fm = FrontMatter.parse(content);
        String bodyContent = fm != null ? FrontMatter.stripFrontMatter(content) : content;

        // Panneau Front Matter (repliable)
        frontMatterPanel = new FrontMatterPanel();
        if (fm != null) {
            frontMatterPanel.setFrontMatter(fm);
            frontMatterPanel.setExpanded(true);
        } else {
            frontMatterPanel.setExpanded(false);
        }

        editor = new StyleClassedTextArea();
        editor.setWrapText(true);
        editor.replaceText(bodyContent);
        editor.getStyleClass().add("markdown-editor");
        
        // Charger le CSS pour la coloration syntaxique
        String cssPath = getClass().getResource("/css/markdown-editor.css").toExternalForm();
        editor.getStylesheets().add(cssPath);
        
        // Appliquer la coloration syntaxique initiale
        applyHighlighting();
        
        // Coloration syntaxique avec délai pour éviter trop de recalculs
        editor.multiPlainChanges()
              .successionEnds(Duration.ofMillis(100))
              .subscribe(ignore -> applyHighlighting());
        
        VirtualizedScrollPane<StyleClassedTextArea> scrollPane = new VirtualizedScrollPane<>(editor);

        // Layout : front matter panel au-dessus de l'éditeur
        javafx.scene.layout.VBox editorBox = new javafx.scene.layout.VBox(frontMatterPanel, scrollPane);
        javafx.scene.layout.VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
        setContent(editorBox);

        // Listener pour détecter les modifications
        editor.textProperty().addListener((obs, oldText, newText) -> {
            updateTabTitle();
            if (onTextChanged != null) {
                onTextChanged.accept(getFullContent());
            }
        });

        // Listener pour les modifications dans le front matter
        frontMatterPanel.setOnChanged(() -> {
            updateTabTitle();
            if (onTextChanged != null) {
                onTextChanged.accept(getFullContent());
            }
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
     * Calcule les styles à appliquer au texte Markdown.
     */
    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("CODEBLOCK") != null ? "code-block" :
                    matcher.group("HEADING") != null ? "heading" :
                    matcher.group("BOLD") != null ? "bold" :
                    matcher.group("ITALIC") != null ? "italic" :
                    matcher.group("CODEINLINE") != null ? "code-inline" :
                    matcher.group("IMAGE") != null ? "image" :
                    matcher.group("LINK") != null ? "link" :
                    matcher.group("BLOCKQUOTE") != null ? "blockquote" :
                    matcher.group("LIST") != null ? "list" :
                    matcher.group("HORIZONTALRULE") != null ? "horizontal-rule" :
                    matcher.group("STRIKETHROUGH") != null ? "strikethrough" :
                    null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Crée un nouvel onglet avec le contenu par défaut.
     *
     * @param tabNumber Le numéro de l'onglet (pour le titre)
     */
    public DocumentTab(int tabNumber) {
        this(MessageFormat.format(getMessages().getString("newdoc.title"), tabNumber),
             getMessages().getString("newdoc.content"),
             null);
    }

    /**
     * Vérifie si le document a été modifié depuis la dernière sauvegarde.
     *
     * @return true si le document est modifié
     */
    public boolean isModified() {
        return savedContent == null || !savedContent.equals(getFullContent());
    }

    /**
     * Retourne le contenu complet du document (front matter + corps).
     *
     * @return Le contenu complet
     */
    public String getFullContent() {
        FrontMatter fm = frontMatterPanel.getFrontMatter();
        String body = editor.getText();
        if (fm != null && !fm.isEmpty()) {
            return fm.serialize() + body;
        }
        return body;
    }

    /**
     * Retourne le contenu actuel du document (corps uniquement, sans front matter).
     *
     * @return Le contenu du document
     */
    public String getTextContent() {
        return editor.getText();
    }

    /**
     * Retourne le fichier associé au document.
     *
     * @return Le fichier (peut être null)
     */
    public File getFile() {
        return file;
    }

    /**
     * Définit le fichier associé au document.
     *
     * @param file Le fichier
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Retourne l'éditeur de texte.
     *
     * @return Le StyleClassedTextArea
     */
    public StyleClassedTextArea getEditor() {
        return editor;
    }

    /**
     * Retourne le panneau d'édition des métadonnées Front Matter.
     *
     * @return Le FrontMatterPanel
     */
    public FrontMatterPanel getFrontMatterPanel() {
        return frontMatterPanel;
    }

    /**
     * Définit l'action à exécuter quand le texte change.
     *
     * @param action L'action (reçoit le nouveau texte)
     */
    public void setOnTextChanged(Consumer<String> action) {
        this.onTextChanged = action;
    }

    /**
     * Sauvegarde le document.
     *
     * @param stage       Le stage parent (pour le FileChooser)
     * @param forceChoose Forcer la sélection d'un fichier (Sauvegarder sous...)
     * @param projectDir  Le répertoire de projet par défaut
     * @return true si la sauvegarde a réussi
     */
    public boolean save(Stage stage, boolean forceChoose, File projectDir) {
        File targetFile = file;

        if (targetFile == null || forceChoose) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(getMessages().getString("chooser.saveFile"));
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(getMessages().getString("chooser.filter.markdown"), "*.md", "*.markdown"),
                    new FileChooser.ExtensionFilter(getMessages().getString("chooser.filter.text"), "*.txt"),
                    new FileChooser.ExtensionFilter(getMessages().getString("chooser.filter.all"), "*.*"));
            
            if (targetFile != null) {
                chooser.setInitialDirectory(targetFile.getParentFile());
                chooser.setInitialFileName(targetFile.getName());
            } else if (projectDir != null && projectDir.exists()) {
                chooser.setInitialDirectory(projectDir);
            }
            targetFile = chooser.showSaveDialog(stage);
        }

        if (targetFile == null) {
            return false;
        }

        if (DocumentService.writeFile(targetFile, getFullContent())) {
            this.file = targetFile;
            this.savedContent = getFullContent();
            setText(truncateTabName(targetFile.getName()));
            setTooltip(new Tooltip(targetFile.getName()));
            return true;
        } else {
            showError(getMessages().getString("error.save.title"), targetFile.getAbsolutePath());
            return false;
        }
    }

    /**
     * Charge un fichier dans le document.
     *
     * @param file Le fichier à charger
     * @return true si le chargement a réussi
     */
    public boolean loadFrom(File file) {
        Optional<String> content = DocumentService.readFile(file);
        if (content.isPresent()) {
            this.file = file;
            this.savedContent = content.get();

            // Parser le front matter
            FrontMatter fm = FrontMatter.parse(content.get());
            if (fm != null) {
                frontMatterPanel.setFrontMatter(fm);
                frontMatterPanel.setExpanded(true);
                editor.replaceText(FrontMatter.stripFrontMatter(content.get()));
            } else {
                frontMatterPanel.clear();
                frontMatterPanel.setExpanded(false);
                editor.replaceText(content.get());
            }

            setText(truncateTabName(file.getName()));
            setTooltip(new Tooltip(file.getName()));
            return true;
        } else {
            showError(getMessages().getString("error.read.title"), file.getAbsolutePath());
            return false;
        }
    }

    /**
     * Met à jour le titre de l'onglet avec l'indicateur de modification.
     */
    private void updateTabTitle() {
        String baseName = file != null ? file.getName() : getText().replaceFirst("^\\*", "");
        String truncated = truncateTabName(baseName);
        if (isModified()) {
            if (!getText().startsWith("*")) {
                setText("*" + truncated);
            }
        } else {
            setText(truncated);
        }
        setTooltip(new Tooltip(baseName));
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
                TabPane tabPane = getTabPane();
                if (tabPane != null) {
                    tabPane.getSelectionModel().select(this);
                }
                // Note: La sauvegarde doit être gérée par l'appelant via un callback
                // Pour l'instant, on ferme si le document n'est plus modifié après sauvegarde
                if (!isModified()) {
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
        this.savedContent = getFullContent();
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
