package ui;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Panneau affichant un nuage de tags.
 * <p>
 * Chaque tag est affiché avec une taille de police proportionnelle
 * à son nombre d'occurrences dans les documents du projet.
 * </p>
 */
public class TagCloudPanel extends BasePanel {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    private static final double MIN_FONT_SIZE = 11.0;
    private static final double MAX_FONT_SIZE = 28.0;

    private final FlowPane tagFlow;
    private final ScrollPane scrollPane;
    private final Label emptyLabel;
    private Consumer<String> onTagClick;

    public TagCloudPanel() {
        super("tagcloud.title", "tagcloud.close.tooltip");

        tagFlow = new FlowPane();
        tagFlow.setHgap(8);
        tagFlow.setVgap(6);
        tagFlow.setPadding(new Insets(8));
        tagFlow.setAlignment(Pos.TOP_LEFT);

        emptyLabel = new Label(bundle.getString("tagcloud.empty"));
        emptyLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        emptyLabel.setPadding(new Insets(12));

        scrollPane = new ScrollPane(emptyLabel);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        setContent(scrollPane);
        setPrefHeight(180);
    }

    /**
     * Met à jour le nuage de tags avec les compteurs donnés.
     *
     * @param tagCounts map de tag → nombre d'occurrences
     */
    public void updateTags(Map<String, Integer> tagCounts) {
        tagFlow.getChildren().clear();

        if (tagCounts == null || tagCounts.isEmpty()) {
            scrollPane.setContent(emptyLabel);
            return;
        }

        // Trouver min/max pour la normalisation
        int minCount = Integer.MAX_VALUE;
        int maxCount = Integer.MIN_VALUE;
        for (int count : tagCounts.values()) {
            if (count < minCount) minCount = count;
            if (count > maxCount) maxCount = count;
        }

        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            String tag = entry.getKey();
            int count = entry.getValue();

            double fontSize;
            if (maxCount == minCount) {
                fontSize = (MIN_FONT_SIZE + MAX_FONT_SIZE) / 2.0;
            } else {
                double ratio = (double) (count - minCount) / (maxCount - minCount);
                fontSize = MIN_FONT_SIZE + ratio * (MAX_FONT_SIZE - MIN_FONT_SIZE);
            }

            Label tagLabel = new Label(tag);
            tagLabel.setFont(Font.font("System", FontWeight.NORMAL, fontSize));
            tagLabel.getStyleClass().add("tag-cloud-label");
            tagLabel.setStyle(
                "-fx-cursor: hand; " +
                "-fx-padding: 2 6 2 6; " +
                "-fx-background-radius: 4; " +
                "-fx-background-color: derive(-fx-base, 20%);"
            );
            tagLabel.setTooltip(new Tooltip(tag + " (" + count + ")"));

            // Hover effect
            tagLabel.setOnMouseEntered(e ->
                tagLabel.setStyle(
                    "-fx-cursor: hand; " +
                    "-fx-padding: 2 6 2 6; " +
                    "-fx-background-radius: 4; " +
                    "-fx-background-color: derive(-fx-accent, 30%); " +
                    "-fx-text-fill: white;"
                )
            );
            tagLabel.setOnMouseExited(e ->
                tagLabel.setStyle(
                    "-fx-cursor: hand; " +
                    "-fx-padding: 2 6 2 6; " +
                    "-fx-background-radius: 4; " +
                    "-fx-background-color: derive(-fx-base, 20%);"
                )
            );

            // Click handler : triggers search by tag
            tagLabel.setOnMouseClicked(e -> {
                if (onTagClick != null) {
                    onTagClick.accept(tag);
                }
            });

            tagFlow.getChildren().add(tagLabel);
        }

        scrollPane.setContent(tagFlow);
    }

    /**
     * Définit l'action à exécuter lors du clic sur un tag.
     *
     * @param action callback recevant le nom du tag cliqué
     */
    public void setOnTagClick(Consumer<String> action) {
        this.onTagClick = action;
    }
}
