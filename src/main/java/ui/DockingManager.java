package ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Gestionnaire de docking pour les panels.
 * <p>
 * Affiche des zones de drop visuelles sur les bords de la fenêtre pendant le drag
 * et gère le repositionnement des panels lors du drop.
 * </p>
 */
public class DockingManager {

    // ═══════════════════════════════════════════════════════════════
    // Constantes visuelles
    // ═══════════════════════════════════════════════════════════════

    /** Opacité normale des zones de drop */
    private static final double ZONE_OPACITY = 0.4;
    /** Opacité des zones de drop quand survolées */
    private static final double ZONE_HIGHLIGHT_OPACITY = 0.7;
    /** Couleur des zones de drop */
    private static final Color ZONE_COLOR = Color.DODGERBLUE;
    /** Épaisseur des zones de drop sur les bords TOP/BOTTOM */
    private static final double ZONE_THICKNESS = 60.0;
    /** Épaisseur minimum pour LEFT/RIGHT si container absent */
    private static final double MIN_ZONE_WIDTH = 60.0;
    /** Distance à partir de laquelle les zones apparaissent */
    private static final double ZONE_ACTIVATION_DISTANCE = 120.0;
    /** Opacité du rectangle fantôme */
    private static final double GHOST_OPACITY = 0.5;
    /** Couleur du rectangle fantôme */
    private static final Color GHOST_COLOR = Color.LIGHTBLUE;
    /** Épaisseur des zones de séparation entre panels */
    private static final double DIVIDER_ZONE_THICKNESS = 20.0;
    /** Couleur des zones de séparation */
    private static final Color DIVIDER_COLOR = Color.ORANGE;

    // ═══════════════════════════════════════════════════════════════
    // Composants
    // ═══════════════════════════════════════════════════════════════

    private final BorderPane rootPane;
    private final Pane dockOverlay;
    private final Map<DockPosition, Rectangle> dropZones = new HashMap<>();
    private final Map<DockPosition, SplitPane> dockContainers = new HashMap<>();
    private final Rectangle ghostRectangle;
    private final List<DividerDropZone> dividerZones = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // État du drag
    // ═══════════════════════════════════════════════════════════════

    private DockPosition highlightedZone = null;
    private DividerDropZone highlightedDivider = null;
    private BasePanel draggingPanel = null;
    private BiConsumer<BasePanel, DockPosition> onDockAction;
    private TriConsumer<BasePanel, SplitPane, Integer> onInsertAction;
    private double panelWidth, panelHeight;
    private double offsetX, offsetY;

    /**
     * Interface fonctionnelle pour l'action d'insertion.
     */
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    /**
     * Représente une zone de drop entre deux panels.
     */
    private static class DividerDropZone {
        final Rectangle rectangle;
        final SplitPane container;
        final int insertIndex;

        DividerDropZone(Rectangle rectangle, SplitPane container, int insertIndex) {
            this.rectangle = rectangle;
            this.container = container;
            this.insertIndex = insertIndex;
        }
    }

    /**
     * Crée un gestionnaire de docking.
     *
     * @param rootPane le BorderPane racine de l'application
     */
    public DockingManager(BorderPane rootPane) {
        this.rootPane = rootPane;

        // Overlay qui contient les zones et le fantôme
        this.dockOverlay = new Pane();
        this.dockOverlay.setPickOnBounds(false);
        this.dockOverlay.setMouseTransparent(true);
        this.dockOverlay.setVisible(false);

        // Rectangle fantôme (représente le panel en cours de drag)
        ghostRectangle = new Rectangle();
        ghostRectangle.setFill(GHOST_COLOR);
        ghostRectangle.setOpacity(GHOST_OPACITY);
        ghostRectangle.setArcWidth(8);
        ghostRectangle.setArcHeight(8);
        ghostRectangle.setStroke(Color.STEELBLUE);
        ghostRectangle.setStrokeWidth(2);
        ghostRectangle.setVisible(false);

        createDropZones();
        dockOverlay.getChildren().add(ghostRectangle);
    }

    /**
     * Crée les zones de drop sur les bords.
     */
    private void createDropZones() {
        for (DockPosition pos : new DockPosition[]{
                DockPosition.TOP, DockPosition.BOTTOM, 
                DockPosition.LEFT, DockPosition.RIGHT}) {
            Rectangle zone = createZoneRectangle();
            dropZones.put(pos, zone);
            dockOverlay.getChildren().add(zone);
        }
    }

    /**
     * Crée un rectangle pour une zone de drop.
     */
    private Rectangle createZoneRectangle() {
        Rectangle rect = new Rectangle();
        rect.setFill(ZONE_COLOR);
        rect.setOpacity(ZONE_OPACITY);
        rect.setArcWidth(6);
        rect.setArcHeight(6);
        rect.setStroke(ZONE_COLOR.darker());
        rect.setStrokeWidth(2);
        rect.setVisible(false);
        return rect;
    }

    /**
     * Retourne l'overlay à ajouter au-dessus de la scène.
     */
    public Pane getOverlay() {
        return dockOverlay;
    }

    /**
     * Enregistre un conteneur de dock pour une position donnée.
     */
    public void registerDockContainer(DockPosition position, SplitPane container) {
        dockContainers.put(position, container);
    }

    /**
     * Définit l'action à exécuter lors d'un dock sur un bord.
     */
    public void setOnDockAction(BiConsumer<BasePanel, DockPosition> action) {
        this.onDockAction = action;
    }

    /**
     * Définit l'action à exécuter lors d'une insertion entre panels.
     */
    public void setOnInsertAction(TriConsumer<BasePanel, SplitPane, Integer> action) {
        this.onInsertAction = action;
    }

    /**
     * Démarre le drag d'un panel.
     *
     * @param panel le panel en cours de drag
     * @param screenX position X écran de la souris
     * @param screenY position Y écran de la souris
     */
    public void startDrag(BasePanel panel, double screenX, double screenY) {
        this.draggingPanel = panel;
        this.highlightedZone = null;
        this.highlightedDivider = null;

        // Mémoriser la taille du panel
        this.panelWidth = panel.getWidth();
        this.panelHeight = panel.getHeight();

        // Calculer l'offset pour centrer le fantôme sous la souris
        Point2D localPoint = dockOverlay.screenToLocal(screenX, screenY);
        if (localPoint != null) {
            this.offsetX = panelWidth / 2;
            this.offsetY = 20; // Décalage depuis le haut (barre de titre)
        }

        // Configurer le rectangle fantôme
        ghostRectangle.setWidth(panelWidth);
        ghostRectangle.setHeight(panelHeight);
        ghostRectangle.setVisible(true);

        // Mettre à jour les positions des zones
        updateZonePositions();

        // Créer les zones de séparation entre panels
        createDividerZones();

        // Afficher l'overlay
        dockOverlay.setVisible(true);
        
        // Positionner le fantôme
        updateGhostPosition(screenX, screenY);
    }

    /**
     * Met à jour la position du curseur pendant le drag.
     */
    public void updateDrag(double screenX, double screenY) {
        if (draggingPanel == null) return;

        // Mettre à jour la position du fantôme
        updateGhostPosition(screenX, screenY);

        // Convertir en coordonnées locales
        Point2D localPoint = dockOverlay.screenToLocal(screenX, screenY);
        if (localPoint == null) return;

        double x = localPoint.getX();
        double y = localPoint.getY();
        double overlayWidth = dockOverlay.getWidth();
        double overlayHeight = dockOverlay.getHeight();

        // Afficher les zones uniquement quand on s'approche des bords
        updateZoneVisibility(x, y, overlayWidth, overlayHeight);

        // Mettre à jour la visibilité des zones de séparation
        updateDividerZoneVisibility(x, y);

        // Priorité aux zones de séparation (si on est dessus, pas de zone de bord)
        DividerDropZone newDividerHighlight = findDividerZoneAt(x, y);
        
        if (newDividerHighlight != highlightedDivider) {
            // Désélectionner l'ancienne zone de séparation
            if (highlightedDivider != null) {
                highlightedDivider.rectangle.setOpacity(ZONE_OPACITY);
            }
            highlightedDivider = newDividerHighlight;
            // Sélectionner la nouvelle zone de séparation
            if (highlightedDivider != null) {
                highlightedDivider.rectangle.setOpacity(ZONE_HIGHLIGHT_OPACITY);
            }
        }

        // Si on est sur une zone de séparation, pas de zone de bord
        DockPosition newHighlight = (highlightedDivider == null) ? findZoneAt(x, y) : null;

        if (newHighlight != highlightedZone) {
            // Désélectionner l'ancienne zone
            if (highlightedZone != null) {
                Rectangle oldRect = dropZones.get(highlightedZone);
                if (oldRect != null) oldRect.setOpacity(ZONE_OPACITY);
            }

            highlightedZone = newHighlight;

            // Sélectionner la nouvelle zone
            if (highlightedZone != null) {
                Rectangle newRect = dropZones.get(highlightedZone);
                if (newRect != null) newRect.setOpacity(ZONE_HIGHLIGHT_OPACITY);
            }
        }
    }

    /**
     * Met à jour la position du rectangle fantôme.
     */
    private void updateGhostPosition(double screenX, double screenY) {
        Point2D localPoint = dockOverlay.screenToLocal(screenX, screenY);
        if (localPoint == null) return;

        ghostRectangle.setX(localPoint.getX() - offsetX);
        ghostRectangle.setY(localPoint.getY() - offsetY);
    }

    /**
     * Affiche/masque les zones en fonction de la proximité avec les bords.
     */
    private void updateZoneVisibility(double x, double y, double w, double h) {
        // Zone TOP : visible si proche du haut
        Rectangle topZone = dropZones.get(DockPosition.TOP);
        topZone.setVisible(y < ZONE_ACTIVATION_DISTANCE);

        // Zone BOTTOM : visible si proche du bas
        Rectangle bottomZone = dropZones.get(DockPosition.BOTTOM);
        bottomZone.setVisible(y > h - ZONE_ACTIVATION_DISTANCE);

        // Zone LEFT : visible si dans la zone gauche (basé sur la largeur du conteneur)
        Rectangle leftZone = dropZones.get(DockPosition.LEFT);
        double leftWidth = getContainerWidth(DockPosition.LEFT);
        // Activation quand on est proche du bord gauche (avec marge en plus)
        leftZone.setVisible(x < leftWidth + ZONE_ACTIVATION_DISTANCE / 2);

        // Zone RIGHT : visible si dans la zone droite (basé sur la largeur du conteneur)
        Rectangle rightZone = dropZones.get(DockPosition.RIGHT);
        double rightWidth = getContainerWidth(DockPosition.RIGHT);
        // Activation quand on est proche du bord droit (avec marge en plus)
        rightZone.setVisible(x > w - rightWidth - ZONE_ACTIVATION_DISTANCE / 2);
    }

    /**
     * Termine le drag et effectue le dock si une zone est sélectionnée.
     */
    public void endDrag() {
        BasePanel panel = draggingPanel;
        DockPosition targetPosition = highlightedZone;
        DividerDropZone targetDivider = highlightedDivider;

        hideAll();

        if (panel != null) {
            if (targetDivider != null && onInsertAction != null) {
                // Insertion entre panels
                onInsertAction.accept(panel, targetDivider.container, targetDivider.insertIndex);
            } else if (targetPosition != null && onDockAction != null) {
                // Dock sur un bord
                onDockAction.accept(panel, targetPosition);
            }
        }

        draggingPanel = null;
        highlightedZone = null;
        highlightedDivider = null;
    }

    /**
     * Annule le drag en cours.
     */
    public void cancelDrag() {
        hideAll();
        draggingPanel = null;
        highlightedZone = null;
        highlightedDivider = null;
    }

    /**
     * Masque tout l'overlay.
     */
    private void hideAll() {
        dockOverlay.setVisible(false);
        ghostRectangle.setVisible(false);
        for (Rectangle zone : dropZones.values()) {
            zone.setVisible(false);
            zone.setOpacity(ZONE_OPACITY);
        }
        // Nettoyer les zones de séparation
        for (DividerDropZone divider : dividerZones) {
            dockOverlay.getChildren().remove(divider.rectangle);
        }
        dividerZones.clear();
    }

    /**
     * Met à jour les positions et dimensions des zones de drop.
     */
    private void updateZonePositions() {
        // Obtenir les bounds du centre (zone principale sans menu/status bar)
        Node center = rootPane.getCenter();
        if (center == null) return;

        Bounds centerBounds = center.getBoundsInParent();
        double cx = centerBounds.getMinX();
        double cy = centerBounds.getMinY();
        double cw = centerBounds.getWidth();
        double ch = centerBounds.getHeight();

        // Zone TOP - bande horizontale sur tout le haut
        Rectangle topZone = dropZones.get(DockPosition.TOP);
        topZone.setX(cx);
        topZone.setY(cy);
        topZone.setWidth(cw);
        topZone.setHeight(ZONE_THICKNESS);

        // Zone BOTTOM - bande horizontale sur tout le bas
        Rectangle bottomZone = dropZones.get(DockPosition.BOTTOM);
        bottomZone.setX(cx);
        bottomZone.setY(cy + ch - ZONE_THICKNESS);
        bottomZone.setWidth(cw);
        bottomZone.setHeight(ZONE_THICKNESS);

        // Zone LEFT - largeur basée sur le conteneur LEFT (si présent)
        Rectangle leftZone = dropZones.get(DockPosition.LEFT);
        double leftWidth = getContainerWidth(DockPosition.LEFT);
        leftZone.setX(cx);
        leftZone.setY(cy);
        leftZone.setWidth(leftWidth);
        leftZone.setHeight(ch);

        // Zone RIGHT - largeur basée sur le conteneur RIGHT (si présent)
        Rectangle rightZone = dropZones.get(DockPosition.RIGHT);
        double rightWidth = getContainerWidth(DockPosition.RIGHT);
        rightZone.setX(cx + cw - rightWidth);
        rightZone.setY(cy);
        rightZone.setWidth(rightWidth);
        rightZone.setHeight(ch);
    }

    /**
     * Retourne la largeur d'un conteneur de dock, ou la largeur minimum si absent.
     */
    private double getContainerWidth(DockPosition position) {
        SplitPane container = dockContainers.get(position);
        if (container != null && container.getScene() != null && container.isVisible()) {
            double width = container.getWidth();
            if (width > 0) {
                return width;
            }
        }
        return MIN_ZONE_WIDTH;
    }

    /**
     * Trouve la zone de docking à la position donnée.
     */
    private DockPosition findZoneAt(double x, double y) {
        for (Map.Entry<DockPosition, Rectangle> entry : dropZones.entrySet()) {
            Rectangle rect = entry.getValue();
            if (rect.isVisible() && rect.contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Docke un panel à une position donnée.
     */
    public void dockPanel(BasePanel panel, DockPosition position) {
        removeFromCurrentContainer(panel);

        SplitPane targetContainer = dockContainers.get(position);
        if (targetContainer != null) {
            targetContainer.getItems().add(panel);
            panel.setDockPosition(position);
            redistributeDividers(targetContainer);
        }
    }

    /**
     * Retire un panel de son conteneur actuel.
     */
    private void removeFromCurrentContainer(BasePanel panel) {
        for (SplitPane container : dockContainers.values()) {
            if (container.getItems().contains(panel)) {
                container.getItems().remove(panel);
                redistributeDividers(container);
                break;
            }
        }
    }

    /**
     * Redistribue les diviseurs d'un SplitPane de manière égale.
     */
    private void redistributeDividers(SplitPane splitPane) {
        int count = splitPane.getItems().size();
        if (count <= 1) return;

        double[] positions = new double[count - 1];
        for (int i = 0; i < count - 1; i++) {
            positions[i] = (double)(i + 1) / count;
        }
        splitPane.setDividerPositions(positions);
    }

    /**
     * Retourne le panel actuellement en cours de drag.
     */
    public BasePanel getDraggingPanel() {
        return draggingPanel;
    }

    /**
     * Retourne true si un drag est en cours.
     */
    public boolean isDragging() {
        return draggingPanel != null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Gestion des zones de séparation (insertion entre panels)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Crée les zones de séparation pour tous les conteneurs.
     */
    private void createDividerZones() {
        // Nettoyer les anciennes zones
        for (DividerDropZone dz : dividerZones) {
            dockOverlay.getChildren().remove(dz.rectangle);
        }
        dividerZones.clear();

        // Créer des zones pour chaque conteneur
        for (Map.Entry<DockPosition, SplitPane> entry : dockContainers.entrySet()) {
            SplitPane container = entry.getValue();
            if (container == null || container.getScene() == null || !container.isVisible()) {
                continue;
            }

            createDividerZonesForContainer(container);
        }
    }

    /**
     * Crée les zones de séparation pour un conteneur donné.
     */
    private void createDividerZonesForContainer(SplitPane container) {
        int itemCount = container.getItems().size();
        if (itemCount < 1) return;

        // Obtenir les bounds du conteneur dans l'overlay
        Bounds containerBounds = container.localToScene(container.getBoundsInLocal());
        if (containerBounds == null) return;

        Point2D topLeft = dockOverlay.sceneToLocal(containerBounds.getMinX(), containerBounds.getMinY());
        if (topLeft == null) return;

        double containerX = topLeft.getX();
        double containerY = topLeft.getY();
        double containerW = containerBounds.getWidth();
        double containerH = containerBounds.getHeight();

        boolean isVertical = container.getOrientation() == Orientation.VERTICAL;

        // Zone avant le premier panel (index 0)
        Rectangle firstZone = createDividerRectangle();
        if (isVertical) {
            firstZone.setX(containerX);
            firstZone.setY(containerY);
            firstZone.setWidth(containerW);
            firstZone.setHeight(DIVIDER_ZONE_THICKNESS);
        } else {
            firstZone.setX(containerX);
            firstZone.setY(containerY);
            firstZone.setWidth(DIVIDER_ZONE_THICKNESS);
            firstZone.setHeight(containerH);
        }
        dividerZones.add(new DividerDropZone(firstZone, container, 0));
        dockOverlay.getChildren().add(firstZone);

        // Zones entre les panels existants
        double[] dividerPositions = container.getDividerPositions();
        for (int i = 0; i < dividerPositions.length; i++) {
            double pos = dividerPositions[i];
            Rectangle dividerZone = createDividerRectangle();

            if (isVertical) {
                double dividerY = containerY + pos * containerH - DIVIDER_ZONE_THICKNESS / 2;
                dividerZone.setX(containerX);
                dividerZone.setY(dividerY);
                dividerZone.setWidth(containerW);
                dividerZone.setHeight(DIVIDER_ZONE_THICKNESS);
            } else {
                double dividerX = containerX + pos * containerW - DIVIDER_ZONE_THICKNESS / 2;
                dividerZone.setX(dividerX);
                dividerZone.setY(containerY);
                dividerZone.setWidth(DIVIDER_ZONE_THICKNESS);
                dividerZone.setHeight(containerH);
            }

            // Index d'insertion = position du divider + 1
            dividerZones.add(new DividerDropZone(dividerZone, container, i + 1));
            dockOverlay.getChildren().add(dividerZone);
        }

        // Zone après le dernier panel
        Rectangle lastZone = createDividerRectangle();
        if (isVertical) {
            lastZone.setX(containerX);
            lastZone.setY(containerY + containerH - DIVIDER_ZONE_THICKNESS);
            lastZone.setWidth(containerW);
            lastZone.setHeight(DIVIDER_ZONE_THICKNESS);
        } else {
            lastZone.setX(containerX + containerW - DIVIDER_ZONE_THICKNESS);
            lastZone.setY(containerY);
            lastZone.setWidth(DIVIDER_ZONE_THICKNESS);
            lastZone.setHeight(containerH);
        }
        dividerZones.add(new DividerDropZone(lastZone, container, itemCount));
        dockOverlay.getChildren().add(lastZone);
    }

    /**
     * Crée un rectangle pour une zone de séparation.
     */
    private Rectangle createDividerRectangle() {
        Rectangle rect = new Rectangle();
        rect.setFill(DIVIDER_COLOR);
        rect.setOpacity(ZONE_OPACITY);
        rect.setArcWidth(4);
        rect.setArcHeight(4);
        rect.setStroke(DIVIDER_COLOR.darker());
        rect.setStrokeWidth(2);
        rect.setVisible(false);
        return rect;
    }

    /**
     * Met à jour la visibilité des zones de séparation.
     */
    private void updateDividerZoneVisibility(double x, double y) {
        for (DividerDropZone dz : dividerZones) {
            Rectangle rect = dz.rectangle;
            // Calculer la distance au centre de la zone
            double centerX = rect.getX() + rect.getWidth() / 2;
            double centerY = rect.getY() + rect.getHeight() / 2;
            double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

            // Visible si proche
            rect.setVisible(distance < ZONE_ACTIVATION_DISTANCE);
        }
    }

    /**
     * Trouve la zone de séparation à la position donnée.
     */
    private DividerDropZone findDividerZoneAt(double x, double y) {
        for (DividerDropZone dz : dividerZones) {
            Rectangle rect = dz.rectangle;
            if (rect.isVisible() && rect.contains(x, y)) {
                return dz;
            }
        }
        return null;
    }
}
