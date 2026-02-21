package ui;

import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

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

        // ── Séparateur ──
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
                sep2,
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
}
