package ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;

import utils.IndexService.IndexEntry;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Panneau affichant un diagramme réseau des liens entre documents.
 * <p>
 * Chaque nœud représente un document (icône doc), chaque ligne représente
 * un lien front matter entre deux documents. Les tags partagés apparaissent
 * comme nœuds intermédiaires sur les arêtes correspondantes.
 * </p>
 * <p>
 * La disposition utilise un algorithme de force-directed layout pour
 * maintenir les distances entre nœuds de manière équilibrée.
 * </p>
 */
public class VisualLinkPanel extends BasePanel {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    // ── Physique du layout ──────────────────────────────────────
    private static final double REPULSION = 2000.0;
    private static final double ATTRACTION = 0.02;
    private static final double DAMPING = 0.85;
    private static final double MIN_VELOCITY = 0.1;
    private static final double IDEAL_DISTANCE = 120.0;
    private static final double CENTER_GRAVITY = 0.005;

    // ── Rendu ───────────────────────────────────────────────────
    private static final double NODE_RADIUS = 14.0;
    private static final double TAG_RADIUS = 8.0;
    private static final Font DOC_FONT = Font.font("System", FontWeight.NORMAL, 10);
    private static final Font TAG_FONT = Font.font("System", FontWeight.NORMAL, 9);

    // ── Données ─────────────────────────────────────────────────
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Canvas canvas;
    private final Pane canvasContainer;
    private AnimationTimer timer;
    private boolean stable = false;
    private int stableFrames = 0;

    // ── Interaction ─────────────────────────────────────────────
    private GraphNode draggedNode = null;
    private double dragOffsetX, dragOffsetY;
    private double panX = 0, panY = 0;
    private double zoom = 1.0;
    private double lastMouseX, lastMouseY;
    private boolean panning = false;

    private Consumer<String> onDocumentClick;

    // ── Modèle interne ──────────────────────────────────────────

    enum NodeType { DOCUMENT, TAG }

    static class GraphNode {
        final String id;
        final String label;
        final NodeType type;
        double x, y;
        double vx, vy;
        boolean pinned = false;

        GraphNode(String id, String label, NodeType type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }
    }

    static class GraphEdge {
        final GraphNode source;
        final GraphNode target;

        GraphEdge(GraphNode source, GraphNode target) {
            this.source = source;
            this.target = target;
        }
    }

    // ── Constructeur ────────────────────────────────────────────

    public VisualLinkPanel() {
        super("networkdiagram.title", "networkdiagram.close.tooltip");

        canvas = new Canvas(400, 300);
        canvasContainer = new Pane(canvas);
        canvasContainer.setPadding(Insets.EMPTY);

        // Redimensionner le canvas avec le conteneur
        canvasContainer.widthProperty().addListener((o, ov, nv) -> {
            canvas.setWidth(nv.doubleValue());
            draw();
        });
        canvasContainer.heightProperty().addListener((o, ov, nv) -> {
            canvas.setHeight(nv.doubleValue());
            draw();
        });

        setupInteraction();
        setContent(canvasContainer);
        setPrefHeight(250);
    }

    // ── Interaction souris ──────────────────────────────────────

    private void setupInteraction() {
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                GraphNode hit = hitTest(e.getX(), e.getY());
                if (hit != null) {
                    draggedNode = hit;
                    draggedNode.pinned = true;
                    dragOffsetX = toWorldX(e.getX()) - hit.x;
                    dragOffsetY = toWorldY(e.getY()) - hit.y;
                }
            } else if (e.getButton() == MouseButton.MIDDLE) {
                panning = true;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (draggedNode != null) {
                draggedNode.x = toWorldX(e.getX()) - dragOffsetX;
                draggedNode.y = toWorldY(e.getY()) - dragOffsetY;
                draggedNode.vx = 0;
                draggedNode.vy = 0;
                stable = false;
                stableFrames = 0;
                startSimulation();
            } else if (panning) {
                panX += e.getX() - lastMouseX;
                panY += e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                draw();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (draggedNode != null) {
                draggedNode.pinned = false;
                draggedNode = null;
            }
            panning = false;
        });

        canvas.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                GraphNode hit = hitTest(e.getX(), e.getY());
                if (hit != null && hit.type == NodeType.DOCUMENT && onDocumentClick != null) {
                    onDocumentClick.accept(hit.id);
                }
            }
        });

        canvas.setOnScroll((ScrollEvent e) -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            // Zoom centré sur la position de la souris
            double mx = e.getX(), my = e.getY();
            panX = mx - factor * (mx - panX);
            panY = my - factor * (my - panY);
            zoom *= factor;
            zoom = Math.max(0.2, Math.min(5.0, zoom));
            draw();
        });
    }

    private double toWorldX(double screenX) {
        return (screenX - panX) / zoom;
    }

    private double toWorldY(double screenY) {
        return (screenY - panY) / zoom;
    }

    private double toScreenX(double worldX) {
        return worldX * zoom + panX;
    }

    private double toScreenY(double worldY) {
        return worldY * zoom + panY;
    }

    private GraphNode hitTest(double screenX, double screenY) {
        double wx = toWorldX(screenX);
        double wy = toWorldY(screenY);
        for (GraphNode node : nodes) {
            double r = node.type == NodeType.DOCUMENT ? NODE_RADIUS : TAG_RADIUS;
            double dx = wx - node.x;
            double dy = wy - node.y;
            if (dx * dx + dy * dy <= r * r * 1.5) {
                return node;
            }
        }
        return null;
    }

    // ── Mise à jour des données ─────────────────────────────────

    /**
     * Met à jour le diagramme avec les entrées d'index courantes.
     *
     * @param entries la liste des entrées d'index
     */
    public void updateDiagram(List<IndexEntry> entries) {
        nodes.clear();
        edges.clear();

        if (entries == null || entries.isEmpty()) {
            stopSimulation();
            draw();
            return;
        }

        // 1. Créer les nœuds document (ceux qui ont un UUID)
        Map<String, GraphNode> nodeByUuid = new HashMap<>();
        Map<String, GraphNode> nodeByPath = new HashMap<>();

        for (IndexEntry entry : entries) {
            String id = !entry.getUuid().isBlank() ? entry.getUuid() : entry.getRelativePath();
            GraphNode node = new GraphNode(entry.getRelativePath(), entry.getDisplayTitle(), NodeType.DOCUMENT);
            nodes.add(node);
            nodeByPath.put(entry.getRelativePath(), node);
            if (!entry.getUuid().isBlank()) {
                nodeByUuid.put(entry.getUuid(), node);
            }
        }

        // 2. Créer les arêtes directes (doc → doc via links)
        Set<String> edgeKeys = new HashSet<>();
        for (IndexEntry entry : entries) {
            GraphNode sourceNode = nodeByPath.get(entry.getRelativePath());
            if (sourceNode == null) continue;

            for (String linkUuid : entry.getLinks()) {
                GraphNode targetNode = nodeByUuid.get(linkUuid);
                if (targetNode != null && targetNode != sourceNode) {
                    String key = edgeKey(sourceNode, targetNode);
                    if (edgeKeys.add(key)) {
                        edges.add(new GraphEdge(sourceNode, targetNode));
                    }
                }
            }
        }

        // 3. Créer les nœuds tags partagés et les arêtes correspondantes
        // Collecter les tags partagés par au moins 2 documents
        Map<String, List<GraphNode>> tagToNodes = new HashMap<>();
        for (IndexEntry entry : entries) {
            GraphNode docNode = nodeByPath.get(entry.getRelativePath());
            if (docNode == null) continue;
            for (String tag : entry.getTags()) {
                String normalized = tag.toLowerCase().trim();
                if (!normalized.isEmpty()) {
                    tagToNodes.computeIfAbsent(normalized, k -> new ArrayList<>()).add(docNode);
                }
            }
        }

        for (Map.Entry<String, List<GraphNode>> tagEntry : tagToNodes.entrySet()) {
            List<GraphNode> docNodes = tagEntry.getValue();
            if (docNodes.size() < 2) continue; // Only show shared tags

            String tagName = tagEntry.getKey();
            GraphNode tagNode = new GraphNode("tag:" + tagName, tagName, NodeType.TAG);
            nodes.add(tagNode);

            for (GraphNode docNode : docNodes) {
                String key = edgeKey(tagNode, docNode);
                if (edgeKeys.add(key)) {
                    edges.add(new GraphEdge(tagNode, docNode));
                }
            }
        }

        // 4. Remove isolated nodes (no edges)
        Set<GraphNode> connectedNodes = new HashSet<>();
        for (GraphEdge edge : edges) {
            connectedNodes.add(edge.source);
            connectedNodes.add(edge.target);
        }
        nodes.removeIf(n -> !connectedNodes.contains(n));

        // 5. Initialiser les positions aléatoirement
        double cx = canvas.getWidth() / 2.0 / zoom;
        double cy = canvas.getHeight() / 2.0 / zoom;
        for (GraphNode node : nodes) {
            node.x = cx + (Math.random() - 0.5) * 200;
            node.y = cy + (Math.random() - 0.5) * 200;
            node.vx = 0;
            node.vy = 0;
        }

        // Reset view
        panX = 0;
        panY = 0;
        zoom = 1.0;
        stable = false;
        stableFrames = 0;
        startSimulation();
    }

    private String edgeKey(GraphNode a, GraphNode b) {
        return a.id.compareTo(b.id) < 0 ? a.id + "|" + b.id : b.id + "|" + a.id;
    }

    // ── Simulation force-directed ───────────────────────────────

    private void startSimulation() {
        if (timer != null) return;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (nodes.isEmpty()) {
                    stopSimulation();
                    return;
                }
                step();
                draw();
                if (stable) {
                    stableFrames++;
                    if (stableFrames > 60) {
                        stopSimulation();
                    }
                }
            }
        };
        timer.start();
    }

    private void stopSimulation() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        draw();
    }

    private void step() {
        double maxVelocity = 0;
        double cx = canvas.getWidth() / 2.0 / zoom;
        double cy = canvas.getHeight() / 2.0 / zoom;

        // Repulsion entre tous les nœuds
        for (int i = 0; i < nodes.size(); i++) {
            GraphNode a = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                GraphNode b = nodes.get(j);
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;

                double force = REPULSION / (dist * dist);
                double fx = force * dx / dist;
                double fy = force * dy / dist;

                if (!a.pinned) { a.vx += fx; a.vy += fy; }
                if (!b.pinned) { b.vx -= fx; b.vy -= fy; }
            }
        }

        // Attraction le long des arêtes
        for (GraphEdge edge : edges) {
            double dx = edge.target.x - edge.source.x;
            double dy = edge.target.y - edge.source.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 1) dist = 1;

            double force = ATTRACTION * (dist - IDEAL_DISTANCE);
            double fx = force * dx / dist;
            double fy = force * dy / dist;

            if (!edge.source.pinned) { edge.source.vx += fx; edge.source.vy += fy; }
            if (!edge.target.pinned) { edge.target.vx -= fx; edge.target.vy -= fy; }
        }

        // Gravité vers le centre
        for (GraphNode node : nodes) {
            if (node.pinned) continue;
            node.vx += (cx - node.x) * CENTER_GRAVITY;
            node.vy += (cy - node.y) * CENTER_GRAVITY;
        }

        // Mise à jour des positions
        for (GraphNode node : nodes) {
            if (node.pinned) continue;

            node.vx *= DAMPING;
            node.vy *= DAMPING;

            double vel = Math.sqrt(node.vx * node.vx + node.vy * node.vy);
            // Limiter la vélocité max
            if (vel > 10) {
                node.vx = node.vx / vel * 10;
                node.vy = node.vy / vel * 10;
            }

            node.x += node.vx;
            node.y += node.vy;

            maxVelocity = Math.max(maxVelocity, vel);
        }

        stable = maxVelocity < MIN_VELOCITY;
    }

    // ── Dessin ──────────────────────────────────────────────────

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Fond
        gc.clearRect(0, 0, w, h);

        if (nodes.isEmpty()) {
            gc.setFont(Font.font("System", FontWeight.NORMAL, 12));
            gc.setFill(Color.GRAY);
            String emptyText = bundle.getString("networkdiagram.empty");
            Text text = new Text(emptyText);
            text.setFont(gc.getFont());
            double tw = text.getLayoutBounds().getWidth();
            gc.fillText(emptyText, (w - tw) / 2, h / 2);
            return;
        }

        // Arêtes
        gc.setLineWidth(1.2);
        for (GraphEdge edge : edges) {
            double sx = toScreenX(edge.source.x);
            double sy = toScreenY(edge.source.y);
            double tx = toScreenX(edge.target.x);
            double ty = toScreenY(edge.target.y);

            // Couleur différente si un des nœuds est un tag
            if (edge.source.type == NodeType.TAG || edge.target.type == NodeType.TAG) {
                gc.setStroke(Color.web("#8888aa", 0.5));
                gc.setLineDashes(4, 3);
            } else {
                gc.setStroke(Color.web("#555555", 0.8));
                gc.setLineDashes();
            }

            gc.strokeLine(sx, sy, tx, ty);
        }
        gc.setLineDashes(); // reset

        // Nœuds
        for (GraphNode node : nodes) {
            double sx = toScreenX(node.x);
            double sy = toScreenY(node.y);

            if (node.type == NodeType.DOCUMENT) {
                drawDocumentNode(gc, sx, sy, node.label);
            } else {
                drawTagNode(gc, sx, sy, node.label);
            }
        }
    }

    /**
     * Dessine un nœud document : icône doc stylisée + label.
     */
    private void drawDocumentNode(GraphicsContext gc, double x, double y, String label) {
        double r = NODE_RADIUS * zoom;
        double iconW = r * 1.2;
        double iconH = r * 1.6;
        double foldSize = iconW * 0.3;

        // Ombre
        gc.setFill(Color.web("#00000030"));
        gc.fillRoundRect(x - iconW / 2 + 1.5, y - iconH / 2 + 1.5, iconW, iconH, 2, 2);

        // Corps du document
        gc.setFill(Color.web("#e8e8e8"));
        gc.setStroke(Color.web("#555555"));
        gc.setLineWidth(1.0);

        // Rectangle principal
        gc.fillRoundRect(x - iconW / 2, y - iconH / 2, iconW, iconH, 2, 2);
        gc.strokeRoundRect(x - iconW / 2, y - iconH / 2, iconW, iconH, 2, 2);

        // Coin replié
        double fx = x + iconW / 2 - foldSize;
        double fy = y - iconH / 2;
        gc.setFill(Color.web("#cccccc"));
        gc.fillPolygon(
            new double[]{fx, x + iconW / 2, fx},
            new double[]{fy, fy + foldSize, fy + foldSize},
            3
        );
        gc.strokePolygon(
            new double[]{fx, x + iconW / 2, fx},
            new double[]{fy, fy + foldSize, fy + foldSize},
            3
        );

        // Lignes de texte stylisées dans l'icône
        gc.setStroke(Color.web("#999999"));
        gc.setLineWidth(0.5);
        double lineY = y - iconH / 2 + foldSize + 3;
        double lineStartX = x - iconW / 2 + 3;
        double lineEndX = x + iconW / 2 - 3;
        for (int i = 0; i < 3 && lineY < y + iconH / 2 - 2; i++) {
            gc.strokeLine(lineStartX, lineY, lineEndX - (i == 2 ? foldSize : 0), lineY);
            lineY += 3;
        }

        // Label du document
        gc.setFont(DOC_FONT);
        gc.setFill(Color.web("#333333"));
        String truncated = truncateLabel(label, 16);
        Text text = new Text(truncated);
        text.setFont(DOC_FONT);
        double tw = text.getLayoutBounds().getWidth();
        gc.fillText(truncated, x - tw / 2, y + iconH / 2 + 12);
    }

    /**
     * Dessine un nœud tag : pastille colorée + label.
     */
    private void drawTagNode(GraphicsContext gc, double x, double y, String label) {
        double r = TAG_RADIUS * zoom;

        // Pastille du tag
        gc.setFill(Color.web("#6ab0f3", 0.7));
        gc.fillOval(x - r, y - r, r * 2, r * 2);
        gc.setStroke(Color.web("#4a90d9"));
        gc.setLineWidth(1.0);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);

        // # dans la pastille
        gc.setFont(Font.font("System", FontWeight.BOLD, 8 * zoom));
        gc.setFill(Color.WHITE);
        Text hash = new Text("#");
        hash.setFont(gc.getFont());
        double hw = hash.getLayoutBounds().getWidth();
        double hh = hash.getLayoutBounds().getHeight();
        gc.fillText("#", x - hw / 2, y + hh / 4);

        // Label du tag
        gc.setFont(TAG_FONT);
        gc.setFill(Color.web("#4a90d9"));
        String truncated = truncateLabel(label, 14);
        Text text = new Text(truncated);
        text.setFont(TAG_FONT);
        double tw = text.getLayoutBounds().getWidth();
        gc.fillText(truncated, x - tw / 2, y + r + 11);
    }

    private static String truncateLabel(String label, int maxLen) {
        if (label == null) return "";
        if (label.length() <= maxLen) return label;
        return label.substring(0, maxLen - 1) + "\u2026";
    }

    // ── API publique ────────────────────────────────────────────

    /**
     * Définit le callback pour le double-clic sur un document.
     *
     * @param action callback recevant le chemin relatif du document
     */
    public void setOnDocumentClick(Consumer<String> action) {
        this.onDocumentClick = action;
    }
}
