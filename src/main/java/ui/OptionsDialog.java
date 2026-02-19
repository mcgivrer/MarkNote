package ui;

import java.util.Locale;
import java.util.ResourceBundle;

import config.AppConfig;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialogue de configuration de l'application.
 */
public class OptionsDialog {

    private static final ResourceBundle messages = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());

    private final Stage dialog;
    private final AppConfig config;
    private boolean saved = false;

    /**
     * Crée le dialogue d'options.
     *
     * @param owner  Le stage parent
     * @param config La configuration de l'application
     */
    public OptionsDialog(Stage owner, AppConfig config) {
        this.config = config;

        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle(messages.getString("options.title"));

        TabPane optionsTabs = new TabPane();
        optionsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Onglet Misc. ---
        Tab miscTab = new Tab(messages.getString("options.tab.misc"));

        GridPane miscGrid = new GridPane();
        miscGrid.setHgap(10);
        miscGrid.setVgap(10);
        miscGrid.setPadding(new Insets(20));

        // Nombre de fichiers récents
        Label recentLabel = new Label(messages.getString("options.recentCount"));
        Spinner<Integer> recentSpinner = new Spinner<>(1, 50, config.getMaxRecentItems());
        recentSpinner.setEditable(true);
        recentSpinner.setPrefWidth(100);
        miscGrid.add(recentLabel, 0, 0);
        miscGrid.add(recentSpinner, 1, 0);

        // Créer un document au démarrage
        Label openDocLabel = new Label(messages.getString("options.openDocOnStart"));
        CheckBox openDocCheck = new CheckBox();
        openDocCheck.setSelected(config.isOpenDocOnStart());
        miscGrid.add(openDocLabel, 0, 1);
        miscGrid.add(openDocCheck, 1, 1);

        // Rouvrir le dernier projet au démarrage
        Label reopenLabel = new Label(messages.getString("options.reopenLastProject"));
        CheckBox reopenCheck = new CheckBox();
        reopenCheck.setSelected(config.isReopenLastProject());
        miscGrid.add(reopenLabel, 0, 2);
        miscGrid.add(reopenCheck, 1, 2);

        // Afficher la page Welcome au démarrage
        Label welcomeLabel = new Label(messages.getString("options.showWelcomePage"));
        CheckBox welcomeCheck = new CheckBox();
        welcomeCheck.setSelected(config.isShowWelcomePage());
        miscGrid.add(welcomeLabel, 0, 3);
        miscGrid.add(welcomeCheck, 1, 3);

        miscTab.setContent(miscGrid);
        optionsTabs.getTabs().add(miscTab);

        // --- Boutons OK / Annuler ---
        Button okBtn = new Button(messages.getString("options.ok"));
        Button cancelBtn = new Button(messages.getString("options.cancel"));

        okBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);

        okBtn.setOnAction(e -> {
            config.setMaxRecentItems(recentSpinner.getValue());
            config.setOpenDocOnStart(openDocCheck.isSelected());
            config.setReopenLastProject(reopenCheck.isSelected());
            config.setShowWelcomePage(welcomeCheck.isSelected());
            config.save();
            saved = true;
            dialog.close();
        });

        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonBar = new HBox(10, okBtn, cancelBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10));

        BorderPane dialogRoot = new BorderPane();
        dialogRoot.setCenter(optionsTabs);
        dialogRoot.setBottom(buttonBar);

        Scene dialogScene = new Scene(dialogRoot, 450, 250);
        dialog.setScene(dialogScene);
    }

    /**
     * Affiche le dialogue et attend sa fermeture.
     *
     * @return true si l'utilisateur a cliqué sur OK
     */
    public boolean showAndWait() {
        dialog.showAndWait();
        return saved;
    }
}
