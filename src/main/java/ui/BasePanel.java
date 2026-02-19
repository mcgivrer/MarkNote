package ui;

import java.util.Locale;
import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Classe parente pour les panels avec un bandeau contenant un titre et un bouton de fermeture [x].
 */
public abstract class BasePanel extends BorderPane {

    protected static final ResourceBundle messages = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());

    private final Label titleLabel;
    private final Button closeButton;
    private final HBox header;

    private Runnable onCloseAction;

    /**
     * Crée un panel avec un bandeau contenant un titre et un bouton de fermeture.
     *
     * @param titleKey     Clé i18n pour le titre du panel
     * @param closeTooltipKey Clé i18n pour le tooltip du bouton de fermeture
     */
    protected BasePanel(String titleKey, String closeTooltipKey) {
        // Titre
        titleLabel = new Label(messages.getString(titleKey));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Espaceur
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Bouton de fermeture
        closeButton = new Button("\u00D7");
        closeButton.setStyle(
                "-fx-font-size: 10; -fx-padding: 0 4 0 4; -fx-background-color: transparent; -fx-cursor: hand;");
        closeButton.setTooltip(new Tooltip(messages.getString(closeTooltipKey)));
        closeButton.setOnAction(e -> {
            if (onCloseAction != null) {
                onCloseAction.run();
            }
        });

        // Header
        header = new HBox(titleLabel, spacer, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4));
        header.setStyle("-fx-background-color: #e0e0e0;");

        setTop(header);
    }

    /**
     * Définit le contenu principal du panel.
     *
     * @param content Le contenu à afficher
     */
    protected void setContent(Node content) {
        setCenter(content);
    }

    /**
     * Définit l'action à exécuter lors du clic sur le bouton de fermeture.
     *
     * @param action L'action à exécuter
     */
    public void setOnClose(Runnable action) {
        this.onCloseAction = action;
    }

    /**
     * Met à jour le titre du panel.
     *
     * @param title Le nouveau titre
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Retourne le bouton de fermeture pour permettre des configurations supplémentaires.
     *
     * @return Le bouton de fermeture
     */
    protected Button getCloseButton() {
        return closeButton;
    }

    /**
     * Retourne le label du titre pour permettre des configurations supplémentaires.
     *
     * @return Le label du titre
     */
    protected Label getTitleLabel() {
        return titleLabel;
    }
}
