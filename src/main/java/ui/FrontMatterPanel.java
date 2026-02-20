package ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

import utils.FrontMatter;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

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

    private Runnable onChanged;

    public FrontMatterPanel() {
        setText(getMessages().getString("frontmatter.title"));
        setCollapsible(true);
        setExpanded(true);
        getStyleClass().add("front-matter-panel");

        GridPane grid = new GridPane();
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

        setContent(grid);
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
        titleField.setText(fm.getTitle());
        authorField.setText(fm.getAuthorsAsString());
        createdAtField.setText(fm.getCreatedAt());
        tagsField.setText(fm.getTagsAsString());
        summaryField.setText(fm.getSummary());
        draftCheck.setSelected(fm.isDraft());
    }

    /**
     * Construit un objet FrontMatter à partir des valeurs des champs.
     *
     * @return le front matter courant
     */
    public FrontMatter getFrontMatter() {
        FrontMatter fm = new FrontMatter();
        fm.setTitle(titleField.getText().trim());
        fm.setAuthorsFromString(authorField.getText().trim());
        fm.setCreatedAt(createdAtField.getText().trim());
        fm.setTagsFromString(tagsField.getText().trim());
        fm.setSummary(summaryField.getText().trim());
        fm.setDraft(draftCheck.isSelected());
        return fm;
    }

    /**
     * Efface tous les champs.
     */
    public void clear() {
        titleField.clear();
        authorField.clear();
        createdAtField.clear();
        tagsField.clear();
        summaryField.clear();
        draftCheck.setSelected(false);
    }

    /**
     * Initialise un front matter par défaut avec la date du jour.
     */
    public void initDefaults() {
        createdAtField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    /**
     * Définit le callback déclenché lorsqu'un champ est modifié.
     */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    // ── Privé ───────────────────────────────────────────────────

    private void fireChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }
}
