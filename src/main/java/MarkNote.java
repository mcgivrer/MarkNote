import java.io.File;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import config.AppConfig;
import config.LLMConfig;
import config.ThemeManager;
import ui.BasePanel;
import ui.ConsolePanel;
import ui.DockingManager;
import ui.DockZone;
import ui.DocumentTab;
import ui.FrontMatterPanel;
import ui.ImagePreviewTab;
import ui.DetachedPanelTab;
import ui.OptionsDialog;
import ui.PreviewPanel;
import ui.ProjectExplorerPanel;
import ui.PromptPanel;
import ui.SearchBox;
import ui.SplashScreen;
import ui.StatusBar;
import ui.TagCloudPanel;
import ui.ThemeTab;
import ui.VisualLinkPanel;
import ui.WelcomeTab;
import utils.DocumentService;
import utils.GitService;
import utils.IndexService;
import utils.LogService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
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
    private SplitPane editorSplit;
    private DockingManager dockingManager;

    private ConsolePanel consolePanel;
    private CheckMenuItem showConsoleMenuItem;
    private boolean consoleDebugEnabled = false;

    private GitService gitService;

    // LLM Chat Panel
    private PromptPanel promptPanel;
    private LLMConfig llmConfig;
    private CheckMenuItem showLLMPanelMenuItem;
    private final Map<String, BasePanel> managedPanels = new LinkedHashMap<>();
    private final Map<BasePanel, Boolean> lastDockedState = new HashMap<>();
    private final Map<BasePanel, CheckMenuItem> panelMenuItems = new HashMap<>();
    private boolean syncingPanelMenuState = false;

    public static void main(String[] args) {
        // Prefer IPv6 when both IPv4/IPv6 exist for the same host.
        // This avoids ConnectException on hosts where IPv4 resolves to a non-routable bridge address.
        System.setProperty("java.net.preferIPv6Addresses", "true");
        launch(args);
    }

    @Override
    public void init() {
        // Charger la configuration avant tout pour définir la locale
        config = new AppConfig();
        config.load();

        // Vérifier l'argument --console-debug
        for (String arg : getParameters().getRaw()) {
            if ("--console-debug".equals(arg)) {
                consoleDebugEnabled = true;
                break;
            }
        }

        // Initialiser le service de logging
        LogService.getInstance().setConsoleDebugEnabled(consoleDebugEnabled);

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
        previewPanel.setAppConfig(config);
        previewPanel.setOnPlantUmlRenderingChanged(
                rendering -> Platform.runLater(() -> statusBar.setPlantUmlRendering(rendering)));

        // Panel d'exploration de projet
        projectExplorerPanel = new ProjectExplorerPanel();
        projectExplorerPanel.setOnFileDoubleClick(this::openFileInTab);

        // Synchronise l'arbre de l'explorateur avec l'onglet actif
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof DocumentTab docTab) {
                File activeFile = docTab.getFile();
                if (activeFile != null) {
                    projectExplorerPanel.revealFile(activeFile);
                }
            }
        });

        // Git service
        gitService = new GitService();
        gitService.setOnStatusUpdated(() -> projectExplorerPanel.refresh());
        gitService.setOnOperationResult(this::showGitOperationResult);
        projectExplorerPanel.setGitService(gitService);

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
        visualLinkPanel.setIndexService(indexService);
        visualLinkPanel.setOnFileSelected(this::openFileInTab);
        visualLinkPanel.setOnDetach(() -> detachPanel(visualLinkPanel));

        // Search box (dans la barre du haut)
        searchBox = new SearchBox();
        searchBox.setIndexService(indexService);
        searchBox.setOnFileSelected(this::openFileInTab);

        // Status bar (en bas de la fenêtre)
        statusBar = new StatusBar();
        statusBar.setPlantUmlIndicator(
                config.isUseLocalPlantUml() && !config.getPlantUmlJarPath().isBlank());

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
        tagCloudPanel.setOnDetach(() -> detachPanel(tagCloudPanel));

        // Reset index depuis le menu contextuel de l'explorateur
        projectExplorerPanel.setOnResetIndex(this::handleResetIndex);
        projectExplorerPanel.setOnDetach(() -> detachPanel(projectExplorerPanel));

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

        // SplitPane éditeur | preview (zone centrale)
        editorSplit = new SplitPane(mainTabPane, previewPanel);
        editorSplit.setOrientation(Orientation.HORIZONTAL);
        editorSplit.setDividerPositions(0.5);

        // Initialiser le DockingManager
        dockingManager = new DockingManager(root);
        dockingManager.setCenterContent(editorSplit);
        registerManagedPanel(projectExplorerPanel);
        registerManagedPanel(tagCloudPanel);
        registerManagedPanel(visualLinkPanel);

        // LLM Chat Panel (à droite)
        llmConfig = new LLMConfig();
        llmConfig.load();
        if (llmConfig.isEnabled()) {
            promptPanel = new PromptPanel(llmConfig);
            promptPanel.setActiveDocumentSupplier(() -> {
                var sel = mainTabPane.getSelectionModel().getSelectedItem();
                return (sel instanceof DocumentTab dt) ? dt : null;
            });
            promptPanel.setOnDetach(() -> detachPanel(promptPanel));
            registerManagedPanel(promptPanel);
        }

        // Console panel (si --console-debug est passé)
        if (consoleDebugEnabled) {
            consolePanel = new ConsolePanel();
            registerManagedPanel(consolePanel);
            consolePanel.startCapture();
        }

        restoreManagedPanelStates();

        // Appliquer le layout
        root.setCenter(dockingManager.getRootNode());

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
        stage.setOnCloseRequest(e -> saveManagedPanelStates());

        // Icônes de fenêtre / barre des tâches (du plus petit au plus grand)
        String[] iconSizes = { "16", "32", "64", "128" };
        for (String size : iconSizes) {
            try (var is = getClass().getResourceAsStream("/images/icons/marknote-" + size + ".png")) {
                if (is != null) {
                    stage.getIcons().add(new Image(is));
                }
            } catch (Exception ignored) {
            }
        }

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

        MenuItem closeTabItem = new MenuItem(messages.getString("menu.file.close"));
        closeTabItem.setAccelerator(KeyCombination.keyCombination("Ctrl+W"));
        closeTabItem.setOnAction(e -> closeActiveTab());

        fileMenu.getItems().addAll(newDocItem, new SeparatorMenuItem(), openProjectItem, openItem,
                new SeparatorMenuItem(), recentMenu, new SeparatorMenuItem(), saveItem, saveAsItem,
                new SeparatorMenuItem(), closeTabItem, quitItem);

        // == Menu Affichage ==
        Menu viewMenu = new Menu(messages.getString("menu.view"));

        CheckMenuItem showProjectPanel = new CheckMenuItem(messages.getString("menu.view.projectExplorer"));
        showProjectPanel.setAccelerator(KeyCombination.keyCombination("Ctrl+E"));
        bindManagedPanelMenuItem(projectExplorerPanel, showProjectPanel);

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

        CheckMenuItem showTagCloud = new CheckMenuItem(messages.getString("menu.view.tagCloud"));
        showTagCloud.setAccelerator(KeyCombination.keyCombination("Ctrl+T"));
        bindManagedPanelMenuItem(tagCloudPanel, showTagCloud);

        CheckMenuItem showNetworkDiagram = new CheckMenuItem(messages.getString("menu.view.networkDiagram"));
        showNetworkDiagram.setAccelerator(KeyCombination.keyCombination("Ctrl+L"));
        bindManagedPanelMenuItem(visualLinkPanel, showNetworkDiagram);

        // Option LLM Panel (si activé dans la config)
        if (llmConfig.isEnabled() && promptPanel != null) {
            showLLMPanelMenuItem = new CheckMenuItem(messages.getString("menu.view.llmPanel"));
            showLLMPanelMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+M"));
            bindManagedPanelMenuItem(promptPanel, showLLMPanelMenuItem);
        }

        // Option Afficher Welcome
        MenuItem showWelcomeItem = new MenuItem(messages.getString("menu.view.showWelcome"));
        showWelcomeItem.setOnAction(e -> showWelcomeTab());

        viewMenu.getItems().addAll(showProjectPanel, showPreviewPanel, new SeparatorMenuItem(), showTagCloud,
                showNetworkDiagram);
        
        // Ajouter l'option LLM si disponible
        if (showLLMPanelMenuItem != null) {
            viewMenu.getItems().add(showLLMPanelMenuItem);
        }
        
        viewMenu.getItems().addAll(new SeparatorMenuItem(), showWelcomeItem);

        // Option Console (uniquement si --console-debug est actif)
        if (consoleDebugEnabled) {
            showConsoleMenuItem = new CheckMenuItem(messages.getString("menu.view.console"));
            bindManagedPanelMenuItem(consolePanel, showConsoleMenuItem);

            viewMenu.getItems().add(viewMenu.getItems().size() - 1, new SeparatorMenuItem());
            viewMenu.getItems().add(viewMenu.getItems().size() - 1, showConsoleMenuItem);
        }

        // == Menu Aide ==
        Menu helpMenu = new Menu(messages.getString("menu.help"));

        MenuItem optionsItem = new MenuItem(messages.getString("menu.help.options"));
        optionsItem.setOnAction(e -> showOptionsDialog());

        MenuItem aboutItem = new MenuItem(messages.getString("menu.help.about"));
        aboutItem.setOnAction(e -> showAboutDialog());

        helpMenu.getItems().addAll(optionsItem, new SeparatorMenuItem(), aboutItem);

        // == Menu Édition ==
        Menu editMenu = new Menu(messages.getString("menu.edit"));

        MenuItem searchItem = new MenuItem(messages.getString("menu.edit.search"));
        searchItem.setAccelerator(KeyCombination.keyCombination("Ctrl+F"));
        searchItem.setOnAction(e -> {
            if (getActiveDocumentTab() != null) getActiveDocumentTab().openSearch();
        });

        MenuItem replaceItem = new MenuItem(messages.getString("menu.edit.replace"));
        replaceItem.setAccelerator(KeyCombination.keyCombination("Ctrl+H"));
        replaceItem.setOnAction(e -> {
            if (getActiveDocumentTab() != null) getActiveDocumentTab().openReplace();
        });

        editMenu.getItems().addAll(searchItem, replaceItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, helpMenu);

        return menuBar;
    }

    /**
     * Retourne le DocumentTab actif, ou null si l'onglet courant n'en est pas un.
     */
    private DocumentTab getActiveDocumentTab() {
        var selected = mainTabPane.getSelectionModel().getSelectedItem();
        return (selected instanceof DocumentTab docTab) ? docTab : null;
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
     * Ferme l'onglet actif (avec confirmation si modifié).
     */
    private void closeActiveTab() {
        var selected = mainTabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Déclencher la logique de fermeture existante (onCloseRequest)
            var closeEvent = new javafx.event.Event(javafx.scene.control.Tab.TAB_CLOSE_REQUEST_EVENT);
            javafx.event.Event.fireEvent(selected, closeEvent);
            if (!closeEvent.isConsumed()) {
                mainTabPane.getTabs().remove(selected);
            }
        }
    }

    /**
     * Affiche l'onglet Welcome avec la liste des projets récents.
     */
    private void showWelcomeTab() {
        WelcomeTab welcomeTab = new WelcomeTab(config.getRecentDirs(), config.getMaxRecentItems(), config);
        welcomeTab.setOnProjectSelected(dir -> {
            openProjectDirectory(dir);

            // Fermer l'onglet Welcome seulement s'il reste d'autres onglets
            if (mainTabPane.getTabs().size() > 1) {
                mainTabPane.getTabs().remove(welcomeTab);
            }
        });
        welcomeTab.setOnCreateProjectRequested(() -> {
            File newProjectDir = createNewProjectDirectory();
            if (newProjectDir != null) {
                openProjectDirectory(newProjectDir);
                if (mainTabPane.getTabs().size() > 1) {
                    mainTabPane.getTabs().remove(welcomeTab);
                }
            }
        });
        welcomeTab.setOnClearProjectsHistoryRequested(() -> {
            config.clearHistory();
            refreshRecentMenu();
            mainTabPane.getTabs().remove(welcomeTab);
            showWelcomeTab();
        });
        mainTabPane.getTabs().add(0, welcomeTab);
        mainTabPane.getSelectionModel().select(welcomeTab);
    }

    /**
     * Détache un panel dans un onglet séparé.
     *
     * @param panel le panel à détacher
     */
    private void detachPanel(BasePanel panel) {
        detachPanel(panel, dockingManager.getLastKnownZone(panel));
    }

    private void detachPanel(BasePanel panel, DockZone fallbackZone) {
        // Éviter les doublons - chercher si un onglet existe déjà pour ce panel
        for (var tab : mainTabPane.getTabs()) {
            if (tab instanceof DetachedPanelTab dpt && dpt.getSourcePanel() == panel) {
                mainTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        // Mémoriser la zone de dock actuelle
        DockZone originalZone = dockingManager.getLastKnownZone(panel);
        if (originalZone == null) {
            originalZone = fallbackZone != null ? fallbackZone : getDefaultPanelZone(panel);
        }
        final DockZone restoredZone = originalZone;

        // Masquer le panel via le DockingManager
        if (dockingManager.isDocked(panel)) {
            dockingManager.undock(panel);
        }
        lastDockedState.put(panel, false);

        // Créer l'onglet
        DetachedPanelTab detachedTab = new DetachedPanelTab(panel);
        detachedTab.setOnCloseAction(() -> {
            // Réafficher le panel dans sa zone d'origine
            if (restoredZone != null) {
                dockingManager.dock(panel, restoredZone);
                lastDockedState.put(panel, true);
            }
        });

        mainTabPane.getTabs().add(detachedTab);
        mainTabPane.getSelectionModel().select(detachedTab);
        syncManagedPanelMenuItem(panel, true);
    }

    private void registerManagedPanel(BasePanel panel) {
        if (panel == null) {
            return;
        }
        managedPanels.put(panel.getPanelStateId(), panel);
        DockZone defaultZone = getDefaultPanelZone(panel);
        dockingManager.rememberPanelZone(panel, defaultZone);
        lastDockedState.put(panel, true);
    }

    private DockZone getDefaultPanelZone(BasePanel panel) {
        return panel == promptPanel ? DockZone.RIGHT : DockZone.LEFT;
    }

    private DockZone getStoredOrDefaultZone(BasePanel panel) {
        DockZone zone = dockingManager.getLastKnownZone(panel);
        if (zone != null) {
            return zone;
        }
        AppConfig.PanelState state = config.getPanelState(panel.getPanelStateId());
        if (state != null) {
            try {
                return DockZone.valueOf(state.zone());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return getDefaultPanelZone(panel);
    }

    private AppConfig.PanelState getSavedOrDefaultPanelState(BasePanel panel) {
        AppConfig.PanelState state = config.getPanelState(panel.getPanelStateId());
        if (state != null) {
            return state;
        }
        return new AppConfig.PanelState(true, true, getDefaultPanelZone(panel).name());
    }

    private void restoreManagedPanelStates() {
        for (BasePanel panel : managedPanels.values()) {
            AppConfig.PanelState state = getSavedOrDefaultPanelState(panel);
            DockZone zone = getDefaultPanelZone(panel);
            try {
                if (state.zone() != null && !state.zone().isBlank()) {
                    zone = DockZone.valueOf(state.zone());
                }
            } catch (IllegalArgumentException ignored) {
            }

            dockingManager.rememberPanelZone(panel, zone);
            lastDockedState.put(panel, state.docked());

            if (!state.visible()) {
                continue;
            }

            dockingManager.dock(panel, zone);
            if (!state.docked()) {
                detachPanel(panel, zone);
            }
        }
    }

    private void bindManagedPanelMenuItem(BasePanel panel, CheckMenuItem menuItem) {
        if (panel == null || menuItem == null) {
            return;
        }
        panelMenuItems.put(panel, menuItem);
        syncingPanelMenuState = true;
        try {
            menuItem.setSelected(isManagedPanelVisible(panel));
        } finally {
            syncingPanelMenuState = false;
        }
        menuItem.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (syncingPanelMenuState || wasSelected == isSelected) {
                return;
            }
            if (isSelected) {
                showManagedPanel(panel);
            } else {
                hideManagedPanel(panel);
            }
        });
        panel.setOnClose(() -> syncManagedPanelMenuItem(panel, false));
    }

    private void syncManagedPanelMenuItem(BasePanel panel, boolean selected) {
        CheckMenuItem menuItem = panelMenuItems.get(panel);
        if (menuItem == null) {
            return;
        }
        syncingPanelMenuState = true;
        try {
            menuItem.setSelected(selected);
        } finally {
            syncingPanelMenuState = false;
        }
        if (!selected) {
            hideManagedPanel(panel);
        }
    }

    private boolean isManagedPanelVisible(BasePanel panel) {
        return dockingManager.isDocked(panel) || findDetachedPanel(panel) != null;
    }

    private DetachedPanelTab findDetachedPanel(BasePanel panel) {
        for (var tab : mainTabPane.getTabs()) {
            if (tab instanceof DetachedPanelTab detachedTab && detachedTab.getSourcePanel() == panel) {
                return detachedTab;
            }
        }
        return null;
    }

    private void showManagedPanel(BasePanel panel) {
        if (isManagedPanelVisible(panel)) {
            return;
        }
        DockZone zone = getStoredOrDefaultZone(panel);
        boolean shouldDock = lastDockedState.getOrDefault(panel, true);
        dockingManager.dock(panel, zone);
        if (!shouldDock) {
            detachPanel(panel, zone);
        } else {
            lastDockedState.put(panel, true);
        }
    }

    private void hideManagedPanel(BasePanel panel) {
        DetachedPanelTab detachedTab = findDetachedPanel(panel);
        if (detachedTab != null) {
            detachedTab.hideWithoutDocking();
        } else if (dockingManager.isDocked(panel)) {
            dockingManager.hidePanel(panel);
        }
    }

    private void saveManagedPanelStates() {
        for (BasePanel panel : managedPanels.values()) {
            boolean detached = findDetachedPanel(panel) != null;
            boolean visible = detached || dockingManager.isDocked(panel);
            boolean docked = visible ? !detached : lastDockedState.getOrDefault(panel, true);
            DockZone zone = getStoredOrDefaultZone(panel);
            config.setPanelState(panel.getPanelStateId(), new AppConfig.PanelState(visible, docked, zone.name()));
        }
        config.save();
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
                previewPanel.setCurrentFile(docTab.getFile());
                updateStatusBarForTab(docTab);
                // Mettre en valeur le document dans le diagramme réseau
                File docFile = docTab.getFile();
                File projectDir = projectExplorerPanel.getProjectDirectory();
                if (docFile != null && projectDir != null) {
                    String relativePath = projectDir.toPath().relativize(docFile.toPath()).toString();
                    visualLinkPanel.setCurrentDocument(relativePath);
                } else {
                    visualLinkPanel.setCurrentDocument(null);
                }
            } else {
                statusBar.clearDocumentInfo();
                statusBar.updateStats(indexService.getEntries().size(), 0, 0);
                visualLinkPanel.setCurrentDocument(null);
            }
        });

        // Initial preview
        previewPanel.updatePreview(tab.getFullContent());
        previewPanel.setCurrentFile(tab.getFile());
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
            openProjectDirectory(dir);
        }
    }

    /**
     * Crée un nouveau répertoire de projet puis le retourne.
     */
    private File createNewProjectDirectory() {
        DirectoryChooser parentChooser = new DirectoryChooser();
        parentChooser.setTitle(messages.getString("chooser.newProjectParent"));

        File currentDir = projectExplorerPanel.getProjectDirectory();
        if (currentDir != null && currentDir.exists()) {
            parentChooser.setInitialDirectory(currentDir);
        }

        File parentDir = parentChooser.showDialog(primaryStage);
        if (parentDir == null) {
            return null;
        }

        TextInputDialog nameDialog = new TextInputDialog("new-project");
        nameDialog.setTitle(messages.getString("newproject.title"));
        nameDialog.setHeaderText(messages.getString("newproject.header"));
        nameDialog.setContentText(messages.getString("newproject.prompt"));

        Optional<String> result = nameDialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }

        String projectName = result.get().trim();
        if (projectName.isEmpty()) {
            return null;
        }

        File newProjectDir = new File(parentDir, projectName);
        if (newProjectDir.exists() || !newProjectDir.mkdirs()) {
            showError(messages.getString("error.projectCreate.title"),
                    MessageFormat.format(messages.getString("error.projectCreate.message"),
                            newProjectDir.getAbsolutePath()));
            return null;
        }

        return newProjectDir;
    }

    /**
     * Ouvre un répertoire de projet et met à jour l'UI/l'historique.
     */
    private void openProjectDirectory(File dir) {
        // Configurer le service git avec les credentials de la config
        gitService.setSshKeyPath(config.getGitSshKeyPath());
        gitService.setGitToken(config.getGitToken());
        gitService.setGitUsername(config.getGitUsername());
        gitService.setProject(dir);

        projectExplorerPanel.setProjectDirectory(dir);
        previewPanel.setBaseDirectory(dir);
        primaryStage.setTitle(messages.getString("app.title") + " - " + dir.getName());
        config.addRecentDir(dir);
        refreshRecentMenu();
        loadOrBuildIndex(dir);
    }

    /**
     * Charge l'index existant ou en construit un nouveau pour le projet.
     * L'indexation complète s'exécute dans un thread séparé.
     */
    private void loadOrBuildIndex(File projectDir) {
        if (projectDir == null)
            return;
        if (!indexService.loadIndex(projectDir)) {
            statusBar.setIndexProgress(-1);
            indexService.buildIndexAsync(projectDir);
        } else {
            tagCloudPanel.updateTags(indexService.getTagCounts());
            visualLinkPanel.updateDiagram(indexService.getEntries());
            updateStatusBarStats();
        }
    }

    /**
     * Réinitialise l'index du projet : supprime le fichier d'index, reconstruit
     * l'index en arrière-plan et met à jour le tag cloud.
     */
    private void handleResetIndex() {
        File projectDir = projectExplorerPanel.getProjectDirectory();
        if (projectDir == null)
            return;
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
                    projectExplorerPanel.revealFile(docTab.getFile());
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
                        openProjectDirectory(d);
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
                reopenAlert
                        .setContentText(MessageFormat.format(messages.getString("reopen.content"), lastDir.getName()));
                Optional<ButtonType> result = reopenAlert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    openProjectDirectory(lastDir);
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
            // Refresh preview in case PlantUML settings changed
            previewPanel.refresh();
            // Update PlantUML status bar indicator
            statusBar.setPlantUmlIndicator(
                    config.isUseLocalPlantUml() && !config.getPlantUmlJarPath().isBlank());
            // Update git credentials
            gitService.setSshKeyPath(config.getGitSshKeyPath());
            gitService.setGitToken(config.getGitToken());
            gitService.setGitUsername(config.getGitUsername());
        }
    }

    /**
     * Affiche le résultat d'une opération git (pull/push) dans une boîte de dialogue.
     */
    private void showGitOperationResult(String result) {
        Alert alert = new Alert(
                result.startsWith("Error:") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
        alert.initOwner(primaryStage);
        alert.setTitle(messages.getString("git.operation.result.title"));
        alert.setHeaderText(null);
        TextArea textArea = new TextArea(result.isBlank() ? messages.getString("git.operation.uptodate") : result);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefSize(420, 180);
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    /**
     * Redirige l'application pour appliquer un changement de langue.
     */
    private void restartApplication() {
        saveManagedPanelStates();
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

    @Override
    public void stop() {
        saveManagedPanelStates();
    }

    // ── Status bar helpers ──────────────────────────────────────────

    /**
     * Met à jour la statusbar pour l'onglet de document donné : nom du fichier,
     * position du curseur, comptage de lignes et de mots.
     */
    private void updateStatusBarForTab(DocumentTab tab) {
        if (tab == null)
            return;

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
     * Met à jour uniquement les statistiques de la statusbar (après un changement
     * d'index, par exemple).
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
