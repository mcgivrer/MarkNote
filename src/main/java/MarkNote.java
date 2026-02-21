import java.io.File;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import config.AppConfig;
import config.ThemeManager;
import ui.DocumentTab;
import ui.FrontMatterPanel;
import ui.ImagePreviewTab;
import ui.OptionsDialog;
import ui.PreviewPanel;
import ui.ProjectExplorerPanel;
import ui.SearchBox;
import ui.SplashScreen;
import ui.StatusBar;
import ui.TagCloudPanel;
import ui.ThemeTab;
import ui.VisualLinkPanel;
import ui.WelcomeTab;
import utils.DocumentService;
import utils.IndexService;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Application principale MarkNote - Éditeur Markdown.
 */
public class MarkNote extends Application {

    private static ResourceBundle messages;

    private Stage primaryStage;
    private TabPane mainTabPane;
    private PreviewPanel previewPanel;
    private ProjectExplorerPanel projectExplorerPanel;
    private TagCloudPanel tagCloudPanel;
    private VisualLinkPanel visualLinkPanel;
    private SearchBox searchBox;
    private StatusBar statusBar;
    private IndexService indexService;
    private AppConfig config;
    private Menu recentMenu;

    // Panels et SplitPanes pour la gestion de l'affichage
    private SplitPane mainSplit;
    private SplitPane editorSplit;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        // Charger la configuration avant tout pour définir la locale
        config = new AppConfig();
        config.load();

        // Appliquer la langue configurée
        String language = config.getLanguage();
        if (language != null && !language.equals("system")) {
            Locale.setDefault(Locale.of(language));
        }

        // Charger les messages avec la locale configurée
        messages = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        BorderPane root = new BorderPane();

        // TabPane pour les documents
        mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        mainTabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);

        // Panel de prévisualisation
        previewPanel = new PreviewPanel();
        previewPanel.setOnMarkdownLinkClick(this::openFileInTab);

        // Panel d'exploration de projet
        projectExplorerPanel = new ProjectExplorerPanel();
        projectExplorerPanel.setOnFileDoubleClick(this::openFileInTab);

        // Index service
        indexService = new IndexService();

        // Tag cloud panel (sous l'explorateur)
        tagCloudPanel = new TagCloudPanel();

        // Visual link panel (diagramme réseau)
        visualLinkPanel = new VisualLinkPanel();
        visualLinkPanel.setOnDocumentClick(relativePath -> {
            File projectDir = projectExplorerPanel.getProjectDirectory();
            if (projectDir != null) {
                openFileInTab(new File(projectDir, relativePath));
            }
        });

        // Search box (dans la barre du haut)
        searchBox = new SearchBox();
        searchBox.setIndexService(indexService);
        searchBox.setOnFileSelected(this::openFileInTab);

        // Status bar (en bas de la fenêtre)
        statusBar = new StatusBar();

        // Callbacks de progression de l'indexation
        indexService.setOnProgress(progress -> statusBar.setIndexProgress(progress));
        indexService.setOnFinished(() -> {
            statusBar.setIndexIdle();
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
            updateStatusBarStats();
        });

        // Tag cloud : clic sur un tag → recherche
        tagCloudPanel.setOnTagClick(tag -> searchBox.setSearchText(tag));

        // Reset index depuis le menu contextuel de l'explorateur
        projectExplorerPanel.setOnResetIndex(this::handleResetIndex);

        // Index updates: fichier créé, renommé, supprimé, déplacé, copié
        projectExplorerPanel.setOnFileCreated(file -> {
            indexService.updateFile(file);
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
        });
        projectExplorerPanel.setOnFileRenamed((oldFile, newFile) -> {
            indexService.handleRename(oldFile, newFile);
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
        });
        projectExplorerPanel.setOnFileDeleted(file -> {
            if (file.isDirectory()) {
                indexService.removeFilesUnder(file);
            } else {
                indexService.removeFile(file);
            }
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
        });
        projectExplorerPanel.setOnFilesMoved((sourceFiles, targetDir) -> {
            indexService.handleMove(sourceFiles, targetDir);
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
        });
        projectExplorerPanel.setOnFilesCopied((sourceFiles, targetDir) -> {
            indexService.handleCopy(sourceFiles, targetDir);
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
        });

        // Conteneur gauche : explorateur + tag cloud + diagramme réseau (redimensionnable)
        SplitPane leftSplit = new SplitPane(projectExplorerPanel, tagCloudPanel, visualLinkPanel);
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.setDividerPositions(0.55, 0.78);

        // SplitPane éditeur | preview
        editorSplit = new SplitPane(mainTabPane, previewPanel);
        editorSplit.setOrientation(Orientation.HORIZONTAL);
        editorSplit.setDividerPositions(0.5);

        // SplitPane principal : explorateur | éditeur/preview
        mainSplit = new SplitPane(leftSplit, editorSplit);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.2);

        root.setCenter(mainSplit);

        // Status bar en bas
        root.setBottom(statusBar);

        // Menu Bar + Search Box
        MenuBar menuBar = createMenuBar();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(menuBar, spacer, searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(menuBar, Priority.NEVER);
        root.setTop(topBar);

        Scene scene = new Scene(root, 1200, 700);
        applyTheme(scene);
        if (projectExplorerPanel.getProjectDirectory() == null) {
            stage.setTitle(messages.getString("app.title.editor"));
        }
        stage.setScene(scene);

        // Afficher le splash screen ; la fenêtre principale et la logique de
        // démarrage s'exécutent une fois le splash fermé.
        if (config.isShowSplashScreen()) {
            SplashScreen splash = new SplashScreen(messages, config.getCurrentTheme());
            splash.setOnClosed(() -> {
                stage.show();
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
            });
            splash.show();
        } else {
            stage.show();
            handleReopenLastProject();
            if (config.isShowWelcomePage()) {
                showWelcomeTab();
            }
            if (config.isOpenDocOnStart() && !config.isShowWelcomePage()) {
                addNewDocument();
            }
        }
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
            // The project explorer and tag cloud are in a vertical SplitPane (leftSplit).
            // We need to find it — it's the parent of projectExplorerPanel.
            javafx.scene.Parent leftPane = projectExplorerPanel.getParent();
            if (leftPane == null) leftPane = projectExplorerPanel; // fallback
            if (isSelected) {
                if (!mainSplit.getItems().contains(leftPane)) {
                    mainSplit.getItems().addFirst(leftPane);
                    mainSplit.setDividerPositions(0.2);
                }
            } else {
                mainSplit.getItems().remove(leftPane);
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

        CheckMenuItem showNetworkDiagram = new CheckMenuItem(messages.getString("menu.view.networkDiagram"));
        showNetworkDiagram.setAccelerator(KeyCombination.keyCombination("Ctrl+L"));
        showNetworkDiagram.setSelected(true);
        showNetworkDiagram.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            javafx.scene.Parent leftPane = visualLinkPanel.getParent();
            if (leftPane instanceof SplitPane leftSplit) {
                if (isSelected) {
                    if (!leftSplit.getItems().contains(visualLinkPanel)) {
                        leftSplit.getItems().add(visualLinkPanel);
                    }
                } else {
                    leftSplit.getItems().remove(visualLinkPanel);
                }
            }
        });

        // Bouton [x] du diagramme réseau décoche le menu
        visualLinkPanel.setOnClose(() -> showNetworkDiagram.setSelected(false));

        // Option Afficher Welcome
        MenuItem showWelcomeItem = new MenuItem(messages.getString("menu.view.showWelcome"));
        showWelcomeItem.setOnAction(e -> showWelcomeTab());

        viewMenu.getItems().addAll(showProjectPanel, showPreviewPanel, showNetworkDiagram, showWelcomeItem);

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
            // Ouvrir le projet
            projectExplorerPanel.setProjectDirectory(dir);
            previewPanel.setBaseDirectory(dir);
            primaryStage.setTitle(messages.getString("app.title") + " - " + dir.getName());
            config.addRecentDir(dir);
            refreshRecentMenu();
            loadOrBuildIndex(dir);

            // Fermer l'onglet Welcome seulement s'il reste d'autres onglets
            if (mainTabPane.getTabs().size() > 1) {
                mainTabPane.getTabs().remove(welcomeTab);
            }
        });
        mainTabPane.getTabs().add(0, welcomeTab);
        mainTabPane.getSelectionModel().select(welcomeTab);
    }

    /**
     * Configure un onglet de document avec les listeners nécessaires.
     */
    private void setupDocumentTab(DocumentTab tab) {
        // Configurer le panneau Front Matter : répertoire de projet et callback de lien
        FrontMatterPanel fmPanel = tab.getFrontMatterPanel();
        if (projectExplorerPanel.getProjectDirectory() != null) {
            fmPanel.setProjectDirectory(projectExplorerPanel.getProjectDirectory());
        }
        fmPanel.setOnLinkClick(this::openFileInTab);

        // Appliquer la préférence d'expansion du panneau Front Matter
        fmPanel.setExpanded(config.isFrontMatterExpandedByDefault());

        // Mettre à jour la preview quand le texte change
        tab.setOnTextChanged(text -> {
            if (mainTabPane.getSelectionModel().getSelectedItem() == tab) {
                previewPanel.updatePreview(text);
                updateStatusBarForTab(tab);
            }
        });

        // Suivre la position du curseur dans l'éditeur
        tab.getEditor().caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (mainTabPane.getSelectionModel().getSelectedItem() == tab) {
                updateStatusBarForTab(tab);
            }
        });

        // Mettre à jour la preview et la statusbar quand on change d'onglet
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof DocumentTab docTab) {
                previewPanel.updatePreview(docTab.getFullContent());
                updateStatusBarForTab(docTab);
            } else {
                statusBar.clearDocumentInfo();
                statusBar.updateStats(
                        indexService.getEntries().size(), 0, 0);
            }
        });

        // Initial preview
        previewPanel.updatePreview(tab.getFullContent());
        updateStatusBarForTab(tab);
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
            loadOrBuildIndex(dir);
        }
    }

    /**
     * Charge l'index existant ou en construit un nouveau pour le projet.
     * L'indexation complète s'exécute dans un thread séparé.
     */
    private void loadOrBuildIndex(File projectDir) {
        if (projectDir == null) return;
        if (!indexService.loadIndex(projectDir)) {
            statusBar.setIndexProgress(-1);
            indexService.buildIndexAsync(projectDir);
        } else {
            tagCloudPanel.updateTags(indexService.getTagCounts());
            updateStatusBarStats();
        }
    }

    /**
     * Réinitialise l'index du projet : supprime le fichier d'index,
     * reconstruit l'index en arrière-plan et met à jour le tag cloud.
     */
    private void handleResetIndex() {
        File projectDir = projectExplorerPanel.getProjectDirectory();
        if (projectDir == null) return;
        indexService.resetIndex(projectDir);
        statusBar.setIndexProgress(-1);
        indexService.buildIndexAsync(projectDir);
        projectExplorerPanel.refresh();
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
                    // Mettre à jour l'index pour ce fichier
                    indexService.updateFile(docTab.getFile());
                    tagCloudPanel.updateTags(indexService.getTagCounts());
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
                        loadOrBuildIndex(d);
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
                    loadOrBuildIndex(lastDir);
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
        dialog.setOnLanguageChanged(this::restartApplication);
        if (dialog.showAndWait()) {
            refreshRecentMenu();
            // Apply theme if it changed
            if (!previousTheme.equals(config.getCurrentTheme())) {
                applyTheme(primaryStage.getScene());
            }
        }
    }

    /**
     * Redémarre l'application pour appliquer un changement de langue.
     */
    private void restartApplication() {
        // Clear ResourceBundle cache
        ResourceBundle.clearCache();
        
        // Close current stage
        primaryStage.close();
        
        // Start a new instance
        Platform.runLater(() -> {
            try {
                MarkNote newApp = new MarkNote();
                Stage newStage = new Stage();
                newApp.init();
                newApp.start(newStage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Affiche le dialogue À propos (réutilise la mise en page du SplashScreen).
     */
    private void showAboutDialog() {
        SplashScreen about = new SplashScreen(messages, primaryStage, true, config.getCurrentTheme());
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

        // Synchroniser le thème de coloration syntaxique dans la preview
        previewPanel.applySyntaxTheme(currentTheme);
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

    // ── Status bar helpers ──────────────────────────────────────────

    /**
     * Met à jour la statusbar pour l'onglet de document donné :
     * nom du fichier, position du curseur, comptage de lignes et de mots.
     */
    private void updateStatusBarForTab(DocumentTab tab) {
        if (tab == null) return;

        // Nom du fichier
        String filename = tab.getFile() != null ? tab.getFile().getName() : tab.getText().replaceFirst("^\\*", "");

        // Position du curseur
        var editor = tab.getEditor();
        int caretPos = editor.getCaretPosition();
        String text = editor.getText();

        int line = 1;
        int col = 1;
        for (int i = 0; i < caretPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        statusBar.updateDocumentInfo(filename, line, col);

        // Statistiques
        int totalLines = text.isEmpty() ? 0 : (int) text.lines().count();
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        int docs = indexService.getEntries().size();
        statusBar.updateStats(docs, totalLines, words);
    }

    /**
     * Met à jour uniquement les statistiques de la statusbar
     * (après un changement d'index, par exemple).
     */
    private void updateStatusBarStats() {
        var selected = mainTabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof DocumentTab docTab) {
            updateStatusBarForTab(docTab);
        } else {
            statusBar.updateStats(indexService.getEntries().size(), 0, 0);
        }
    }
}
