package ui;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import config.AppConfig;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Onglet d'accueil affichant la liste des projets récents.
 */
public class WelcomeTab extends Tab {

    private static final ResourceBundle messages = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());

    private Consumer<File> onProjectSelected;

    /**
     * Crée l'onglet Welcome.
     *
     * @param recentProjects La liste des chemins des projets récents
     * @param maxItems Le nombre maximum de projets à afficher
     * @param config La configuration de l'application
     */
    public WelcomeTab(List<String> recentProjects, int maxItems, AppConfig config) {
        super(messages.getString("welcome.tab.title"));
        setClosable(true);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        // Titre
        Label titleLabel = new Label(messages.getString("welcome.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 28));

        // Sous-titre
        Label subtitleLabel = new Label(messages.getString("welcome.subtitle"));
        subtitleLabel.setFont(Font.font("System", 14));
        subtitleLabel.setStyle("-fx-text-fill: #666;");

        content.getChildren().addAll(titleLabel, subtitleLabel);

        // Section projets récents
        if (!recentProjects.isEmpty()) {
            Label recentLabel = new Label(messages.getString("welcome.recentProjects"));
            recentLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
            recentLabel.setPadding(new Insets(20, 0, 10, 0));
            content.getChildren().add(recentLabel);

            VBox projectsList = new VBox(8);
            projectsList.setAlignment(Pos.CENTER);

            int count = 0;
            for (String path : recentProjects) {
                if (count >= maxItems) {
                    break;
                }
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    Hyperlink projectLink = createProjectRow(dir);
                    projectsList.getChildren().add(projectLink);
                    count++;
                }
            }

            if (projectsList.getChildren().isEmpty()) {
                Label noProjectLabel = new Label(messages.getString("welcome.noRecentProjects"));
                noProjectLabel.setStyle("-fx-text-fill: #999;");
                projectsList.getChildren().add(noProjectLabel);
            }

            content.getChildren().add(projectsList);
        } else {
            Label noProjectLabel = new Label(messages.getString("welcome.noRecentProjects"));
            noProjectLabel.setStyle("-fx-text-fill: #999;");
            noProjectLabel.setPadding(new Insets(20, 0, 0, 0));
            content.getChildren().add(noProjectLabel);
        }

        root.setCenter(content);

        // Checkbox en bas à droite
        CheckBox showOnStartupCheck = new CheckBox(messages.getString("welcome.showOnStartup"));
        showOnStartupCheck.setSelected(config.isShowWelcomePage());
        showOnStartupCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            config.setShowWelcomePage(newVal);
            config.save();
        });

        HBox bottomBar = new HBox(showOnStartupCheck);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottomBar);

        setContent(root);
    }

    /**
     * Crée une ligne pour un projet récent.
     */
    private Hyperlink createProjectRow(File dir) {
        Hyperlink link = new Hyperlink(dir.getAbsolutePath());
        link.setFont(Font.font("System", 13));
        link.setOnAction(e -> {
            if (onProjectSelected != null) {
                onProjectSelected.accept(dir);
            }
        });
        return link;
    }

    /**
     * Définit le callback appelé quand un projet est sélectionné.
     *
     * @param handler Le handler de sélection
     */
    public void setOnProjectSelected(Consumer<File> handler) {
        this.onProjectSelected = handler;
    }
}
