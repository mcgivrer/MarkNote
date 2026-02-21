package ui;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import utils.DocumentService;
import utils.FrontMatter;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Panneau d'édition des attributs Front Matter d'un document Markdown.
 * <p>
 * Affiche un formulaire avec les champs : title, author, created_at, tags,
 * summary et draft. Le panneau est repliable (TitledPane).
 */
public class FrontMatterPanel extends TitledPane {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final TextField titleField;
    private final TextField authorField;
    private final TextField createdAtField;
    private final TextField tagsField;
    private final TextField summaryField;
    private final CheckBox draftCheck;
    private final TextField uuidField;
    private final ObservableList<String> linksList;
    private final VBox linksBox;
    private final TitledPane linksTitledPane;
    private final GridPane grid;
    private int knownFieldsEndRow;
    private final Map<String, TextField> extraFields = new LinkedHashMap<>();

    private Runnable onChanged;
    private java.util.function.Consumer<File> onLinkClick;
    private File projectDir;

    public FrontMatterPanel() {
        setText(getMessages().getString("frontmatter.title"));
        setCollapsible(true);
        setExpanded(true);
        getStyleClass().add("front-matter-panel");

        grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(8));

        // Contraintes de colonnes : label fixe, champ extensible
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(100);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        int row = 0;

        // Title
        Label titleLabel = new Label(getMessages().getString("frontmatter.field.title"));
        titleField = new TextField();
        titleField.setPromptText(getMessages().getString("frontmatter.field.title.prompt"));
        titleField.textProperty().addListener((o, ov, nv) -> fireChanged());
        grid.add(titleLabel, 0, row);
        grid.add(titleField, 1, row);
        row++;

        // Author
        Label authorLabel = new Label(getMessages().getString("frontmatter.field.author"));
        authorField = new TextField();
        authorField.setPromptText(getMessages().getString("frontmatter.field.author.prompt"));
        authorField.textProperty().addListener((o, ov, nv) -> fireChanged());
        grid.add(authorLabel, 0, row);
        grid.add(authorField, 1, row);
        row++;

        // Created at
        Label createdAtLabel = new Label(getMessages().getString("frontmatter.field.created_at"));
        createdAtField = new TextField();
        createdAtField.setPromptText("YYYY-MM-DD (HH:mm)");
        createdAtField.textProperty().addListener((o, ov, nv) -> fireChanged());
        grid.add(createdAtLabel, 0, row);
        grid.add(createdAtField, 1, row);
        row++;

        // Tags
        Label tagsLabel = new Label(getMessages().getString("frontmatter.field.tags"));
        tagsField = new TextField();
        tagsField.setPromptText(getMessages().getString("frontmatter.field.tags.prompt"));
        tagsField.textProperty().addListener((o, ov, nv) -> fireChanged());
        grid.add(tagsLabel, 0, row);
        grid.add(tagsField, 1, row);
        row++;

        // Summary
        Label summaryLabel = new Label(getMessages().getString("frontmatter.field.summary"));
        summaryField = new TextField();
        summaryField.setPromptText(getMessages().getString("frontmatter.field.summary.prompt"));
        summaryField.textProperty().addListener((o, ov, nv) -> fireChanged());
        grid.add(summaryLabel, 0, row);
        grid.add(summaryField, 1, row);
        row++;

        // Draft
        Label draftLabel = new Label(getMessages().getString("frontmatter.field.draft"));
        draftCheck = new CheckBox();
        draftCheck.setTooltip(new Tooltip(getMessages().getString("frontmatter.field.draft.tooltip")));
        draftCheck.selectedProperty().addListener((o, ov, nv) -> fireChanged());
        grid.add(draftLabel, 0, row);
        grid.add(draftCheck, 1, row);
        row++;

        // UUID (lecture seule)
        Label uuidLabel = new Label(getMessages().getString("frontmatter.field.uuid"));
        uuidField = new TextField();
        uuidField.setEditable(false);
        uuidField.setStyle("-fx-opacity: 0.7;");
        uuidField.setTooltip(new Tooltip(getMessages().getString("frontmatter.field.uuid.tooltip")));
        grid.add(uuidLabel, 0, row);
        grid.add(uuidField, 1, row);
        row++;

        // Marquer la fin des champs connus
        knownFieldsEndRow = row;

        // Links (zone repliable avec liste verticale d'UUIDs)
        linksList = FXCollections.observableArrayList();
        linksBox = new VBox(2);
        linksBox.setPadding(new Insets(4));
        Label linksPlaceholder = new Label(getMessages().getString("frontmatter.field.links.prompt"));
        linksPlaceholder.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
        linksBox.getChildren().add(linksPlaceholder);

        linksList.addListener((ListChangeListener<String>) change -> {
            rebuildLinksView();
            fireChanged();
        });

        linksTitledPane = new TitledPane(getMessages().getString("frontmatter.field.links"), linksBox);
        linksTitledPane.setCollapsible(true);
        linksTitledPane.setExpanded(false);
        linksTitledPane.setTooltip(new Tooltip(getMessages().getString("frontmatter.field.links.tooltip")));

        // Layout principal : grid + liens repliables
        VBox mainContent = new VBox(grid, linksTitledPane);
        setContent(mainContent);

        // ── Drag & Drop : accepter les fichiers .md pour créer des liens ──
        setupDragAndDrop();
    }

    /**
     * Configure le drag & drop sur le panneau pour accepter les fichiers .md
     * et ajouter leur UUID dans la liste des liens.
     */
    private void setupDragAndDrop() {
        setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                boolean hasMd = event.getDragboard().getFiles().stream()
                        .anyMatch(f -> f.getName().toLowerCase().endsWith(".md"));
                if (hasMd) {
                    event.acceptTransferModes(TransferMode.LINK);
                }
            }
            event.consume();
        });

        setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                boolean hasMd = event.getDragboard().getFiles().stream()
                        .anyMatch(f -> f.getName().toLowerCase().endsWith(".md"));
                if (hasMd) {
                    setStyle("-fx-border-color: #4a90d9; -fx-border-width: 2; -fx-border-style: dashed;");
                }
            }
        });

        setOnDragExited(event -> {
            setStyle("");
        });

        setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    if (file.getName().toLowerCase().endsWith(".md")) {
                        String linkedUuid = ensureUuidInFile(file);
                        if (linkedUuid != null) {
                            addLinkUuid(linkedUuid);
                            success = true;
                        }
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * S'assure qu'un fichier .md possède un UUID dans son front matter.
     * Si absent, en génère un et réécrit le fichier.
     *
     * @param file le fichier Markdown
     * @return l'UUID du fichier, ou null en cas d'erreur
     */
    private String ensureUuidInFile(File file) {
        Optional<String> contentOpt = DocumentService.readFile(file);
        if (contentOpt.isEmpty()) return null;

        String content = contentOpt.get();
        FrontMatter fm = FrontMatter.parse(content);

        if (fm != null && !fm.getUuid().isBlank()) {
            return fm.getUuid();
        }

        // Créer ou compléter le front matter avec un UUID
        if (fm == null) {
            fm = new FrontMatter();
        }
        fm.generateUuid();

        // Réécrire le fichier avec le front matter mis à jour
        String body = FrontMatter.stripFrontMatter(content);
        String newContent = fm.serialize() + body;
        DocumentService.writeFile(file, newContent);

        return fm.getUuid();
    }

    /**
     * Ajoute un UUID dans la liste des liens s'il n'y est pas déjà.
     *
     * @param uuid l'UUID à ajouter
     */
    private void addLinkUuid(String uuid) {
        if (uuid != null && !uuid.isBlank() && !linksList.contains(uuid)) {
            linksList.add(uuid);
            linksTitledPane.setExpanded(true);
        }
    }

    /**
     * Reconstruit l'affichage de la liste des liens.
     */
    private void rebuildLinksView() {
        linksBox.getChildren().clear();
        if (linksList.isEmpty()) {
            Label placeholder = new Label(getMessages().getString("frontmatter.field.links.prompt"));
            placeholder.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
            linksBox.getChildren().add(placeholder);
            linksTitledPane.setText(getMessages().getString("frontmatter.field.links"));
        } else {
            linksTitledPane.setText(getMessages().getString("frontmatter.field.links")
                    + " (" + linksList.size() + ")");
            for (String uuid : linksList) {
                HBox row = new HBox(4);
                row.setAlignment(Pos.CENTER_LEFT);

                // Lien cliquable vers le document cible
                Hyperlink linkLabel = new Hyperlink(uuid);
                linkLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
                HBox.setHgrow(linkLabel, Priority.ALWAYS);
                linkLabel.setMaxWidth(Double.MAX_VALUE);

                // Tooltip : titre du document cible
                String targetTitle = findTitleByUuid(uuid);
                if (targetTitle != null && !targetTitle.isBlank()) {
                    linkLabel.setTooltip(new Tooltip(targetTitle));
                }

                // Clic : ouvrir le document cible
                linkLabel.setOnAction(e -> {
                    File targetFile = findFileByUuid(uuid);
                    if (targetFile != null && onLinkClick != null) {
                        onLinkClick.accept(targetFile);
                    }
                });

                Button removeBtn = new Button("\u2715");
                removeBtn.setStyle("-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-min-width: 20; -fx-min-height: 18;");
                removeBtn.setTooltip(new Tooltip(getMessages().getString("frontmatter.field.links.remove")));
                removeBtn.setOnAction(e -> linksList.remove(uuid));

                row.getChildren().addAll(linkLabel, removeBtn);
                linksBox.getChildren().add(row);
            }
        }
    }

    /**
     * Recherche récursivement un fichier .md dont le front matter contient l'UUID donné.
     *
     * @param uuid l'UUID à rechercher
     * @return le fichier trouvé, ou null
     */
    private File findFileByUuid(String uuid) {
        if (projectDir == null || !projectDir.isDirectory() || uuid == null) return null;
        return searchFileByUuid(projectDir, uuid);
    }

    private File searchFileByUuid(File dir, String uuid) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) {
                File found = searchFileByUuid(child, uuid);
                if (found != null) return found;
            } else if (child.getName().toLowerCase().endsWith(".md")) {
                String fileUuid = extractUuidFromFile(child);
                if (uuid.equals(fileUuid)) return child;
            }
        }
        return null;
    }

    /**
     * Extrait le titre du front matter du document ayant l'UUID donné.
     */
    private String findTitleByUuid(String uuid) {
        File file = findFileByUuid(uuid);
        if (file == null) return null;
        return extractTitleFromFile(file);
    }

    /**
     * Lit le front matter d'un fichier et retourne son UUID.
     */
    private String extractUuidFromFile(File file) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(lines.get(i)).append('\n');
                if (i > 0 && lines.get(i).trim().equals("---")) break;
            }
            FrontMatter fm = FrontMatter.parse(sb.toString());
            return fm != null ? fm.getUuid() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lit le front matter d'un fichier et retourne son titre.
     */
    private String extractTitleFromFile(File file) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(lines.get(i)).append('\n');
                if (i > 0 && lines.get(i).trim().equals("---")) break;
            }
            FrontMatter fm = FrontMatter.parse(sb.toString());
            return fm != null ? fm.getTitle() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Remplit le panneau avec les données d'un objet FrontMatter.
     *
     * @param fm le front matter (peut être null pour un front matter vide)
     */
    public void setFrontMatter(FrontMatter fm) {
        if (fm == null) {
            clear();
            return;
        }
        uuidField.setText(fm.getUuid());
        titleField.setText(fm.getTitle());
        authorField.setText(fm.getAuthorsAsString());
        createdAtField.setText(fm.getCreatedAt());
        tagsField.setText(fm.getTagsAsString());
        summaryField.setText(fm.getSummary());
        draftCheck.setSelected(fm.isDraft());
        linksList.setAll(fm.getLinks());
        if (!fm.getLinks().isEmpty()) {
            linksTitledPane.setExpanded(true);
        }
        // Attributs inconnus
        rebuildExtraFields(fm.getExtraKeys(), fm);
    }

    /**
     * Construit un objet FrontMatter à partir des valeurs des champs.
     *
     * @return le front matter courant
     */
    public FrontMatter getFrontMatter() {
        FrontMatter fm = new FrontMatter();
        fm.setUuid(uuidField.getText().trim());
        fm.setTitle(titleField.getText().trim());
        fm.setAuthorsFromString(authorField.getText().trim());
        fm.setCreatedAt(createdAtField.getText().trim());
        fm.setTagsFromString(tagsField.getText().trim());
        fm.setSummary(summaryField.getText().trim());
        fm.setDraft(draftCheck.isSelected());
        fm.setLinks(new java.util.ArrayList<>(linksList));
        // Attributs inconnus
        for (Map.Entry<String, TextField> entry : extraFields.entrySet()) {
            fm.put(entry.getKey(), entry.getValue().getText().trim());
        }
        return fm;
    }

    /**
     * Efface tous les champs.
     */
    public void clear() {
        uuidField.clear();
        titleField.clear();
        authorField.clear();
        createdAtField.clear();
        tagsField.clear();
        summaryField.clear();
        draftCheck.setSelected(false);
        linksList.clear();
        clearExtraFields();
    }

    /**
     * Initialise un front matter par défaut avec la date du jour et un UUID.
     */
    public void initDefaults() {
        createdAtField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if (uuidField.getText().isBlank()) {
            uuidField.setText(java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * Retourne l'UUID courant du document.
     */
    public String getUuid() {
        return uuidField.getText().trim();
    }

    /**
     * Définit l'UUID du document.
     */
    public void setUuid(String uuid) {
        uuidField.setText(uuid != null ? uuid : "");
    }

    /**
     * Définit le callback déclenché lorsqu'un champ est modifié.
     */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /**
     * Définit le callback pour ouvrir un document lié par clic sur un lien.
     *
     * @param onLinkClick action recevant le fichier à ouvrir
     */
    public void setOnLinkClick(java.util.function.Consumer<File> onLinkClick) {
        this.onLinkClick = onLinkClick;
    }

    /**
     * Définit le répertoire de projet pour la recherche de documents par UUID.
     *
     * @param projectDir le répertoire racine du projet
     */
    public void setProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
    }

    // ── Privé ───────────────────────────────────────────────────

    /**
     * Reconstruit les champs d'attributs inconnus dans la grille.
     *
     * @param extraKeys les clés d'attributs non standard
     * @param fm        le front matter source
     */
    private void rebuildExtraFields(List<String> extraKeys, FrontMatter fm) {
        clearExtraFields();
        int row = knownFieldsEndRow;
        for (String key : extraKeys) {
            Label label = new Label(key);
            label.setStyle("-fx-font-style: italic;");
            TextField field = new TextField(fm.get(key));
            field.textProperty().addListener((o, ov, nv) -> fireChanged());
            grid.add(label, 0, row);
            grid.add(field, 1, row);
            extraFields.put(key, field);
            row++;
        }
    }

    /**
     * Supprime tous les champs d'attributs inconnus de la grille.
     */
    private void clearExtraFields() {
        for (Map.Entry<String, TextField> entry : extraFields.entrySet()) {
            grid.getChildren().removeIf(node -> {
                Integer nodeRow = GridPane.getRowIndex(node);
                return nodeRow != null && nodeRow >= knownFieldsEndRow;
            });
        }
        // Nettoyage final : supprimer tout nœud au-delà des champs connus
        grid.getChildren().removeIf(node -> {
            Integer nodeRow = GridPane.getRowIndex(node);
            return nodeRow != null && nodeRow >= knownFieldsEndRow;
        });
        extraFields.clear();
    }

    private void fireChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }
}
