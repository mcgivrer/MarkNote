package ui;

import java.util.Locale;
import java.util.ResourceBundle;

import javafx.scene.Node;
import javafx.scene.control.Tab;

/**
 * Onglet affichant le diagramme réseau dans le TabPane principal.
 * Réutilise le contenu du VisualLinkPanel.
 */
public class NetworkDiagramTab extends Tab {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final VisualLinkPanel sourcePanel;
    private final Node originalContent;
    private Runnable onCloseAction;

    public NetworkDiagramTab(VisualLinkPanel sourcePanel) {
        this.sourcePanel = sourcePanel;
        
        setText(getMessages().getString("networkdiagram.title"));
        setClosable(true);

        // Récupérer le contenu du panel et l'afficher dans l'onglet
        originalContent = sourcePanel.getCenter();
        sourcePanel.setCenter(null);
        setContent(originalContent);

        // Quand l'onglet est fermé
        setOnClosed(e -> {
            // Restituer le contenu au panel
            sourcePanel.setCenter(originalContent);
            sourcePanel.zoomToFit();
            
            if (onCloseAction != null) {
                onCloseAction.run();
            }
        });
    }

    /**
     * Définit l'action à exécuter quand l'onglet est fermé.
     */
    public void setOnCloseAction(Runnable action) {
        this.onCloseAction = action;
    }

    /**
     * Met à jour l'affichage.
     */
    public void refresh() {
        sourcePanel.zoomToFit();
    }
}
