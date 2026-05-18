package ui;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Overlay plein écran affiché pendant la restauration du workspace au démarrage.
 * Doit être ajouté en dernier enfant d'un {@link StackPane} pour couvrir toute la fenêtre.
 */
public class WorkspaceRestoreOverlay extends StackPane {

    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final ResourceBundle messages;

    public WorkspaceRestoreOverlay(ResourceBundle messages) {
        this.messages = messages;

        setAlignment(Pos.CENTER);
        setPickOnBounds(true); // bloque les clics pendant le chargement

        // Rectangle de fond : lié à notre propre taille, insensible au CSS du thème
        Rectangle backdrop = new Rectangle();
        backdrop.setFill(Color.rgb(0, 0, 0, 0.52));
        backdrop.widthProperty().bind(widthProperty());
        backdrop.heightProperty().bind(heightProperty());

        // ── Carte centrale ────────────────────────────────────────────
        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40, 64, 40, 64));
        card.setMaxWidth(480);
        card.setMaxHeight(480);
        card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.96);" +
            "-fx-background-radius: 14;" +
            "-fx-border-radius: 14;"
        );
        card.setEffect(new DropShadow(32, 0, 8, Color.rgb(0, 0, 0, 0.35)));

        // Logo
        ImageView logo = loadLogo();

        // Titre
        Label titleLabel = new Label(messages.getString("restore.overlay.title"));
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2c3440;");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(580);
        titleLabel.setAlignment(Pos.CENTER);

        // Barre de progression
        progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(520);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent: #3584e4;");

        // Fichier en cours
        statusLabel = new Label(" ");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7a8f;");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(580);
        statusLabel.setAlignment(Pos.CENTER);

        if (logo != null) {
            card.getChildren().addAll(logo, titleLabel, progressBar, statusLabel);
        } else {
            card.getChildren().addAll(titleLabel, progressBar, statusLabel);
        }
        getChildren().addAll(backdrop, card);
        setVisible(false);
    }

    /** Affiche l'overlay avec la barre indéterminée. */
    public void show() {
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText(" ");
        setVisible(true);
        toFront();
    }

    /**
     * Met à jour la progression et le nom du fichier en cours d'ouverture.
     *
     * @param filename nom du fichier
     * @param current  index 1-based du fichier en cours
     * @param total    nombre total de fichiers
     */
    public void setProgress(String filename, int current, int total) {
        progressBar.setProgress(total > 0 ? (double) current / total : ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText(MessageFormat.format(messages.getString("restore.overlay.file"), filename));
    }

    /** Masque l'overlay. */
    public void hide() {
        setVisible(false);
    }

    private ImageView loadLogo() {
        try (var is = getClass().getResourceAsStream("/images/icons/marknote-64.png")) {
            if (is != null) {
                ImageView iv = new ImageView(new Image(is));
                iv.setFitWidth(48);
                iv.setFitHeight(48);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                return iv;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
