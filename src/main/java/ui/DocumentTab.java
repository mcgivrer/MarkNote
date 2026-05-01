package ui;

import java.io.File;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
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

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico", "tiff", "tif"
    );

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
    private VirtualizedScrollPane<StyleClassedTextArea> scrollPane;
    private SearchReplaceBar searchReplaceBar;
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

        // Parser le front matter s'il existe
        FrontMatter fm = FrontMatter.parse(content);
        String bodyContent = fm != null ? FrontMatter.stripFrontMatter(content) : content;

        // Panneau Front Matter (repliable)
        frontMatterPanel = new FrontMatterPanel();
        if (fm != null) {
            // Générer un UUID si le document existant n'en possède pas
            if (fm.getUuid().isBlank()) {
                fm.generateUuid();
            }
            frontMatterPanel.setFrontMatter(fm);
            frontMatterPanel.setExpanded(true);
        } else {
            // Nouveau document sans front matter : déplier le panneau,
            // pré-remplir le titre depuis le nom de fichier si disponible
            frontMatterPanel.initDefaults();
            if (file != null) {
                String baseName = file.getName();
                int dotIdx = baseName.lastIndexOf('.');
                String nameWithoutExt = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
                frontMatterPanel.setTitle(nameWithoutExt.replace('-', ' ').replace('_', ' '));
            }
            frontMatterPanel.setExpanded(true);
            frontMatterPanel.focusTitle();
        }

        editor = new StyleClassedTextArea();
        editor.setWrapText(true);
        editor.replaceText(bodyContent);
        editor.moveTo(0);
        javafx.application.Platform.runLater(editor::requestFollowCaret);
        editor.getStyleClass().add("markdown-editor");
        // Aligner savedContent sur la forme canonique produite par getFullContent()
        // (front matter re-sérialisé + corps) pour éviter les faux positifs de isModified().
        this.savedContent = getFullContent();
        
        // Charger le CSS pour la coloration syntaxique
        String cssPath = getClass().getResource("/css/markdown-editor.css").toExternalForm();
        editor.getStylesheets().add(cssPath);
        
        // Appliquer la coloration syntaxique initiale
        applyHighlighting();
        
        // Coloration syntaxique avec délai pour éviter trop de recalculs
        editor.multiPlainChanges()
              .successionEnds(Duration.ofMillis(100))
              .subscribe(ignore -> applyHighlighting());

        // Drag & drop : insertion de liens markdown depuis l'explorateur
        setupEditorDragAndDrop();

        // Menu contextuel de l'éditeur
        editor.setContextMenu(createEditorContextMenu());

        // Barre d'outils flottante sur sélection
        Map<String, Runnable> toolbarActions = Map.ofEntries(
                Map.entry("bold",   () -> toggleWrap("**")),
                Map.entry("italic", () -> toggleWrap("*")),
                Map.entry("link",   this::insertLinkSyntax),
                Map.entry("image",  this::insertImageSyntax),
                Map.entry("code",   this::insertCodeBlock),
                Map.entry("h1", () -> applyHeading(1)),
                Map.entry("h2", () -> applyHeading(2)),
                Map.entry("h3", () -> applyHeading(3)),
                Map.entry("h4", () -> applyHeading(4)),
                Map.entry("h5", () -> applyHeading(5)),
                Map.entry("h6", () -> applyHeading(6))
        );
        EditorFloatingToolbar floatingToolbar = new EditorFloatingToolbar(editor, toolbarActions);

        editor.selectionProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel.getLength() > 0) {
                editor.getCharacterBoundsOnScreen(newSel.getStart(), newSel.getEnd())
                      .ifPresent(bounds -> floatingToolbar.show(
                              bounds.getMinX() + bounds.getWidth() / 2.0,
                              bounds.getMinY() - 8
                      ));
            } else {
                floatingToolbar.hide();
            }
        });

        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) floatingToolbar.hide();
        });

        // Raccourcis clavier de l'éditeur
        editor.setOnKeyPressed(e -> {
            if (!e.isControlDown()) return;
            switch (e.getCode()) {
                case F -> { searchReplaceBar.showSearchOnly();      e.consume(); }
                case H -> { searchReplaceBar.showSearchAndReplace(); e.consume(); }
                case B -> { toggleWrap("**");      e.consume(); }
                case I -> { toggleWrap("*");       e.consume(); }
                case K -> { insertLinkSyntax();    e.consume(); }
                case J -> { insertImageSyntax();   e.consume(); }
                case E -> { insertCodeBlock();     e.consume(); }
                case DIGIT1 -> { applyHeading(1); e.consume(); }
                case DIGIT2 -> { applyHeading(2); e.consume(); }
                case DIGIT3 -> { applyHeading(3); e.consume(); }
                case DIGIT4 -> { applyHeading(4); e.consume(); }
                case DIGIT5 -> { applyHeading(5); e.consume(); }
                case DIGIT6 -> { applyHeading(6); e.consume(); }
                default -> {}
            }
        });

        scrollPane = new VirtualizedScrollPane<>(editor);

        // Barre de recherche/remplacement (overlay)
        searchReplaceBar = new SearchReplaceBar();
        searchReplaceBar.setEditor(editor);
        searchReplaceBar.setOnClearHighlights(this::applyHighlighting);

        // La barre flotte au-dessus de l'éditeur uniquement (pas du FrontMatter).
        // StackPane enveloppe uniquement scrollPane + barre → la barre apparaît
        // juste en-dessous du panneau FrontMatter.
        javafx.scene.layout.StackPane editorStack = new javafx.scene.layout.StackPane(scrollPane, searchReplaceBar);
        javafx.scene.layout.StackPane.setAlignment(searchReplaceBar, javafx.geometry.Pos.TOP_RIGHT);

        javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(frontMatterPanel, editorStack);
        javafx.scene.layout.VBox.setVgrow(editorStack, javafx.scene.layout.Priority.ALWAYS);

        // Le CSS doit être sur contentBox (ancêtre de SearchReplaceBar) pour que
        // les règles .search-replace-bar soient visibles par le composant.
        contentBox.getStylesheets().add(cssPath);

        setContent(contentBox);

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
     * Public pour permettre à la SearchReplaceBar de réappliquer le highlighting
     * après suppression des surbrillances de recherche.
     */
    public void applyHighlighting() {
        editor.setStyleSpans(0, computeHighlighting(editor.getText()));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Markdown formatting helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Applique ou bascule un niveau de titre Markdown sur la ligne courante.
     *
     * @param level niveau 1–6
     */
    void applyHeading(int level) {
        int paraIdx = editor.getCurrentParagraph();
        String line = editor.getParagraph(paraIdx).getText();
        String newLine = MarkdownFormatter.applyHeading(line, level);
        int start = editor.getAbsolutePosition(paraIdx, 0);
        int end = start + line.length();
        editor.replaceText(start, end, newLine);
    }

    /**
     * Entoure ou retire le marqueur Markdown inline sur le texte sélectionné.
     *
     * @param marker le délimiteur ({@code "**"}, {@code "*"}, etc.)
     */
    void toggleWrap(String marker) {
        IndexRange sel = editor.getSelection();
        if (sel.getLength() == 0) return;
        String selected = editor.getSelectedText();
        String result = MarkdownFormatter.toggleWrap(selected, marker);
        int start = sel.getStart();
        editor.replaceText(start, sel.getEnd(), result);
        editor.selectRange(start, start + result.length());
    }

    /**
     * Insère la syntaxe d'un lien Markdown en utilisant le texte sélectionné
     * comme URL. Le curseur est positionné entre les crochets {@code []}.
     */
    void insertLinkSyntax() {
        IndexRange sel = editor.getSelection();
        if (sel.getLength() == 0) return;
        String url = editor.getSelectedText();
        int start = sel.getStart();
        String inserted = MarkdownFormatter.buildLink(url);
        editor.replaceText(start, sel.getEnd(), inserted);
        editor.moveTo(start + MarkdownFormatter.linkCaretOffset());
    }

    /**
     * Insère la syntaxe d'une image Markdown en utilisant le texte sélectionné
     * comme chemin. Le curseur est positionné entre les crochets {@code []}.
     */
    void insertImageSyntax() {
        IndexRange sel = editor.getSelection();
        if (sel.getLength() == 0) return;
        String url = editor.getSelectedText();
        int start = sel.getStart();
        String inserted = MarkdownFormatter.buildImage(url);
        editor.replaceText(start, sel.getEnd(), inserted);
        editor.moveTo(start + MarkdownFormatter.imageCaretOffset());
    }

    /**
     * Insère un bloc de code Markdown fencé ({@code ```}).
     *
     * <p>Si du texte est sélectionné, il est encadré par les marqueurs.
     * Sinon, un bloc vide est inséré et le curseur se positionne
     * entre les deux clôtures pour saisie immédiate.</p>
     */
    void insertCodeBlock() {
        IndexRange sel = editor.getSelection();
        if (sel.getLength() > 0) {
            String selected = editor.getSelectedText();
            int start = sel.getStart();
            String block = MarkdownFormatter.buildCodeBlock(selected);
            editor.replaceText(start, sel.getEnd(), block);
            editor.moveTo(start + block.length());
        } else {
            int pos = editor.getCaretPosition();
            String block = MarkdownFormatter.buildCodeBlock("");
            editor.replaceText(pos, pos, block);
            editor.moveTo(pos + MarkdownFormatter.codeBlockCaretOffset());
        }
    }

    /**
     * Crée le menu contextuel de l'éditeur Markdown.
     */
    private ContextMenu createEditorContextMenu() {
        ResourceBundle msg = getMessages();

        MenuItem copyItem    = new MenuItem(msg.getString("editor.menu.copy"));
        MenuItem cutItem     = new MenuItem(msg.getString("editor.menu.cut"));
        MenuItem pasteItem   = new MenuItem(msg.getString("editor.menu.paste"));
        copyItem.setOnAction(e  -> editor.copy());
        cutItem.setOnAction(e   -> editor.cut());
        pasteItem.setOnAction(e -> editor.paste());

        MenuItem[] headingItems = new MenuItem[6];
        for (int i = 1; i <= 6; i++) {
            final int level = i;
            headingItems[i - 1] = new MenuItem(
                    MessageFormat.format(msg.getString("editor.menu.heading"), level));
            headingItems[i - 1].setOnAction(e -> applyHeading(level));
        }

        MenuItem boldItem         = new MenuItem(msg.getString("editor.menu.bold"));
        MenuItem italicItem       = new MenuItem(msg.getString("editor.menu.italic"));
        MenuItem insertLinkItem   = new MenuItem(msg.getString("editor.menu.insertLink"));
        MenuItem insertImageItem  = new MenuItem(msg.getString("editor.menu.insertImage"));
        MenuItem insertCodeItem   = new MenuItem(msg.getString("editor.menu.insertCode"));
        boldItem.setOnAction(e         -> toggleWrap("**"));
        italicItem.setOnAction(e       -> toggleWrap("*"));
        insertLinkItem.setOnAction(e   -> insertLinkSyntax());
        insertImageItem.setOnAction(e  -> insertImageSyntax());
        insertCodeItem.setOnAction(e   -> insertCodeBlock());

        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
                copyItem, cutItem, pasteItem,
                new SeparatorMenuItem(),
                headingItems[0], headingItems[1], headingItems[2],
                headingItems[3], headingItems[4], headingItems[5],
                new SeparatorMenuItem(),
                boldItem, italicItem,
                new SeparatorMenuItem(),
                insertLinkItem, insertImageItem, insertCodeItem
        );

        menu.setOnShowing(e -> {
            boolean hasSel = editor.getSelection().getLength() > 0;
            copyItem.setDisable(!hasSel);
            cutItem.setDisable(!hasSel);
            boldItem.setDisable(!hasSel);
            italicItem.setDisable(!hasSel);
            insertLinkItem.setDisable(!hasSel);
            insertImageItem.setDisable(!hasSel);
        });

        return menu;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Drag & drop
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Configure le drag & drop depuis l'explorateur vers l'éditeur.
     * Images → ![alt](path), Documents → [title](path)
     */
    private void setupEditorDragAndDrop() {
        editor.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY, TransferMode.LINK);
            }
            event.consume();
        });

        editor.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                var hit = editor.hit(event.getX(), event.getY());
                int insertPos = hit.getInsertionIndex();

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < db.getFiles().size(); i++) {
                    File droppedFile = db.getFiles().get(i);
                    if (i > 0) sb.append("\n");
                    String relativePath = computeRelativePath(droppedFile);
                    if (isImageFile(droppedFile)) {
                        String altText = getFileNameWithoutExtension(droppedFile);
                        sb.append("![").append(altText).append("](")
                           .append(relativePath).append(")");
                    } else {
                        String title = extractDroppedFileTitle(droppedFile);
                        sb.append("[").append(title).append("](")
                           .append(relativePath).append(")");
                    }
                }

                editor.insertText(insertPos, sb.toString());
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    /**
     * Calcule le chemin relatif d'un fichier par rapport au document courant.
     */
    private String computeRelativePath(File droppedFile) {
        File baseDir = (this.file != null) ? this.file.getParentFile() : null;
        if (baseDir != null) {
            try {
                String relative = baseDir.toPath().relativize(droppedFile.toPath())
                        .toString().replace('\\', '/');
                return relative.replace(" ", "%20");
            } catch (IllegalArgumentException ignored) {
            }
        }
        return droppedFile.getAbsolutePath().replace('\\', '/').replace(" ", "%20");
    }

    /**
     * Vérifie si un fichier est une image.
     */
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) return false;
        return IMAGE_EXTENSIONS.contains(name.substring(dotIndex + 1));
    }

    /**
     * Retourne le nom du fichier sans extension.
     */
    private String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    /**
     * Extrait le titre d'un fichier pour le lien Markdown.
     * Pour les fichiers .md, utilise le titre du front matter s'il existe.
     */
    private String extractDroppedFileTitle(File file) {
        if (file.getName().toLowerCase().endsWith(".md")) {
            Optional<String> content = DocumentService.readFile(file);
            if (content.isPresent()) {
                FrontMatter fm = FrontMatter.parse(content.get());
                if (fm != null && !fm.getTitle().isBlank()) {
                    return fm.getTitle();
                }
            }
        }
        return getFileNameWithoutExtension(file);
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
     * Insère du texte à la ligne suivant la position courante du curseur.
     * Si l'éditeur n'a pas de focus, le texte est ajouté à la fin du document.
     *
     * @param text Le texte à insérer
     */
    public void insertAtNextLine(String text) {
        int caretPos = editor.getCaretPosition();
        String content = editor.getText();
        // Trouver la fin de la ligne courante
        int lineEnd = content.indexOf('\n', caretPos);
        int insertPos;
        String toInsert;
        if (lineEnd == -1) {
            // Curseur sur la dernière ligne : ajouter après
            insertPos = content.length();
            toInsert = (content.isEmpty() || content.endsWith("\n")) ? text : "\n" + text;
        } else {
            insertPos = lineEnd + 1;
            toInsert = text.endsWith("\n") ? text : text + "\n";
        }
        editor.insertText(insertPos, toInsert);
        editor.moveTo(insertPos + toInsert.length());
        editor.requestFocus();
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
     * Enregistre un listener appelé à chaque changement de position de défilement
     * vertical dans l'éditeur. La valeur transmise est une fraction normalisée
     * dans [0.0, 1.0] (0 = haut, 1 = bas).
     *
     * @param listener Le callback recevant la fraction de défilement
     */
    public void setOnScrollFractionChanged(Consumer<Double> listener) {
        scrollPane.estimatedScrollYProperty().addListener((obs, oldY, newY) -> {
            double totalHeight = editor.totalHeightEstimateProperty().getValue();
            double viewportHeight = scrollPane.getHeight();
            double scrollable = Math.max(1.0, totalHeight - viewportHeight);
            double fraction = Math.min(1.0, Math.max(0.0, newY.doubleValue() / scrollable));
            listener.accept(fraction);
        });
    }

    /**
     * Retourne la position de défilement verticale actuelle de l'éditeur
     * sous forme de fraction normalisée dans [0.0, 1.0] (0 = haut, 1 = bas).
     *
     * @return La fraction de défilement actuelle
     */
    public double getScrollFraction() {
        double scrollY = scrollPane.getEstimatedScrollY();
        double totalHeight = editor.totalHeightEstimateProperty().getValue();
        double viewportHeight = scrollPane.getHeight();
        double scrollable = Math.max(1.0, totalHeight - viewportHeight);
        return Math.min(1.0, Math.max(0.0, scrollY / scrollable));
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

            // Parser le front matter
            FrontMatter fm = FrontMatter.parse(content.get());
            if (fm != null) {
                // Générer un UUID si le document n'en possède pas
                if (fm.getUuid().isBlank()) {
                    fm.generateUuid();
                }
                frontMatterPanel.setFrontMatter(fm);
                frontMatterPanel.setExpanded(true);
                editor.replaceText(FrontMatter.stripFrontMatter(content.get()));
                editor.moveTo(0);
                javafx.application.Platform.runLater(editor::requestFollowCaret);
            } else {
                frontMatterPanel.clear();
                frontMatterPanel.initDefaults();
                // Pré-remplir le titre depuis le nom de fichier
                String baseName = file.getName();
                int dotIdx = baseName.lastIndexOf('.');
                String nameWithoutExt = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
                frontMatterPanel.setTitle(nameWithoutExt.replace('-', ' ').replace('_', ' '));
                frontMatterPanel.setExpanded(true);
                frontMatterPanel.focusTitle();
                editor.replaceText(content.get());
                editor.moveTo(0);
                javafx.application.Platform.runLater(editor::requestFollowCaret);
            }

            // Aligner savedContent sur la forme canonique pour éviter les faux positifs
            this.savedContent = getFullContent();
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
                Stage stage = (tabPane != null && tabPane.getScene() != null)
                        ? (Stage) tabPane.getScene().getWindow() : null;
                if (save(stage, false, file != null ? file.getParentFile() : null)) {
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

    /**
     * Ouvre la barre en mode "Recherche uniquement" (Ctrl+F).
     */
    public void openSearch() {
        searchReplaceBar.showSearchOnly();
    }

    /**
     * Ouvre la barre en mode "Recherche et Remplacement" (Ctrl+H).
     */
    public void openReplace() {
        searchReplaceBar.showSearchAndReplace();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
