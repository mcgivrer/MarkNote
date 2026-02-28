package ui;

import java.util.Locale;
import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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
 * Implémente les interfaces Detachable et Dockable pour permettre le détachement vers un onglet
 * et le docking vers différentes zones de la fenêtre.
 */
public abstract class BasePanel extends BorderPane implements Detachable, Dockable {

    protected static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final Label titleLabel;
    private final Button closeButton;
    private final Button detachButton;
    private final HBox header;
    private final String titleKey;

    private Runnable onCloseAction;
    private Runnable onDetachAction;
    
    // Docking support
    private DockPosition dockPosition = DockPosition.LEFT;
    private DockingManager dockingManager;
    private double dragStartX, dragStartY;
    private boolean dragging = false;
    private static final double DRAG_THRESHOLD = 10.0;

    /**
     * Crée un panel avec un bandeau contenant un titre et un bouton de fermeture.
     *
     * @param titleKey     Clé i18n pour le titre du panel
     * @param closeTooltipKey Clé i18n pour le tooltip du bouton de fermeture
     */
    protected BasePanel(String titleKey, String closeTooltipKey) {
        this.titleKey = titleKey;
        
        // Titre
        titleLabel = new Label(getMessages().getString(titleKey));
        titleLabel.getStyleClass().add("panel-title");

        // Espaceur
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Bouton de détachement
        detachButton = new Button("\u21F1"); // ⇱
        detachButton.getStyleClass().add("panel-close-button");
        detachButton.setTooltip(new Tooltip(getMessages().getString("panel.detach.tooltip")));
        detachButton.setOnAction(e -> {
            if (onDetachAction != null) {
                onDetachAction.run();
            }
        });

        // Bouton de fermeture
        closeButton = new Button("\u00D7");
        closeButton.getStyleClass().add("panel-close-button");
        closeButton.setTooltip(new Tooltip(getMessages().getString(closeTooltipKey)));
        closeButton.setOnAction(e -> {
            if (onCloseAction != null) {
                onCloseAction.run();
            }
        });

        // Header
        header = new HBox(titleLabel, spacer, detachButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4));
        header.getStyleClass().add("panel-header");
        header.setCursor(Cursor.MOVE);

        // Support du drag pour le docking
        setupDragHandlers();

        setTop(header);
    }

    /**
     * Configure les handlers de drag pour le docking.
     */
    private void setupDragHandlers() {
        header.setOnMousePressed(e -> {
            if (dockingManager == null) return;
            dragStartX = e.getScreenX();
            dragStartY = e.getScreenY();
            dragging = false;
        });

        header.setOnMouseDragged(e -> {
            if (dockingManager == null) return;
            
            double deltaX = Math.abs(e.getScreenX() - dragStartX);
            double deltaY = Math.abs(e.getScreenY() - dragStartY);
            
            if (!dragging && (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD)) {
                dragging = true;
                dockingManager.startDrag(this, e.getScreenX(), e.getScreenY());
            }
            
            if (dragging) {
                dockingManager.updateDrag(e.getScreenX(), e.getScreenY());
            }
        });

        header.setOnMouseReleased(e -> {
            if (dockingManager != null && dragging) {
                dockingManager.endDrag();
            }
            dragging = false;
        });
    }

    /**
     * Définit le DockingManager pour ce panel.
     *
     * @param manager le gestionnaire de docking
     */
    public void setDockingManager(DockingManager manager) {
        this.dockingManager = manager;
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

    /**
     * Retourne le header HBox pour permettre l'ajout de boutons supplémentaires.
     *
     * @return Le header HBox
     */
    protected HBox getHeader() {
        return header;
    }

    /**
     * Définit l'action à exécuter lors du clic sur le bouton de détachement.
     *
     * @param action L'action à exécuter
     */
    public void setOnDetach(Runnable action) {
        this.onDetachAction = action;
    }

    /**
     * Retourne le bouton de détachement pour permettre des configurations supplémentaires.
     *
     * @return Le bouton de détachement
     */
    protected Button getDetachButton() {
        return detachButton;
    }

    /**
     * Active ou désactive le bouton de détachement.
     * Par défaut, le détachement est activé.
     *
     * @param enabled true pour activer le bouton
     */
    public void setDetachEnabled(boolean enabled) {
        detachButton.setVisible(enabled);
        detachButton.setManaged(enabled);
    }

    // ══════════════════════════════════════════════════════════════
    // Implémentation de l'interface Detachable
    // ══════════════════════════════════════════════════════════════

    @Override
    public Node getDetachableContent() {
        return getCenter();
    }

    @Override
    public String getDetachTabTitle() {
        return getMessages().getString(titleKey);
    }

    @Override
    public void onReattached(Node content) {
        setCenter(content);
    }

    // ══════════════════════════════════════════════════════════════
    // Implémentation de l'interface Dockable
    // ══════════════════════════════════════════════════════════════

    @Override
    public DockPosition getDockPosition() {
        return dockPosition;
    }

    @Override
    public void setDockPosition(DockPosition position) {
        this.dockPosition = position;
    }

    @Override
    public String getDockTitle() {
        return getMessages().getString(titleKey);
    }
}
