package ui;

import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Onglet de prévisualisation d'image.
 * Affiche une image avec zoom (molette), déplacement (clic molette + mouvement),
 * et informations sur l'image.
 */
public class ImagePreviewTab extends Tab {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private static final int MAX_TAB_NAME_LENGTH = 15;
    
    /** Extensions de fichiers image supportées */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg"
    );

    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;
    private static final double ZOOM_FACTOR = 1.1;

    private final File file;
    private final ImageView imageView;
    private final Label zoomLabel;
    private final ScrollPane scrollPane;
    
    private double currentZoom = 1.0;
    private double dragStartX;
    private double dragStartY;
    private double scrollStartH;
    private double scrollStartV;
    private boolean isPanning = false;

    /**
     * Crée un onglet de prévisualisation pour une image.
     *
     * @param file Le fichier image à afficher
     */
    public ImagePreviewTab(File file) {
        super(truncateTabName(getMessages().getString("image.preview.title") + " " + file.getName()));
        setTooltip(new Tooltip(file.getAbsolutePath()));
        
        this.file = file;

        // Charger l'image
        Image image = new Image(file.toURI().toString());
        imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        
        // Conteneur pour l'image (permet le centrage et le zoom)
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setStyle("-fx-background-color: #2d2d2d;");
        imageContainer.setMinWidth(image.getWidth());
        imageContainer.setMinHeight(image.getHeight());
        
        // ScrollPane avec barres de défilement
        scrollPane = new ScrollPane(imageContainer);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(false); // On gère le pan manuellement
        scrollPane.setStyle("-fx-background-color: #2d2d2d;");
        
        // Label de zoom en surimpression
        zoomLabel = new Label("100%");
        zoomLabel.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; " +
                          "-fx-padding: 8 16; -fx-background-radius: 4; -fx-font-size: 14;");
        zoomLabel.setVisible(false);
        
        // Conteneur principal avec surimpression du zoom
        StackPane mainContainer = new StackPane(scrollPane, zoomLabel);
        StackPane.setAlignment(zoomLabel, Pos.CENTER);
        
        // Bandeau d'informations en haut
        String extension = getFileExtension(file).toUpperCase();
        String infoText = String.format("%s | %d x %d px", 
                extension, (int) image.getWidth(), (int) image.getHeight());
        Label infoLabel = new Label(infoText);
        infoLabel.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #cccccc; " +
                          "-fx-padding: 4 10; -fx-font-size: 12;");
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        infoLabel.setAlignment(Pos.CENTER_LEFT);
        
        BorderPane rootPane = new BorderPane();
        rootPane.setTop(infoLabel);
        rootPane.setCenter(mainContainer);
        
        // Gestion du zoom avec la molette
        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleZoom);
        
        // Gestion du pan avec clic molette
        scrollPane.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.MIDDLE) {
                isPanning = true;
                dragStartX = event.getScreenX();
                dragStartY = event.getScreenY();
                scrollStartH = scrollPane.getHvalue();
                scrollStartV = scrollPane.getVvalue();
                event.consume();
            }
        });
        
        scrollPane.setOnMouseDragged(event -> {
            if (isPanning) {
                double deltaX = event.getScreenX() - dragStartX;
                double deltaY = event.getScreenY() - dragStartY;
                
                // Calculer le nouveau scroll en fonction du déplacement
                double hRange = scrollPane.getHmax() - scrollPane.getHmin();
                double vRange = scrollPane.getVmax() - scrollPane.getVmin();
                
                if (hRange > 0) {
                    double newH = scrollStartH - (deltaX / scrollPane.getWidth()) * 2;
                    scrollPane.setHvalue(Math.max(0, Math.min(1, newH)));
                }
                if (vRange > 0) {
                    double newV = scrollStartV - (deltaY / scrollPane.getHeight()) * 2;
                    scrollPane.setVvalue(Math.max(0, Math.min(1, newV)));
                }
                event.consume();
            }
        });
        
        scrollPane.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.MIDDLE) {
                isPanning = false;
                event.consume();
            }
        });
        
        setContent(rootPane);
    }

    /**
     * Gère le zoom avec la molette de la souris.
     */
    private void handleZoom(ScrollEvent event) {
        if (event.getDeltaY() == 0) return;
        
        event.consume();
        
        double zoomFactor = event.getDeltaY() > 0 ? ZOOM_FACTOR : 1 / ZOOM_FACTOR;
        double newZoom = currentZoom * zoomFactor;
        
        // Limiter le zoom
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        
        if (newZoom != currentZoom) {
            currentZoom = newZoom;
            
            // Appliquer le zoom
            imageView.setScaleX(currentZoom);
            imageView.setScaleY(currentZoom);
            
            // Mettre à jour la taille du conteneur pour le scroll
            Image img = imageView.getImage();
            ((StackPane) scrollPane.getContent()).setMinWidth(img.getWidth() * currentZoom);
            ((StackPane) scrollPane.getContent()).setMinHeight(img.getHeight() * currentZoom);
            
            // Afficher le niveau de zoom
            showZoomLevel();
        }
    }

    /**
     * Affiche temporairement le niveau de zoom.
     */
    private void showZoomLevel() {
        zoomLabel.setText(String.format("%.0f%%", currentZoom * 100));
        zoomLabel.setVisible(true);
        zoomLabel.setOpacity(1.0);
        
        // Animation de fondu
        FadeTransition fade = new FadeTransition(Duration.millis(1500), zoomLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setDelay(Duration.millis(500));
        fade.setOnFinished(e -> zoomLabel.setVisible(false));
        fade.play();
    }

    /**
     * Retourne le fichier image associé.
     *
     * @return Le fichier image
     */
    public File getFile() {
        return file;
    }

    /**
     * Vérifie si un fichier est une image supportée.
     *
     * @param file Le fichier à vérifier
     * @return true si c'est une image supportée
     */
    public static boolean isImageFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String extension = getFileExtension(file);
        return IMAGE_EXTENSIONS.contains(extension);
    }

    /**
     * Retourne l'extension d'un fichier en minuscules.
     */
    private static String getFileExtension(File file) {
        String name = file.getName().toLowerCase();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return name.substring(dotIndex + 1);
    }

    private static String truncateTabName(String name) {
        if (name.length() <= MAX_TAB_NAME_LENGTH) return name;
        return name.substring(0, MAX_TAB_NAME_LENGTH - 1) + "\u2026";
    }
}
