package ui;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import config.AppConfig;
import config.ThemeManager;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Dialogue de configuration de l'application.
 */
public class OptionsDialog {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    // Supported languages with their display names
    private static final String[][] LANGUAGES = {
        {"system", "System"},
        {"fr", "Français"},
        {"en", "English"},
        {"de", "Deutsch"},
        {"es", "Español"},
        {"it", "Italiano"}
    };

    private final Stage dialog;
    private final AppConfig config;
    private boolean saved = false;
    private Consumer<File> onOpenThemeFile;
    private Runnable onLanguageChanged;

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
        dialog.setTitle(getMessages().getString("options.title"));

        TabPane optionsTabs = new TabPane();
        optionsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Onglet Misc. ---
        Tab miscTab = new Tab(getMessages().getString("options.tab.misc"));

        GridPane miscGrid = new GridPane();
        miscGrid.setHgap(10);
        miscGrid.setVgap(10);
        miscGrid.setPadding(new Insets(20));

        // Nombre de fichiers récents
        Label recentLabel = new Label(getMessages().getString("options.recentCount"));
        Spinner<Integer> recentSpinner = new Spinner<>(1, 50, config.getMaxRecentItems());
        recentSpinner.setEditable(true);
        recentSpinner.setPrefWidth(100);
        miscGrid.add(recentLabel, 0, 0);
        miscGrid.add(recentSpinner, 1, 0);

        // Créer un document au démarrage
        Label openDocLabel = new Label(getMessages().getString("options.openDocOnStart"));
        CheckBox openDocCheck = new CheckBox();
        openDocCheck.setSelected(config.isOpenDocOnStart());
        miscGrid.add(openDocLabel, 0, 1);
        miscGrid.add(openDocCheck, 1, 1);

        // Rouvrir le dernier projet au démarrage
        Label reopenLabel = new Label(getMessages().getString("options.reopenLastProject"));
        CheckBox reopenCheck = new CheckBox();
        reopenCheck.setSelected(config.isReopenLastProject());
        miscGrid.add(reopenLabel, 0, 2);
        miscGrid.add(reopenCheck, 1, 2);

        // Afficher la page Welcome au démarrage
        Label welcomeLabel = new Label(getMessages().getString("options.showWelcomePage"));
        CheckBox welcomeCheck = new CheckBox();
        welcomeCheck.setSelected(config.isShowWelcomePage());
        miscGrid.add(welcomeLabel, 0, 3);
        miscGrid.add(welcomeCheck, 1, 3);

        // Sélecteur de langue
        Label languageLabel = new Label(getMessages().getString("options.language"));
        ComboBox<String> languageCombo = new ComboBox<>();
        for (String[] lang : LANGUAGES) {
            languageCombo.getItems().add(lang[0]);
        }
        languageCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                if (code == null) return "";
                for (String[] lang : LANGUAGES) {
                    if (lang[0].equals(code)) {
                        return lang[1];
                    }
                }
                return code;
            }

            @Override
            public String fromString(String string) {
                for (String[] lang : LANGUAGES) {
                    if (lang[1].equals(string)) {
                        return lang[0];
                    }
                }
                return "system";
            }
        });
        languageCombo.setValue(config.getLanguage());
        String initialLanguage = config.getLanguage();
        languageCombo.setOnAction(e -> {
            String selected = languageCombo.getValue();
            if (selected != null && !selected.equals(initialLanguage)) {
                config.setLanguage(selected);
                config.save();
                // Show restart info
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.initOwner(dialog);
                info.setTitle(getMessages().getString("options.language.restart.title"));
                info.setHeaderText(getMessages().getString("options.language.restart.header"));
                info.setContentText(getMessages().getString("options.language.restart.content"));
                info.showAndWait().ifPresent(result -> {
                    if (onLanguageChanged != null) {
                        dialog.close();
                        onLanguageChanged.run();
                    }
                });
            }
        });
        miscGrid.add(languageLabel, 0, 4);
        miscGrid.add(languageCombo, 1, 4);

        miscTab.setContent(miscGrid);
        optionsTabs.getTabs().add(miscTab);

        // --- Onglet Themes ---
        Tab themesTab = new Tab(getMessages().getString("options.tab.themes"));
        
        ThemeManager themeManager = ThemeManager.getInstance();
        ListView<String> themeList = new ListView<>(FXCollections.observableArrayList(themeManager.getAvailableThemes()));
        themeList.getSelectionModel().select(config.getCurrentTheme());
        VBox.setVgrow(themeList, Priority.ALWAYS);
        
        // Cell factory pour styliser les thèmes (italique pour built-in, gras pour custom)
        themeList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (themeManager.isBuiltinTheme(item)) {
                        setStyle("-fx-font-style: italic;");
                    } else {
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
        
        Button createThemeBtn = new Button(getMessages().getString("options.theme.create"));
        Button deleteThemeBtn = new Button(getMessages().getString("options.theme.delete"));
        
        // Update delete button state based on selection
        deleteThemeBtn.setDisable(true);
        themeList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteThemeBtn.setDisable(newVal == null || themeManager.isBuiltinTheme(newVal));
        });
        
        // Double-click to edit custom theme
        themeList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = themeList.getSelectionModel().getSelectedItem();
                if (selected != null && !themeManager.isBuiltinTheme(selected)) {
                    File themeFile = themeManager.getCustomThemeFile(selected);
                    if (themeFile != null && themeFile.exists() && onOpenThemeFile != null) {
                        dialog.close();
                        onOpenThemeFile.accept(themeFile);
                    }
                }
            }
        });
        
        createThemeBtn.setOnAction(e -> {
            TextInputDialog nameDialog = new TextInputDialog();
            nameDialog.setTitle(getMessages().getString("options.theme.create.title"));
            nameDialog.setHeaderText(getMessages().getString("options.theme.create.header"));
            nameDialog.setContentText(getMessages().getString("options.theme.create.prompt"));
            nameDialog.initOwner(dialog);
            
            nameDialog.showAndWait().ifPresent(name -> {
                if (name != null && !name.isBlank()) {
                    String themeName = name.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
                    String baseTheme = themeList.getSelectionModel().getSelectedItem();
                    if (baseTheme == null) baseTheme = "light";
                    
                    try {
                        File cssFile = themeManager.createCustomTheme(baseTheme, themeName);
                        if (cssFile == null) {
                            throw new IOException("Failed to create theme file");
                        }
                        themeList.setItems(FXCollections.observableArrayList(themeManager.getAvailableThemes()));
                        themeList.getSelectionModel().select(themeName);
                        
                        // Open the CSS file in ThemeTab
                        if (cssFile.exists() && onOpenThemeFile != null) {
                            dialog.close();
                            onOpenThemeFile.accept(cssFile);
                        }
                    } catch (IOException ex) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle(getMessages().getString("options.theme.error.title"));
                        alert.setHeaderText(getMessages().getString("options.theme.error.create"));
                        alert.setContentText(ex.getMessage());
                        alert.initOwner(dialog);
                        alert.showAndWait();
                    }
                }
            });
        });
        
        deleteThemeBtn.setOnAction(e -> {
            String selected = themeList.getSelectionModel().getSelectedItem();
            if (selected != null && !themeManager.isBuiltinTheme(selected)) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(getMessages().getString("options.theme.delete.title"));
                confirm.setHeaderText(getMessages().getString("options.theme.delete.header"));
                confirm.setContentText(getMessages().getString("options.theme.delete.content"));
                confirm.initOwner(dialog);
                
                confirm.showAndWait().ifPresent(result -> {
                    if (result == ButtonType.OK) {
                        themeManager.deleteCustomTheme(selected);
                        themeList.setItems(FXCollections.observableArrayList(themeManager.getAvailableThemes()));
                        themeList.getSelectionModel().select("light");
                    }
                });
            }
        });
        
        HBox themeButtons = new HBox(10, createThemeBtn, deleteThemeBtn);
        themeButtons.setPadding(new Insets(10, 0, 0, 0));
        
        VBox themesBox = new VBox(10, themeList, themeButtons);
        themesBox.setPadding(new Insets(20));
        
        themesTab.setContent(themesBox);
        optionsTabs.getTabs().add(themesTab);

        // --- Boutons OK / Annuler ---
        Button okBtn = new Button(getMessages().getString("options.ok"));
        Button cancelBtn = new Button(getMessages().getString("options.cancel"));

        okBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);

        okBtn.setOnAction(e -> {
            config.setMaxRecentItems(recentSpinner.getValue());
            config.setOpenDocOnStart(openDocCheck.isSelected());
            config.setReopenLastProject(reopenCheck.isSelected());
            config.setShowWelcomePage(welcomeCheck.isSelected());
            String selectedTheme = themeList.getSelectionModel().getSelectedItem();
            if (selectedTheme != null) {
                config.setCurrentTheme(selectedTheme);
            }
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

        Scene dialogScene = new Scene(dialogRoot, 500, 350);
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

    /**
     * Définit le callback appelé lors de la création d'un nouveau thème.
     * Le callback reçoit le fichier CSS à ouvrir dans un ThemeTab.
     */
    public void setOnOpenThemeFile(Consumer<File> callback) {
        this.onOpenThemeFile = callback;
    }

    /**
     * Définit le callback appelé lors du changement de langue.
     * Ce callback doit redémarrer l'application.
     */
    public void setOnLanguageChanged(Runnable callback) {
        this.onLanguageChanged = callback;
    }
}
