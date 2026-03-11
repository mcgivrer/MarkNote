package ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import utils.LogService;

/**
 * Gestionnaire de docking pour les panels de l'application.
 * <p>
 * Permet de docker des panels sur les 4 zones de la fenêtre (TOP, BOTTOM, LEFT, RIGHT)
 * avec support du drag & drop et réorganisation automatique des splits.
 * </p>
 */
public class DockingManager {

    private static final String LOG_SOURCE = "DockingManager";
    private static final double DROP_ZONE_THRESHOLD = 80.0;
    private static final double INDICATOR_OPACITY = 0.4;
    public static final DataFormat PANEL_DATA_FORMAT = new DataFormat("application/x-marknote-panel");

    private final LogService log = LogService.getInstance();
    private final BorderPane rootPane;
    private final StackPane contentStack;

    // Zones de docking (chaque zone peut contenir plusieurs panels dans un SplitPane)
    private final Map<DockZone, SplitPane> zoneSplits = new EnumMap<>(DockZone.class);
    private final Map<DockZone, List<BasePanel>> zonePanels = new EnumMap<>(DockZone.class);

    // Mémoriser la dernière zone de chaque panel (pour showPanel sans zone)
    private final Map<BasePanel, DockZone> lastPanelZone = new java.util.HashMap<>();

    // SplitPanes pour séparer les zones du centre
    private SplitPane horizontalSplit; // LEFT | CENTER | RIGHT
    private SplitPane verticalSplit;   // TOP | horizontalSplit | BOTTOM

    // Contenu central (éditeur)
    private Node centerContent;

    // Indicateurs de drop visuels
    private final Map<DockZone, Rectangle> dropIndicators = new EnumMap<>(DockZone.class);
    private final Pane indicatorLayer;

    // Panel en cours de drag
    private BasePanel draggingPanel;
    private DockZone highlightedZone;
    private DockZone dropTargetZone; // Zone de destination lors du drop

    // Callbacks
    private BiConsumer<BasePanel, DockZone> onPanelDocked;
    private Consumer<BasePanel> onPanelUndocked;
    private Consumer<BasePanel> onPanelClosed;

    /**
     * Crée un nouveau gestionnaire de docking.
     *
     * @param rootPane Le BorderPane racine de l'application
     */
    public DockingManager(BorderPane rootPane) {
        this.rootPane = rootPane;

        // Initialiser les listes de panels pour chaque zone
        for (DockZone zone : DockZone.values()) {
            zonePanels.put(zone, new ArrayList<>());
        }

        // Créer la couche d'indicateurs (superposée au contenu)
        indicatorLayer = new Pane();
        indicatorLayer.setMouseTransparent(true);
        indicatorLayer.setPickOnBounds(false);

        // Créer les indicateurs de drop pour chaque zone
        createDropIndicators();

        // Stack pour superposer le contenu et les indicateurs
        contentStack = new StackPane();

        log.debug(LOG_SOURCE, "DockingManager initialized");
    }

    /**
     * Définit le contenu central (zone éditeur).
     */
    public void setCenterContent(Node content) {
        this.centerContent = content;
        rebuildLayout();
    }

    /**
     * Retourne le contenu central.
     */
    public Node getCenterContent() {
        return centerContent;
    }

    /**
     * Retourne le noeud racine à utiliser dans la scène.
     */
    public StackPane getRootNode() {
        return contentStack;
    }

    /**
     * Docke un panel dans une zone spécifique.
     *
     * @param panel Le panel à docker
     * @param zone  La zone de destination
     */
    public void dock(BasePanel panel, DockZone zone) {
        if (panel == null || zone == null || zone == DockZone.CENTER) {
            return;
        }

        log.info(LOG_SOURCE, "Docking panel '" + panel.getDetachTabTitle() + "' to " + zone);

        // Retirer le panel de son ancienne zone si nécessaire
        DockZone oldZone = getZone(panel);
        if (oldZone != null) {
            zonePanels.get(oldZone).remove(panel);
        }

        // Ajouter à la nouvelle zone
        zonePanels.get(zone).add(panel);

        // Mémoriser la zone pour showPanel sans argument
        lastPanelZone.put(panel, zone);

        // Configurer les actions du panel
        setupPanelActions(panel, zone);

        // Reconstruire le layout
        rebuildLayout();

        if (onPanelDocked != null) {
            onPanelDocked.accept(panel, zone);
        }
    }

    /**
     * Retire un panel de sa zone de docking.
     */
    public void undock(BasePanel panel) {
        DockZone zone = getZone(panel);
        if (zone != null) {
            log.info(LOG_SOURCE, "Undocking panel '" + panel.getDetachTabTitle() + "' from " + zone);
            zonePanels.get(zone).remove(panel);
            rebuildLayout();

            if (onPanelUndocked != null) {
                onPanelUndocked.accept(panel);
            }
        }
    }

    /**
     * Cache un panel (le retire du layout sans le supprimer de la liste).
     */
    public void hidePanel(BasePanel panel) {
        DockZone zone = getZone(panel);
        if (zone != null) {
            log.debug(LOG_SOURCE, "Hiding panel: " + panel.getDetachTabTitle());
            zonePanels.get(zone).remove(panel);
            rebuildLayout();
            
            if (onPanelClosed != null) {
                onPanelClosed.accept(panel);
            }
        }
    }

    /**
     * Affiche un panel précédemment caché dans une zone spécifique.
     */
    public void showPanel(BasePanel panel, DockZone zone) {
        if (!zonePanels.get(zone).contains(panel)) {
            dock(panel, zone);
        }
    }

    /**
     * Affiche un panel précédemment caché dans sa dernière zone connue.
     */
    public void showPanel(BasePanel panel) {
        DockZone zone = lastPanelZone.get(panel);
        if (zone != null) {
            showPanel(panel, zone);
        } else {
            // Zone par défaut si inconnue
            showPanel(panel, DockZone.LEFT);
        }
    }

    /**
     * Configure les actions du panel (fermeture, drag).
     */
    private void setupPanelActions(BasePanel panel, DockZone zone) {
        // Fermeture = cacher le panel
        panel.setOnClose(() -> hidePanel(panel));

        // Activer le drag sur le header du panel
        enableDrag(panel);
    }

    /**
     * Active le drag & drop sur un panel.
     */
    private void enableDrag(BasePanel panel) {
        // Utiliser directement le header du panel
        Node header = panel.getHeader();
        header.setCursor(Cursor.MOVE);
        
        header.setOnDragDetected(e -> {
            draggingPanel = panel;
            dropTargetZone = null;
            
            Dragboard db = header.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(PANEL_DATA_FORMAT, panel.getDetachTabTitle());
            db.setContent(content);
            
            log.debug(LOG_SOURCE, "Drag started for panel: " + panel.getDetachTabTitle());
            showDropIndicators();
            e.consume();
        });

        header.setOnDragDone(e -> {
            log.debug(LOG_SOURCE, "Drag done, transfer mode: " + e.getTransferMode() + ", dropTargetZone: " + dropTargetZone);
            hideDropIndicators();
            if (e.getTransferMode() == TransferMode.MOVE && dropTargetZone != null) {
                // Le drop a été accepté
                DockZone currentZone = getZone(draggingPanel);
                if (currentZone != dropTargetZone) {
                    log.info(LOG_SOURCE, "Moving panel from " + currentZone + " to " + dropTargetZone);
                    dock(draggingPanel, dropTargetZone);
                }
            }
            draggingPanel = null;
            dropTargetZone = null;
            highlightedZone = null;
            e.consume();
        });

        // Configurer les zones de drop sur le contentStack
        setupDropZones();
    }

    /**
     * Configure les zones de drop pour recevoir les panels.
     */
    private void setupDropZones() {
        // Configurer sur le contentStack
        contentStack.setOnDragOver(e -> {
            if (draggingPanel != null) {
                e.acceptTransferModes(TransferMode.MOVE);
                updateHighlightedZone(e.getX(), e.getY());
            }
            e.consume();
        });

        contentStack.setOnDragDropped(e -> {
            log.debug(LOG_SOURCE, "Drop received on contentStack, zone: " + highlightedZone);
            if (draggingPanel != null && highlightedZone != null) {
                dropTargetZone = highlightedZone; // Sauvegarder avant dragDone
                e.setDropCompleted(true);
            } else {
                e.setDropCompleted(false);
            }
            e.consume();
        });

        contentStack.setOnDragExited(e -> {
            highlightedZone = null;
            updateIndicatorHighlights();
            e.consume();
        });
        
        // Propager les événements depuis l'indicatorLayer (en mode non-transparent pendant le drag)
        indicatorLayer.setOnDragOver(e -> {
            if (draggingPanel != null) {
                e.acceptTransferModes(TransferMode.MOVE);
                updateHighlightedZone(e.getX(), e.getY());
            }
            e.consume();
        });
        
        indicatorLayer.setOnDragDropped(e -> {
            log.debug(LOG_SOURCE, "Drop received on indicatorLayer, zone: " + highlightedZone);
            if (draggingPanel != null && highlightedZone != null) {
                dropTargetZone = highlightedZone; // Sauvegarder avant dragDone
                e.setDropCompleted(true);
            } else {
                e.setDropCompleted(false);
            }
            e.consume();
        });
    }

    /**
     * Met à jour la zone survolée pendant le drag.
     */
    private void updateHighlightedZone(double x, double y) {
        double w = contentStack.getWidth();
        double h = contentStack.getHeight();

        DockZone newHighlight = null;

        // Déterminer la zone survolée en fonction de la position
        if (y < DROP_ZONE_THRESHOLD) {
            newHighlight = DockZone.TOP;
        } else if (y > h - DROP_ZONE_THRESHOLD) {
            newHighlight = DockZone.BOTTOM;
        } else if (x < DROP_ZONE_THRESHOLD) {
            newHighlight = DockZone.LEFT;
        } else if (x > w - DROP_ZONE_THRESHOLD) {
            newHighlight = DockZone.RIGHT;
        }

        if (newHighlight != highlightedZone) {
            highlightedZone = newHighlight;
            updateIndicatorHighlights();
        }
    }

    /**
     * Crée les indicateurs visuels de drop.
     */
    private void createDropIndicators() {
        for (DockZone zone : DockZone.values()) {
            if (zone == DockZone.CENTER) continue;

            Rectangle indicator = new Rectangle();
            indicator.setFill(Color.rgb(53, 132, 228, INDICATOR_OPACITY));
            indicator.setStroke(Color.rgb(53, 132, 228, 0.8));
            indicator.setStrokeWidth(2);
            indicator.setArcWidth(8);
            indicator.setArcHeight(8);
            indicator.setVisible(false);
            indicator.setMouseTransparent(true);

            dropIndicators.put(zone, indicator);
            indicatorLayer.getChildren().add(indicator);
        }
    }

    /**
     * Affiche les indicateurs de drop.
     */
    private void showDropIndicators() {
        updateIndicatorPositions();
        for (Rectangle indicator : dropIndicators.values()) {
            indicator.setVisible(true);
            indicator.setOpacity(0.2);
            indicator.setMouseTransparent(false); // Autoriser les événements de drop
        }
        
        // S'assurer que la couche d'indicateurs est visible et reçoit les événements
        indicatorLayer.setMouseTransparent(false);
        if (!contentStack.getChildren().contains(indicatorLayer)) {
            contentStack.getChildren().add(indicatorLayer);
        }
        indicatorLayer.toFront();
    }

    /**
     * Cache les indicateurs de drop.
     */
    private void hideDropIndicators() {
        for (Rectangle indicator : dropIndicators.values()) {
            indicator.setVisible(false);
            indicator.setMouseTransparent(true);
        }
        indicatorLayer.setMouseTransparent(true);
        highlightedZone = null;
    }

    /**
     * Met à jour les highlights des indicateurs.
     */
    private void updateIndicatorHighlights() {
        for (Map.Entry<DockZone, Rectangle> entry : dropIndicators.entrySet()) {
            Rectangle indicator = entry.getValue();
            if (entry.getKey() == highlightedZone) {
                indicator.setOpacity(0.6);
                indicator.setStrokeWidth(3);
            } else {
                indicator.setOpacity(0.2);
                indicator.setStrokeWidth(1);
            }
        }
    }

    /**
     * Met à jour les positions des indicateurs.
     */
    private void updateIndicatorPositions() {
        double w = contentStack.getWidth();
        double h = contentStack.getHeight();
        double margin = 5;
        double zoneSize = DROP_ZONE_THRESHOLD - 10;

        // TOP
        Rectangle top = dropIndicators.get(DockZone.TOP);
        top.setX(margin);
        top.setY(margin);
        top.setWidth(w - 2 * margin);
        top.setHeight(zoneSize);

        // BOTTOM
        Rectangle bottom = dropIndicators.get(DockZone.BOTTOM);
        bottom.setX(margin);
        bottom.setY(h - zoneSize - margin);
        bottom.setWidth(w - 2 * margin);
        bottom.setHeight(zoneSize);

        // LEFT
        Rectangle left = dropIndicators.get(DockZone.LEFT);
        left.setX(margin);
        left.setY(zoneSize + margin * 2);
        left.setWidth(zoneSize);
        left.setHeight(h - 2 * zoneSize - 4 * margin);

        // RIGHT
        Rectangle right = dropIndicators.get(DockZone.RIGHT);
        right.setX(w - zoneSize - margin);
        right.setY(zoneSize + margin * 2);
        right.setWidth(zoneSize);
        right.setHeight(h - 2 * zoneSize - 4 * margin);
    }

    /**
     * Reconstruit le layout complet.
     */
    public void rebuildLayout() {
        log.debug(LOG_SOURCE, "Rebuilding layout");

        // Construire le centre avec LEFT | CENTER | RIGHT
        Node leftContent = buildZoneContent(DockZone.LEFT, Orientation.VERTICAL);
        Node rightContent = buildZoneContent(DockZone.RIGHT, Orientation.VERTICAL);

        Node centerSection;
        if (leftContent != null || rightContent != null) {
            horizontalSplit = new SplitPane();
            horizontalSplit.setOrientation(Orientation.HORIZONTAL);

            List<Node> items = new ArrayList<>();
            List<Double> positions = new ArrayList<>();

            if (leftContent != null) {
                items.add(leftContent);
                positions.add(0.2);
            }
            
            if (centerContent != null) {
                items.add(centerContent);
            }
            
            if (rightContent != null) {
                items.add(rightContent);
                if (!positions.isEmpty()) {
                    positions.add(0.8);
                }
            }

            horizontalSplit.getItems().addAll(items);
            if (!positions.isEmpty()) {
                double[] pos = positions.stream().mapToDouble(d -> d).toArray();
                horizontalSplit.setDividerPositions(pos);
            }
            centerSection = horizontalSplit;
        } else {
            centerSection = centerContent;
        }

        // Construire avec TOP | centerSection | BOTTOM
        Node topContent = buildZoneContent(DockZone.TOP, Orientation.HORIZONTAL);
        Node bottomContent = buildZoneContent(DockZone.BOTTOM, Orientation.HORIZONTAL);

        Node mainContent;
        if (topContent != null || bottomContent != null) {
            verticalSplit = new SplitPane();
            verticalSplit.setOrientation(Orientation.VERTICAL);

            List<Node> items = new ArrayList<>();
            List<Double> positions = new ArrayList<>();

            if (topContent != null) {
                items.add(topContent);
                positions.add(0.15);
            }
            
            if (centerSection != null) {
                items.add(centerSection);
            }
            
            if (bottomContent != null) {
                items.add(bottomContent);
                if (!positions.isEmpty()) {
                    positions.add(0.85);
                } else {
                    positions.add(0.8);
                }
            }

            verticalSplit.getItems().addAll(items);
            if (!positions.isEmpty()) {
                double[] pos = positions.stream().mapToDouble(d -> d).toArray();
                verticalSplit.setDividerPositions(pos);
            }
            mainContent = verticalSplit;
        } else {
            mainContent = centerSection;
        }

        // Mettre à jour le content stack
        contentStack.getChildren().clear();
        if (mainContent != null) {
            contentStack.getChildren().add(mainContent);
        }
        contentStack.getChildren().add(indicatorLayer);

        // Réactiver les zones de drop
        setupDropZones();
    }

    /**
     * Construit le contenu d'une zone de docking.
     */
    private Node buildZoneContent(DockZone zone, Orientation orientation) {
        List<BasePanel> panels = zonePanels.get(zone);
        if (panels.isEmpty()) {
            zoneSplits.remove(zone);
            return null;
        }

        // Réappliquer les actions sur les panels
        for (BasePanel panel : panels) {
            enableDrag(panel);
        }

        if (panels.size() == 1) {
            zoneSplits.remove(zone);
            return panels.get(0);
        }

        // Plusieurs panels : créer un SplitPane
        SplitPane split = new SplitPane();
        split.setOrientation(orientation);
        split.getItems().addAll(panels);

        // Répartir équitablement
        double[] positions = new double[panels.size() - 1];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = (double) (i + 1) / panels.size();
        }
        split.setDividerPositions(positions);

        zoneSplits.put(zone, split);
        return split;
    }

    /**
     * Retourne la zone où un panel est docké.
     */
    public DockZone getZone(BasePanel panel) {
        for (Map.Entry<DockZone, List<BasePanel>> entry : zonePanels.entrySet()) {
            if (entry.getValue().contains(panel)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Retourne tous les panels d'une zone.
     */
    public List<BasePanel> getPanels(DockZone zone) {
        return new ArrayList<>(zonePanels.get(zone));
    }

    /**
     * Vérifie si un panel est docké.
     */
    public boolean isDocked(BasePanel panel) {
        return getZone(panel) != null;
    }

    /**
     * Définit le callback appelé quand un panel est docké.
     */
    public void setOnPanelDocked(BiConsumer<BasePanel, DockZone> callback) {
        this.onPanelDocked = callback;
    }

    /**
     * Définit le callback appelé quand un panel est undocké.
     */
    public void setOnPanelUndocked(Consumer<BasePanel> callback) {
        this.onPanelUndocked = callback;
    }

    /**
     * Définit le callback appelé quand un panel est fermé.
     */
    public void setOnPanelClosed(Consumer<BasePanel> callback) {
        this.onPanelClosed = callback;
    }
}
