package ui;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import config.ThemeManager;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Splash screen et dialogue "À propos" pour MarkNote.
 *
 * <p>En mode splash (au démarrage) : fenêtre sans décoration, se ferme
 * automatiquement après {@value #SPLASH_DURATION} secondes ou sur clic.
 * Lorsqu'elle se ferme, le {@link Runnable} passé via {@link #setOnClosed}
 * est exécuté.</p>
 *
 * <p>En mode about (menu Aide → À propos) : fenêtre modale décorée avec un
 * bouton Fermer ; pas d'auto-fermeture.</p>
 */
public class SplashScreen {

    /** Version de l'application affichée dans le splash / about. */
    public static final String APP_VERSION = "0.0.4";

    /** Durée d'affichage automatique du splash en secondes. */
    private static final double SPLASH_DURATION = 3.0;

    private final Stage stage;
    private PauseTransition autoClose;
    private Runnable onClosed;

    // -------------------------------------------------------------------------
    // Constructeurs
    // -------------------------------------------------------------------------

    /**
     * Crée un splash screen en mode démarrage (sans décoration, sans propriétaire).
     *
     * @param messages  bundle de messages i18n
     * @param themeName nom du thème applicatif courant (ex. "dark", "light")
     */
    public SplashScreen(ResourceBundle messages, String themeName) {
        this(messages, null, false, themeName);
    }

    /**
     * Constructeur complet.
     *
     * @param messages   bundle de messages i18n
     * @param owner      fenêtre propriétaire (utilisée en mode about seulement)
     * @param aboutMode  {@code true} pour le mode "À propos" (modal, bouton Fermer)
     * @param themeName  nom du thème applicatif courant
     */
    public SplashScreen(ResourceBundle messages, Window owner, boolean aboutMode, String themeName) {
        stage = new Stage();
        if (aboutMode) {
            stage.initModality(Modality.WINDOW_MODAL);
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setTitle(messages.getString("about.title"));
        } else {
            stage.initStyle(StageStyle.UNDECORATED);
        }

        VBox root = buildContent(messages, aboutMode);
        Scene scene = new Scene(root);

        // Appliquer le thème applicatif courant à la scène du splash
        if (themeName != null) {
            String themeCssUrl = ThemeManager.getInstance().getThemeCssUrl(themeName);
            if (themeCssUrl != null) {
                scene.getStylesheets().add(themeCssUrl);
            }
        }

        stage.setScene(scene);

        if (!aboutMode) {
            // Clic n'importe où → fermeture anticipée
            scene.setOnMouseClicked(e -> closeSplash());
            // Fermeture automatique après SPLASH_DURATION secondes
            autoClose = new PauseTransition(Duration.seconds(SPLASH_DURATION));
            autoClose.setOnFinished(e -> closeSplash());
            stage.setOnShown(e -> autoClose.play());
        }

        stage.centerOnScreen();
    }

    // -------------------------------------------------------------------------
    // Construction du contenu visuel
    // -------------------------------------------------------------------------

    private VBox buildContent(ResourceBundle messages, boolean aboutMode) {
        // Titre de l'application
        Label nameLabel = new Label("MarkNote");
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 42));
        nameLabel.getStyleClass().add("splash-title");

        // Sous-titre
        Label subtitleLabel = new Label(messages.getString("welcome.subtitle"));
        subtitleLabel.getStyleClass().add("splash-subtitle");

        Separator sep1 = new Separator();

        // Version
        String versionText = MessageFormat.format(
                messages.getString("about.version"), APP_VERSION);
        Label versionLabel = new Label(versionText);
        versionLabel.getStyleClass().add("splash-version");

        // Auteur
        Label authorLabel = new Label(messages.getString("about.author"));
        authorLabel.getStyleClass().add("splash-info");

        // Contact
        Label contactLabel = new Label(messages.getString("about.contact"));
        contactLabel.getStyleClass().add("splash-info-secondary");

        Separator sep2 = new Separator();

        // Copyright
        Label copyrightLabel = new Label(messages.getString("about.copyright"));
        copyrightLabel.getStyleClass().add("splash-footer");

        // Dépôt
        Label repoLabel = new Label(messages.getString("about.repository"));
        repoLabel.getStyleClass().add("splash-footer");

        VBox root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40, 70, 30, 70));
        root.setPrefWidth(480);
        root.getStyleClass().add("splash-root");

        root.getChildren().addAll(
                nameLabel,
                subtitleLabel,
                sep1,
                versionLabel,
                authorLabel,
                contactLabel,
                sep2,
                copyrightLabel,
                repoLabel);

        if (aboutMode) {
            Button closeBtn = new Button(messages.getString("splash.close"));
            closeBtn.setDefaultButton(true);
            closeBtn.setOnAction(e -> stage.close());
            VBox.setMargin(closeBtn, new Insets(12, 0, 0, 0));
            root.getChildren().add(closeBtn);
        } else {
            Label hintLabel = new Label(messages.getString("splash.click"));
            hintLabel.getStyleClass().add("splash-hint");
            VBox.setMargin(hintLabel, new Insets(12, 0, 0, 0));
            root.getChildren().add(hintLabel);
        }

        return root;
    }

    // -------------------------------------------------------------------------
    // Contrôle
    // -------------------------------------------------------------------------

    private void closeSplash() {
        if (autoClose != null) {
            autoClose.stop();
        }
        stage.close();
        if (onClosed != null) {
            // Defer to after the current animation/layout pulse so that any
            // showAndWait() called from the callback is not blocked by JavaFX.
            Platform.runLater(onClosed);
        }
    }

    /**
     * Définit l'action à exécuter lorsque le splash se ferme
     * (par timeout ou clic). Non utilisé en mode about.
     *
     * @param onClosed callback appelé après la fermeture
     */
    public void setOnClosed(Runnable onClosed) {
        this.onClosed = onClosed;
    }

    /** Affiche le splash de manière non bloquante. */
    public void show() {
        stage.show();
    }

    /** Affiche la fenêtre de manière bloquante (mode about). */
    public void showAndWait() {
        stage.showAndWait();
    }
}
