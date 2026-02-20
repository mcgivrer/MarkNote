import java.io.File;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import config.AppConfig;
import config.ThemeManager;
import ui.DocumentTab;
import ui.ImagePreviewTab;
import ui.OptionsDialog;
import ui.PreviewPanel;
import ui.ProjectExplorerPanel;
import ui.ThemeTab;
import ui.WelcomeTab;
import utils.DocumentService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Application principale MarkNote - Éditeur Markdown.
 */
public class MarkNote extends Application {

    private static final ResourceBundle messages = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());

    private Stage primaryStage;
    private TabPane mainTabPane;
    private PreviewPanel previewPanel;
    private ProjectExplorerPanel projectExplorerPanel;
    private AppConfig config;
    private Menu recentMenu;

    // Panels et SplitPanes pour la gestion de l'affichage
    private SplitPane mainSplit;
    private SplitPane editorSplit;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Charger la configuration
        config = new AppConfig();
        config.load();

        BorderPane root = new BorderPane();

        // TabPane pour les documents
        mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Panel de prévisualisation
        previewPanel = new PreviewPanel();
        previewPanel.setOnMarkdownLinkClick(this::openFileInTab);

        // Panel d'exploration de projet
        projectExplorerPanel = new ProjectExplorerPanel();
        projectExplorerPanel.setOnFileDoubleClick(this::openFileInTab);

        // SplitPane éditeur | preview
        editorSplit = new SplitPane(mainTabPane, previewPanel);
        editorSplit.setOrientation(Orientation.HORIZONTAL);
        editorSplit.setDividerPositions(0.5);

        // SplitPane principal : explorateur | éditeur/preview
        mainSplit = new SplitPane(projectExplorerPanel, editorSplit);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.2);

        root.setCenter(mainSplit);

        // Menu Bar
        MenuBar menuBar = createMenuBar();
        root.setTop(menuBar);

        // Rouvrir le dernier projet si l'option est activée
        handleReopenLastProject();

        // Afficher l'onglet Welcome si l'option est activée
        if (config.isShowWelcomePage()) {
            showWelcomeTab();
        }

        // Premier onglet (optionnel)
        if (config.isOpenDocOnStart() && !config.isShowWelcomePage()) {
            addNewDocument();
        }

        Scene scene = new Scene(root, 1200, 700);
        applyTheme(scene);
        if (projectExplorerPanel.getProjectDirectory() == null) {
            stage.setTitle(messages.getString("app.title.editor"));
        }
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Crée la barre de menus.
     */
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // == Menu Fichier ==
        Menu fileMenu = new Menu(messages.getString("menu.file"));

        MenuItem newDocItem = new MenuItem(messages.getString("menu.file.new"));
        newDocItem.setAccelerator(KeyCombination.keyCombination("Ctrl+N"));
        newDocItem.setOnAction(e -> addNewDocument());

        MenuItem openProjectItem = new MenuItem(messages.getString("menu.file.openProject"));
        openProjectItem.setOnAction(e -> openProject());

        MenuItem openItem = new MenuItem(messages.getString("menu.file.openFile"));
        openItem.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
        openItem.setOnAction(e -> openFile());

        recentMenu = new Menu(messages.getString("menu.file.recent"));
        refreshRecentMenu();

        MenuItem saveItem = new MenuItem(messages.getString("menu.file.save"));
        saveItem.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));
        saveItem.setOnAction(e -> saveCurrentDocument(false));

        MenuItem saveAsItem = new MenuItem(messages.getString("menu.file.saveAs"));
        saveAsItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Shift+S"));
        saveAsItem.setOnAction(e -> saveCurrentDocument(true));

        MenuItem quitItem = new MenuItem(messages.getString("menu.file.quit"));
        quitItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Q"));
        quitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(newDocItem, new SeparatorMenuItem(), openProjectItem, openItem,
                new SeparatorMenuItem(), recentMenu, new SeparatorMenuItem(), saveItem, saveAsItem,
                new SeparatorMenuItem(), quitItem);

        // == Menu Affichage ==
        Menu viewMenu = new Menu(messages.getString("menu.view"));

        CheckMenuItem showProjectPanel = new CheckMenuItem(messages.getString("menu.view.projectExplorer"));
        showProjectPanel.setAccelerator(KeyCombination.keyCombination("Ctrl+E"));
        showProjectPanel.setSelected(true);
        showProjectPanel.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                if (!mainSplit.getItems().contains(projectExplorerPanel)) {
                    mainSplit.getItems().addFirst(projectExplorerPanel);
                    mainSplit.setDividerPositions(0.2);
                }
            } else {
                mainSplit.getItems().remove(projectExplorerPanel);
            }
        });

        // Bouton [x] du project explorer décoche le menu
        projectExplorerPanel.setOnClose(() -> showProjectPanel.setSelected(false));

        CheckMenuItem showPreviewPanel = new CheckMenuItem(messages.getString("menu.view.previewPanel"));
        showPreviewPanel.setAccelerator(KeyCombination.keyCombination("Ctrl+P"));
        showPreviewPanel.setSelected(true);
        showPreviewPanel.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                if (!editorSplit.getItems().contains(previewPanel)) {
                    editorSplit.getItems().add(previewPanel);
                    editorSplit.setDividerPositions(0.5);
                }
            } else {
                editorSplit.getItems().remove(previewPanel);
            }
        });

        // Bouton [x] du preview décoche le menu
        previewPanel.setOnClose(() -> showPreviewPanel.setSelected(false));

        // Option Afficher Welcome
        MenuItem showWelcomeItem = new MenuItem(messages.getString("menu.view.showWelcome"));
        showWelcomeItem.setOnAction(e -> showWelcomeTab());

        viewMenu.getItems().addAll(showProjectPanel, showPreviewPanel, showWelcomeItem);

        // == Menu Aide ==
        Menu helpMenu = new Menu(messages.getString("menu.help"));

        MenuItem optionsItem = new MenuItem(messages.getString("menu.help.options"));
        optionsItem.setOnAction(e -> showOptionsDialog());

        MenuItem aboutItem = new MenuItem(messages.getString("menu.help.about"));
        aboutItem.setOnAction(e -> showAboutDialog());

        helpMenu.getItems().addAll(optionsItem, new SeparatorMenuItem(), aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);

        return menuBar;
    }

    /**
     * Crée un nouveau document.
     */
    private void addNewDocument() {
        DocumentTab tab = new DocumentTab(mainTabPane.getTabs().size() + 1);
        setupDocumentTab(tab);
        mainTabPane.getTabs().add(tab);
        mainTabPane.getSelectionModel().select(tab);
    }

    /**
     * Affiche l'onglet Welcome avec la liste des projets récents.
     */
    private void showWelcomeTab() {
        WelcomeTab welcomeTab = new WelcomeTab(config.getRecentDirs(), config.getMaxRecentItems(), config);
        welcomeTab.setOnProjectSelected(dir -> {
            // Fermer l'onglet Welcome
            mainTabPane.getTabs().remove(welcomeTab);
            // Ouvrir le projet
            projectExplorerPanel.setProjectDirectory(dir);
            previewPanel.setBaseDirectory(dir);
            primaryStage.setTitle(messages.getString("app.title") + " - " + dir.getName());
            config.addRecentDir(dir);
            refreshRecentMenu();
        });
        mainTabPane.getTabs().add(0, welcomeTab);
        mainTabPane.getSelectionModel().select(welcomeTab);
    }

    /**
     * Configure un onglet de document avec les listeners nécessaires.
     */
    private void setupDocumentTab(DocumentTab tab) {
        // Mettre à jour la preview quand le texte change
        tab.setOnTextChanged(text -> {
            if (mainTabPane.getSelectionModel().getSelectedItem() == tab) {
                previewPanel.updatePreview(text);
            }
        });

        // Mettre à jour la preview quand on change d'onglet
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof DocumentTab docTab) {
                previewPanel.updatePreview(docTab.getTextContent());
            }
        });

        // Initial preview
        previewPanel.updatePreview(tab.getTextContent());
    }

    /**
     * Ouvre un fichier dans un nouvel onglet.
     */
    private void openFileInTab(File file) {
        // Vérifier si ce fichier est déjà ouvert
        for (var tab : mainTabPane.getTabs()) {
            if (tab instanceof DocumentTab docTab && file.equals(docTab.getFile())) {
                mainTabPane.getSelectionModel().select(tab);
                return;
            }
            if (tab instanceof ImagePreviewTab imgTab && file.equals(imgTab.getFile())) {
                mainTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        // Si c'est une image, ouvrir dans un onglet de prévisualisation
        if (ImagePreviewTab.isImageFile(file)) {
            ImagePreviewTab tab = new ImagePreviewTab(file);
            mainTabPane.getTabs().add(tab);
            mainTabPane.getSelectionModel().select(tab);
            return;
        }

        // Sinon, ouvrir comme document texte
        Optional<String> content = DocumentService.readFile(file);
        if (content.isPresent()) {
            DocumentTab tab = new DocumentTab(file.getName(), content.get(), file);
            setupDocumentTab(tab);
            mainTabPane.getTabs().add(tab);
            mainTabPane.getSelectionModel().select(tab);
            config.addRecentFile(file);
            refreshRecentMenu();
        } else {
            showError(messages.getString("error.read.title"), file.getAbsolutePath());
        }
    }

    /**
     * Ouvre un fichier via le dialogue de sélection.
     */
    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.getString("chooser.openFile"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(messages.getString("chooser.filter.markdown"), "*.md", "*.markdown"),
                new FileChooser.ExtensionFilter(messages.getString("chooser.filter.text"), "*.txt"),
                new FileChooser.ExtensionFilter(messages.getString("chooser.filter.all"), "*.*"));

        File projectDir = projectExplorerPanel.getProjectDirectory();
        if (projectDir != null && projectDir.exists()) {
            chooser.setInitialDirectory(projectDir);
        }

        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            openFileInTab(file);
        }
    }

    /**
     * Ouvre un projet (répertoire).
     */
    private void openProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.getString("chooser.openProject"));

        File currentDir = projectExplorerPanel.getProjectDirectory();
        if (currentDir != null && currentDir.exists()) {
            chooser.setInitialDirectory(currentDir);
        }

        File dir = chooser.showDialog(primaryStage);
        if (dir != null) {
            projectExplorerPanel.setProjectDirectory(dir);
            previewPanel.setBaseDirectory(dir);
            primaryStage.setTitle(messages.getString("app.title") + " - " + dir.getName());
            config.addRecentDir(dir);
            refreshRecentMenu();
        }
    }

    /**
     * Sauvegarde le document courant.
     */
    private void saveCurrentDocument(boolean forceChoose) {
        var selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof DocumentTab docTab) {
            File projectDir = projectExplorerPanel.getProjectDirectory();
            if (docTab.save(primaryStage, forceChoose, projectDir)) {
                config.addRecentFile(docTab.getFile());
                refreshRecentMenu();
                // Rafraîchir l'arbre si le fichier est dans le projet
                if (projectDir != null && docTab.getFile().toPath().startsWith(projectDir.toPath())) {
                    projectExplorerPanel.refresh();
                }
            }
        }
    }

    /**
     * Rafraîchit le menu des fichiers récents.
     */
    private void refreshRecentMenu() {
        recentMenu.getItems().clear();

        var recentFiles = config.getRecentFiles();
        var recentDirs = config.getRecentDirs();

        if (recentFiles.isEmpty() && recentDirs.isEmpty()) {
            MenuItem emptyItem = new MenuItem(messages.getString("recent.empty"));
            emptyItem.setDisable(true);
            recentMenu.getItems().add(emptyItem);
            return;
        }

        // Section fichiers
        if (!recentFiles.isEmpty()) {
            MenuItem filesHeader = new MenuItem(messages.getString("recent.files.header"));
            filesHeader.setDisable(true);
            filesHeader.setStyle("-fx-font-weight: bold;");
            recentMenu.getItems().add(filesHeader);

            for (String path : recentFiles) {
                File f = new File(path);
                MenuItem item = new MenuItem(f.getName() + "  (" + f.getParent() + ")");
                item.setOnAction(e -> {
                    if (f.exists()) {
                        openFileInTab(f);
                    } else {
                        showError(messages.getString("error.fileNotFound.title"),
                                MessageFormat.format(messages.getString("error.fileNotFound.message"), path));
                        config.removeRecentFile(path);
                        refreshRecentMenu();
                    }
                });
                recentMenu.getItems().add(item);
            }
        }

        // Séparateur entre sections
        if (!recentFiles.isEmpty() && !recentDirs.isEmpty()) {
            recentMenu.getItems().add(new SeparatorMenuItem());
        }

        // Section répertoires
        if (!recentDirs.isEmpty()) {
            MenuItem dirsHeader = new MenuItem(messages.getString("recent.projects.header"));
            dirsHeader.setDisable(true);
            dirsHeader.setStyle("-fx-font-weight: bold;");
            recentMenu.getItems().add(dirsHeader);

            for (String path : recentDirs) {
                File d = new File(path);
                MenuItem item = new MenuItem(d.getName() + "  (" + d.getParent() + ")");
                item.setOnAction(e -> {
                    if (d.exists() && d.isDirectory()) {
                        projectExplorerPanel.setProjectDirectory(d);
                        previewPanel.setBaseDirectory(d);
                        primaryStage.setTitle(messages.getString("app.title") + " - " + d.getName());
                        config.addRecentDir(d);
                        refreshRecentMenu();
                    } else {
                        showError(messages.getString("error.dirNotFound.title"),
                                MessageFormat.format(messages.getString("error.dirNotFound.message"), path));
                        config.removeRecentDir(path);
                        refreshRecentMenu();
                    }
                });
                recentMenu.getItems().add(item);
            }
        }

        // Séparateur + effacer l'historique
        recentMenu.getItems().add(new SeparatorMenuItem());
        MenuItem clearItem = new MenuItem(messages.getString("recent.clear"));
        clearItem.setOnAction(e -> {
            config.clearHistory();
            refreshRecentMenu();
        });
        recentMenu.getItems().add(clearItem);
    }

    /**
     * Gère la réouverture du dernier projet au démarrage.
     */
    private void handleReopenLastProject() {
        if (config.isReopenLastProject() && !config.getRecentDirs().isEmpty()) {
            File lastDir = new File(config.getRecentDirs().getFirst());
            if (lastDir.exists() && lastDir.isDirectory()) {
                Alert reopenAlert = new Alert(Alert.AlertType.CONFIRMATION);
                reopenAlert.setTitle(messages.getString("reopen.title"));
                reopenAlert.setHeaderText(messages.getString("reopen.header"));
                reopenAlert.setContentText(MessageFormat.format(messages.getString("reopen.content"), lastDir.getName()));
                Optional<ButtonType> result = reopenAlert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    projectExplorerPanel.setProjectDirectory(lastDir);
                    previewPanel.setBaseDirectory(lastDir);
                    primaryStage.setTitle(messages.getString("app.title") + " - " + lastDir.getName());
                }
            }
        }
    }

    /**
     * Affiche le dialogue d'options.
     */
    private void showOptionsDialog() {
        String previousTheme = config.getCurrentTheme();
        OptionsDialog dialog = new OptionsDialog(primaryStage, config);
        dialog.setOnOpenThemeFile(this::openThemeFile);
        if (dialog.showAndWait()) {
            refreshRecentMenu();
            // Apply theme if it changed
            if (!previousTheme.equals(config.getCurrentTheme())) {
                applyTheme(primaryStage.getScene());
            }
        }
    }

    /**
     * Affiche le dialogue À propos.
     */
    private void showAboutDialog() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.initOwner(primaryStage);
        about.setTitle(messages.getString("about.title"));
        about.setHeaderText("MarkNote");
        
        String version = MessageFormat.format(messages.getString("about.version"), "0.0.1");
        String content = version + "\n\n" +
                messages.getString("about.copyright") + "\n\n" +
                messages.getString("about.author") + "\n" +
                messages.getString("about.contact") + "\n\n" +
                messages.getString("about.repository");
        
        about.setContentText(content);
        about.showAndWait();
    }

    /**
     * Ouvre un fichier CSS de thème dans un ThemeTab.
     */
    private void openThemeFile(File file) {
        // Vérifier si le fichier est déjà ouvert
        for (var tab : mainTabPane.getTabs()) {
            if (tab instanceof ThemeTab themeTab) {
                if (file.equals(themeTab.getFile())) {
                    mainTabPane.getSelectionModel().select(tab);
                    return;
                }
            }
        }
        
        // Charger le contenu
        Optional<String> content = DocumentService.readFile(file);
        if (content.isPresent()) {
            ThemeTab themeTab = new ThemeTab(file, content.get());
            themeTab.setOnSaveCallback(v -> {
                // Rafraîchir le thème si c'est le thème courant
                ThemeManager themeManager = ThemeManager.getInstance();
                String themeName = file.getName().replace(".css", "");
                if (themeName.equals(config.getCurrentTheme())) {
                    applyTheme(primaryStage.getScene());
                }
            });
            mainTabPane.getTabs().add(themeTab);
            mainTabPane.getSelectionModel().select(themeTab);
        } else {
            showError(messages.getString("error.read.title"), file.getAbsolutePath());
        }
    }

    /**
     * Applique le thème courant à la scène.
     */
    private void applyTheme(Scene scene) {
        ThemeManager themeManager = ThemeManager.getInstance();
        String currentTheme = config.getCurrentTheme();
        String themeCssUrl = themeManager.getThemeCssUrl(currentTheme);
        
        scene.getStylesheets().clear();
        if (themeCssUrl != null) {
            scene.getStylesheets().add(themeCssUrl);
        }
    }

    /**
     * Affiche une erreur.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
