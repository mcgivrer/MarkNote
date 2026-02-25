package ui;

import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Barre de statut affichée en bas de la fenêtre principale.
 * <p>
 * Trois sections :
 * <ol>
 *   <li>Nom du document courant et position du curseur (ligne:colonne)</li>
 *   <li>Statistiques : nombre de documents, lignes, mots</li>
 *   <li>Barre de progression de l'indexation</li>
 * </ol>
 */
public class StatusBar extends HBox {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    // ── Section 1 : document & position ──

    private final Label documentLabel;
    private final Label positionLabel;

    // ── Section 2 : statistiques ──

    private final Label statsLabel;

    // ── Section 3 : indexation ──

    private final Label indexLabel;
    private final ProgressBar progressBar;

    // ── Section 4 : indicateur PlantUML local ──

    private final Label plantUmlLabel;
    /** Icône engrenage animée, visible pendant le rendu local PlantUML. */
    private final Label gearLabel;
    private final RotateTransition gearSpin;

    public StatusBar() {
        ResourceBundle msg = getMessages();

        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 8, 2, 8));
        setSpacing(6);
        getStyleClass().add("status-bar");

        // ── Section 1 ──

        documentLabel = new Label();
        documentLabel.getStyleClass().add("status-section");

        positionLabel = new Label();
        positionLabel.getStyleClass().add("status-section");

        // ── Séparateur ──
        Separator sep1 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // ── Section 2 ──

        statsLabel = new Label();
        statsLabel.getStyleClass().add("status-section");

        // ── Espaceur ──
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Indicateur PlantUML local ──
        plantUmlLabel = new Label();
        plantUmlLabel.getStyleClass().addAll("status-section", "status-plantuml");
        plantUmlLabel.setVisible(false);
        plantUmlLabel.setManaged(false);

        // Engrenage animé : visible uniquement pendant le rendu
        gearLabel = new Label("⚙");
        gearLabel.getStyleClass().addAll("status-section", "status-plantuml-gear");
        gearLabel.setStyle("-fx-font-size: 13px;");
        gearLabel.setVisible(false);
        gearLabel.setManaged(false);

        gearSpin = new RotateTransition(Duration.seconds(1.5), gearLabel);
        gearSpin.setByAngle(360);
        gearSpin.setCycleCount(RotateTransition.INDEFINITE);
        gearSpin.setInterpolator(Interpolator.LINEAR);

        Separator sep2 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // ── Section 3 ──

        indexLabel = new Label();
        indexLabel.getStyleClass().add("status-section");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(140);
        progressBar.setPrefHeight(14);
        progressBar.setVisible(false);
        progressBar.getStyleClass().add("status-progress");

        getChildren().addAll(
                documentLabel, positionLabel,
                sep1,
                statsLabel,
                spacer,
                gearLabel, plantUmlLabel, sep2,
                indexLabel, progressBar
        );

        // Valeurs par défaut
        clearDocumentInfo();
        updateStats(0, 0, 0);
        setIndexIdle();
    }

    // ── API publique ────────────────────────────────────────────

    /**
     * Met à jour les informations sur le document courant.
     *
     * @param filename nom du fichier (ou titre de l'onglet)
     * @param line     numéro de ligne du curseur (1-based)
     * @param column   numéro de colonne du curseur (1-based)
     */
    public void updateDocumentInfo(String filename, int line, int column) {
        documentLabel.setText(filename != null ? filename : "");
        positionLabel.setText("Ln " + line + ", Col " + column);
    }

    /**
     * Efface les informations de document (aucun document ouvert).
     */
    public void clearDocumentInfo() {
        documentLabel.setText("");
        positionLabel.setText("");
    }

    /**
     * Met à jour les statistiques.
     *
     * @param documents nombre de documents dans l'index
     * @param lines     nombre de lignes du document courant
     * @param words     nombre de mots du document courant
     */
    public void updateStats(int documents, int lines, int words) {
        ResourceBundle msg = getMessages();
        statsLabel.setText(
                msg.getString("statusbar.docs") + ": " + documents
                + "  |  " + msg.getString("statusbar.lines") + ": " + lines
                + "  |  " + msg.getString("statusbar.words") + ": " + words
        );
    }

    /**
     * Affiche la barre de progression avec le pourcentage donné.
     *
     * @param progress valeur entre 0.0 et 1.0, ou -1 pour indéterminé
     */
    public void setIndexProgress(double progress) {
        progressBar.setVisible(true);
        progressBar.setProgress(progress);
        indexLabel.setText(getMessages().getString("statusbar.indexing"));
    }

    /**
     * Masque la barre de progression et affiche « Prêt ».
     */
    public void setIndexIdle() {
        progressBar.setVisible(false);
        progressBar.setProgress(0);
        indexLabel.setText(getMessages().getString("statusbar.ready"));
    }

    /**
     * Affiche ou masque l'indicateur PlantUML local.
     *
     * @param active {@code true} si un jar PlantUML local est configuré et activé
     */
    public void setPlantUmlIndicator(boolean active) {
        plantUmlLabel.setVisible(active);
        plantUmlLabel.setManaged(active);
        if (active) {
            plantUmlLabel.setText(getMessages().getString("statusbar.plantuml.local"));
        }
        // Si on désactive la config, éteindre aussi l'engrenage
        if (!active) {
            setPlantUmlRendering(false);
        }
    }

    /**
     * Montre/cache l'engrenage animé pendant le rendu PlantUML via jar local.
     *
     * @param rendering {@code true} pour démarrer la rotation, {@code false} pour l'arrêter
     */
    public void setPlantUmlRendering(boolean rendering) {
        if (rendering) {
            gearLabel.setVisible(true);
            gearLabel.setManaged(true);
            gearSpin.play();
        } else {
            gearSpin.stop();
            gearLabel.setRotate(0);
            gearLabel.setVisible(false);
            gearLabel.setManaged(false);
        }
    }
}
