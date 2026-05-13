package ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Tab;

/**
 * Onglet générique affichant le contenu détaché d'un panel.
 * <p>
 * Cette classe permet de prendre le contenu d'un panel implémentant
 * l'interface {@link Detachable} et de l'afficher dans un onglet
 * du TabPane principal.
 * </p>
 */
public class DetachedPanelTab extends Tab {

    private final BasePanel sourcePanel;
    private final Node detachedContent;
    private Runnable onCloseAction;
    private boolean runCloseActionOnClose = true;

    /**
     * Crée un onglet détaché à partir d'un panel.
     *
     * @param sourcePanel le panel source implémentant Detachable
     */
    public DetachedPanelTab(BasePanel sourcePanel) {
        this.sourcePanel = sourcePanel;
        
        setText(sourcePanel.getDetachTabTitle());
        setClosable(true);

        // Récupérer le contenu du panel et l'afficher dans l'onglet
        detachedContent = sourcePanel.getDetachableContent();
        sourcePanel.setCenter(null);
        setContent(detachedContent);

        // Notifier le panel qu'il a été détaché
        sourcePanel.onDetached();

        // Quand l'onglet est fermé
        setOnClosed(e -> {
            // Restituer le contenu au panel
            sourcePanel.onReattached(detachedContent);
            
            // Actions post-rattachement après stabilisation du layout
            Platform.runLater(() -> sourcePanel.afterReattach());
            
            if (runCloseActionOnClose && onCloseAction != null) {
                onCloseAction.run();
            }
        });
    }

    /**
     * Retourne le panel source.
     *
     * @return le panel source
     */
    public BasePanel getSourcePanel() {
        return sourcePanel;
    }

    /**
     * Définit l'action à exécuter quand l'onglet est fermé.
     *
     * @param action l'action à exécuter
     */
    public void setOnCloseAction(Runnable action) {
        this.onCloseAction = action;
    }

    public void hideWithoutDocking() {
        runCloseActionOnClose = false;
        // Restore the panel's content before removing the tab so the panel
        // is not left with a null center when shown again.
        sourcePanel.onReattached(detachedContent);
        if (getTabPane() != null) {
            getTabPane().getTabs().remove(this);
        }
    }

    /**
     * Met à jour l'affichage après rattachement.
     */
    public void refresh() {
        sourcePanel.afterReattach();
    }
}
