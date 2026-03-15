package ui;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import config.AppConfig;
import config.LLMConfig;
import config.ThemeManager;
import services.LLMService;

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
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
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

        // Afficher le splash screen au démarrage
        Label splashLabel = new Label(getMessages().getString("options.showSplashScreen"));
        CheckBox splashCheck = new CheckBox();
        splashCheck.setSelected(config.isShowSplashScreen());
        miscGrid.add(splashLabel, 0, 4);
        miscGrid.add(splashCheck, 1, 4);

        // Panneau FrontMatter ouvert par défaut
        Label fmExpandedLabel = new Label(getMessages().getString("options.frontMatterExpanded"));
        CheckBox fmExpandedCheck = new CheckBox();
        fmExpandedCheck.setSelected(config.isFrontMatterExpandedByDefault());
        miscGrid.add(fmExpandedLabel, 0, 5);
        miscGrid.add(fmExpandedCheck, 1, 5);

        // Réafficher le panneau diagramme quand l'onglet est fermé
        Label reattachDiagramLabel = new Label(getMessages().getString("options.reattachDiagramOnTabClose"));
        CheckBox reattachDiagramCheck = new CheckBox();
        reattachDiagramCheck.setSelected(config.isReattachDiagramOnTabClose());
        miscGrid.add(reattachDiagramLabel, 0, 6);
        miscGrid.add(reattachDiagramCheck, 1, 6);

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
        miscGrid.add(languageLabel, 0, 6);
        miscGrid.add(languageCombo, 1, 6);

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

        // --- Onglet Tools ---
        Tab toolsTab = new Tab(getMessages().getString("options.tab.tools"));

        GridPane toolsGrid = new GridPane();
        toolsGrid.setHgap(10);
        toolsGrid.setVgap(10);
        toolsGrid.setPadding(new Insets(20));

        // Section PlantUML
        Label plantUmlHeader = new Label(getMessages().getString("options.tools.plantuml.header"));
        plantUmlHeader.setStyle("-fx-font-weight: bold;");
        toolsGrid.add(plantUmlHeader, 0, 0, 3, 1);

        // Checkbox : use local plantuml jar
        Label useLocalLabel = new Label(getMessages().getString("options.tools.plantuml.useLocal"));
        CheckBox useLocalCheck = new CheckBox();
        useLocalCheck.setSelected(config.isUseLocalPlantUml());
        toolsGrid.add(useLocalLabel, 0, 1);
        toolsGrid.add(useLocalCheck, 1, 1);

        // Path field + Browse button
        Label jarPathLabel = new Label(getMessages().getString("options.tools.plantuml.jarPath"));
        TextField jarPathField = new TextField(config.getPlantUmlJarPath());
        jarPathField.setPromptText(getMessages().getString("options.tools.plantuml.jarPath.prompt"));
        jarPathField.setPrefWidth(280);
        GridPane.setHgrow(jarPathField, Priority.ALWAYS);

        Button browseJarBtn = new Button(getMessages().getString("options.tools.plantuml.browse"));
        browseJarBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(getMessages().getString("options.tools.plantuml.browse.title"));
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JAR files (*.jar)", "*.jar"));
            String current = jarPathField.getText().trim();
            if (!current.isEmpty()) {
                File currentFile = new File(current);
                if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                    fc.setInitialDirectory(currentFile.getParentFile());
                }
            }
            File selected = fc.showOpenDialog(dialog);
            if (selected != null) {
                jarPathField.setText(selected.getAbsolutePath());
            }
        });

        toolsGrid.add(jarPathLabel, 0, 2);
        toolsGrid.add(jarPathField, 1, 2);
        toolsGrid.add(browseJarBtn, 2, 2);

        toolsTab.setContent(toolsGrid);
        optionsTabs.getTabs().add(toolsTab);

        // --- Onglet Git ---
        Tab gitTab = new Tab(getMessages().getString("options.tab.git"));

        GridPane gitGrid = new GridPane();
        gitGrid.setHgap(10);
        gitGrid.setVgap(10);
        gitGrid.setPadding(new Insets(20));

        // Section SSH
        Label sshHeader = new Label(getMessages().getString("options.git.ssh.header"));
        sshHeader.setStyle("-fx-font-weight: bold;");
        gitGrid.add(sshHeader, 0, 0, 3, 1);

        Label sshKeyLabel = new Label(getMessages().getString("options.git.ssh.keyPath"));
        TextField sshKeyField = new TextField(config.getGitSshKeyPath());
        sshKeyField.setPromptText(getMessages().getString("options.git.ssh.keyPath.prompt"));
        sshKeyField.setPrefWidth(280);
        GridPane.setHgrow(sshKeyField, Priority.ALWAYS);

        Button browseSshBtn = new Button(getMessages().getString("options.git.browse"));
        browseSshBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(getMessages().getString("options.git.ssh.browse.title"));
            String current = sshKeyField.getText().trim();
            if (!current.isEmpty()) {
                File currentFile = new File(current);
                if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                    fc.setInitialDirectory(currentFile.getParentFile());
                }
            }
            File selected = fc.showOpenDialog(dialog);
            if (selected != null) sshKeyField.setText(selected.getAbsolutePath());
        });
        gitGrid.add(sshKeyLabel, 0, 1);
        gitGrid.add(sshKeyField, 1, 1);
        gitGrid.add(browseSshBtn, 2, 1);

        // Section Token HTTPS
        Label tokenHeader = new Label(getMessages().getString("options.git.token.header"));
        tokenHeader.setStyle("-fx-font-weight: bold;");
        gitGrid.add(tokenHeader, 0, 3, 3, 1);

        Label usernameLabel = new Label(getMessages().getString("options.git.token.username"));
        TextField usernameField = new TextField(config.getGitUsername());
        usernameField.setPromptText(getMessages().getString("options.git.token.username.prompt"));
        gitGrid.add(usernameLabel, 0, 4);
        gitGrid.add(usernameField, 1, 4);

        Label tokenLabel = new Label(getMessages().getString("options.git.token.token"));
        PasswordField tokenField = new PasswordField();
        tokenField.setText(config.getGitToken());
        tokenField.setPromptText(getMessages().getString("options.git.token.token.prompt"));
        tokenField.setPrefWidth(280);
        GridPane.setHgrow(tokenField, Priority.ALWAYS);
        gitGrid.add(tokenLabel, 0, 5);
        gitGrid.add(tokenField, 1, 5);

        gitTab.setContent(gitGrid);
        optionsTabs.getTabs().add(gitTab);

        // --- Onglet LLM ---
        Tab llmTab = new Tab(getMessages().getString("llm.config.tab"));
        
        LLMConfig llmConfig = new LLMConfig();
        llmConfig.load();

        GridPane llmGrid = new GridPane();
        llmGrid.setHgap(10);
        llmGrid.setVgap(10);
        llmGrid.setPadding(new Insets(20));

        // Header
        Label llmHeader = new Label(getMessages().getString("llm.config.header"));
        llmHeader.setStyle("-fx-font-weight: bold;");
        llmGrid.add(llmHeader, 0, 0, 3, 1);

        // Enable LLM
        Label enableLabel = new Label(getMessages().getString("llm.config.enabled"));
        CheckBox enableCheck = new CheckBox();
        enableCheck.setSelected(llmConfig.isEnabled());
        llmGrid.add(enableLabel, 0, 1);
        llmGrid.add(enableCheck, 1, 1);

        // Endpoint URL
        Label urlLabel = new Label(getMessages().getString("llm.config.url"));
        TextField urlField = new TextField(llmConfig.getEndpointUrl());
        urlField.setPromptText(getMessages().getString("llm.config.url.prompt"));
        urlField.setPrefWidth(280);
        GridPane.setHgrow(urlField, Priority.ALWAYS);
        llmGrid.add(urlLabel, 0, 2);
        llmGrid.add(urlField, 1, 2, 2, 1);

        // API Key
        Label apiKeyLabel = new Label(getMessages().getString("llm.config.apikey"));
        PasswordField apiKeyField = new PasswordField();
        apiKeyField.setText(llmConfig.getApiKey());
        apiKeyField.setPromptText(getMessages().getString("llm.config.apikey.prompt"));
        apiKeyField.setPrefWidth(280);
        llmGrid.add(apiKeyLabel, 0, 3);
        llmGrid.add(apiKeyField, 1, 3, 2, 1);

        // Model
        Label modelLabel = new Label(getMessages().getString("llm.config.model"));
        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.setValue(llmConfig.getModel());
        modelCombo.setPromptText(getMessages().getString("llm.config.model.prompt"));
        modelCombo.setPrefWidth(180);
        
        Button refreshModelsBtn = new Button(getMessages().getString("llm.config.refresh"));
        refreshModelsBtn.setOnAction(e -> {
            LLMConfig tempConfig = new LLMConfig();
            tempConfig.setEndpointUrl(urlField.getText());
            tempConfig.setApiKey(apiKeyField.getText());
            LLMService tempService = new LLMService(tempConfig);
            List<String> models = tempService.getAvailableModels();
            if (!models.isEmpty()) {
                modelCombo.getItems().clear();
                modelCombo.getItems().addAll(models);
                if (!models.contains(modelCombo.getValue())) {
                    modelCombo.setValue(models.get(0));
                }
            }
        });
        llmGrid.add(modelLabel, 0, 4);
        llmGrid.add(modelCombo, 1, 4);
        llmGrid.add(refreshModelsBtn, 2, 4);

        // Timeout
        Label timeoutLabel = new Label(getMessages().getString("llm.config.timeout"));
        Spinner<Integer> timeoutSpinner = new Spinner<>(1, 300, llmConfig.getTimeout());
        timeoutSpinner.setEditable(true);
        timeoutSpinner.setPrefWidth(100);
        llmGrid.add(timeoutLabel, 0, 5);
        llmGrid.add(timeoutSpinner, 1, 5);

        // Test Connection button
        Button testBtn = new Button(getMessages().getString("llm.config.test"));
        testBtn.setOnAction(e -> {
            LLMConfig tempConfig = new LLMConfig();
            tempConfig.setEndpointUrl(urlField.getText());
            tempConfig.setApiKey(apiKeyField.getText());
            tempConfig.setModel(modelCombo.getValue() != null ? modelCombo.getValue() : "");
            tempConfig.setTimeout(timeoutSpinner.getValue());
            LLMService tempService = new LLMService(tempConfig);
            
            boolean success = tempService.testConnection();
            Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.initOwner(dialog);
            alert.setTitle(getMessages().getString("llm.config.test"));
            alert.setHeaderText(null);
            alert.setContentText(getMessages().getString(
                    success ? "llm.config.test.success" : "llm.config.test.failure"));
            alert.showAndWait();
        });
        llmGrid.add(testBtn, 2, 5);

        // System context
        Label contextLabel = new Label(getMessages().getString("llm.config.context"));
        llmGrid.add(contextLabel, 0, 6);
        
        TextArea contextArea = new TextArea(llmConfig.getSystemContext());
        contextArea.setPromptText(getMessages().getString("llm.config.context.prompt"));
        contextArea.setPrefRowCount(3);
        contextArea.setWrapText(true);
        llmGrid.add(contextArea, 0, 7, 3, 1);

        llmTab.setContent(llmGrid);
        optionsTabs.getTabs().add(llmTab);

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
            config.setShowSplashScreen(splashCheck.isSelected());
            config.setFrontMatterExpandedByDefault(fmExpandedCheck.isSelected());
            config.setReattachDiagramOnTabClose(reattachDiagramCheck.isSelected());
            String selectedTheme = themeList.getSelectionModel().getSelectedItem();
            if (selectedTheme != null) {
                config.setCurrentTheme(selectedTheme);
            }
            config.setUseLocalPlantUml(useLocalCheck.isSelected());
            config.setPlantUmlJarPath(jarPathField.getText().trim());
            config.setGitSshKeyPath(sshKeyField.getText().trim());
            config.setGitUsername(usernameField.getText().trim());
            config.setGitToken(tokenField.getText());
            config.save();
            
            // Save LLM config
            llmConfig.setEnabled(enableCheck.isSelected());
            llmConfig.setEndpointUrl(urlField.getText().trim());
            llmConfig.setApiKey(apiKeyField.getText());
            llmConfig.setModel(modelCombo.getValue() != null ? modelCombo.getValue() : "");
            llmConfig.setTimeout(timeoutSpinner.getValue());
            llmConfig.setSystemContext(contextArea.getText());
            llmConfig.save();
            
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
