import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GameIDE extends Application {

    private Parser markdownParser;
    private HtmlRenderer htmlRenderer;

    // Associe chaque Tab au fichier correspondant (null si nouveau doc non
    // sauvegardé)
    private final Map<Tab, File> tabFileMap = new HashMap<>();

    // Contenu sauvegardé de chaque tab (pour détecter les modifications)
    private final Map<Tab, String> tabSavedContent = new HashMap<>();

    // Référence au stage pour la sauvegarde depuis le close handler
    private Stage primaryStage;
    private TabPane mainTabPane;

    // Répertoire de projet courant
    private File projectDir;

    // Panel d'exploration de projet
    private TreeView<File> projectTree;
    private BorderPane projectPane;

    // --- Fichiers récents ---
    private static final String CONFIG_FILE = System.getProperty("user.home") + File.separator + ".gameide.conf";
    private final LinkedList<String> recentFiles = new LinkedList<>();
    private final LinkedList<String> recentDirs = new LinkedList<>();
    private int maxRecentItems = 10;
    private boolean openDocOnStart = true;
    private boolean reopenLastProject = false;
    private Menu recentMenu;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Charger la configuration
        loadConfig();

        // Flexmark init
        markdownParser = Parser.builder().build();
        htmlRenderer = HtmlRenderer.builder().build();

        BorderPane root = new BorderPane();

        TabPane tabPane = new TabPane();
        this.mainTabPane = tabPane;
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Preview globale à droite
        WebView preview = new WebView();

        // Header du preview avec titre + bouton [x]
        Label previewTitle = new Label("Preview");
        previewTitle.setStyle("-fx-font-weight: bold;");
        Region previewSpacer = new Region();
        HBox.setHgrow(previewSpacer, Priority.ALWAYS);
        Button closePreviewBtn = new Button("\u00D7");
        closePreviewBtn.setStyle(
                "-fx-font-size: 10; -fx-padding: 0 4 0 4; -fx-background-color: transparent; -fx-cursor: hand;");
        closePreviewBtn.setTooltip(new Tooltip("Fermer la preview"));
        HBox previewHeader = new HBox(previewTitle, previewSpacer, closePreviewBtn);
        previewHeader.setAlignment(Pos.CENTER_LEFT);
        previewHeader.setPadding(new Insets(4));
        previewHeader.setStyle("-fx-background-color: #e0e0e0;");

        BorderPane previewPane = new BorderPane();
        previewPane.setTop(previewHeader);
        previewPane.setCenter(preview);

        // --- Project Explorer (gauche) ---
        projectTree = new TreeView<>();
        projectTree.setShowRoot(true);
        projectTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName().isEmpty() ? item.getPath() : item.getName());
                    setGraphic(new ImageView(item.isDirectory() ? new Image("images/icons/folder-invoices--v1.png")
                            : new Image("images/icons/file.png")));
                }
            }
        });

        // Double-clic pour ouvrir un fichier dans un onglet
        projectTree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<File> selected = projectTree.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue().isFile()) {
                    openFileInTab(selected.getValue(), tabPane, preview);
                }
            }
        });

        // Header du project explorer avec titre + bouton [x]
        Label projectTitle = new Label("Exploration du projet");
        projectTitle.setStyle("-fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeProjectBtn = new Button("\u00D7");
        closeProjectBtn.setStyle(
                "-fx-font-size: 10; -fx-padding: 0 4 0 4; -fx-background-color: transparent; -fx-cursor: hand;");
        closeProjectBtn.setTooltip(new Tooltip("Fermer l'explorateur"));
        HBox projectHeader = new HBox(projectTitle, spacer, closeProjectBtn);
        projectHeader.setAlignment(Pos.CENTER_LEFT);
        projectHeader.setPadding(new Insets(4));
        projectHeader.setStyle("-fx-background-color: #e0e0e0;");

        projectPane = new BorderPane();
        projectPane.setTop(projectHeader);
        projectPane.setCenter(projectTree);
        projectPane.setPrefWidth(250);
        projectPane.setMaxHeight(Double.MAX_VALUE);

        // SplitPane éditeur | preview
        SplitPane editorSplit = new SplitPane(tabPane, previewPane);
        editorSplit.setOrientation(Orientation.HORIZONTAL);
        editorSplit.setDividerPositions(0.5);

        // SplitPane principal : explorateur | éditeur/preview
        SplitPane mainSplit = new SplitPane(projectPane, editorSplit);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.2);

        root.setCenter(mainSplit);

        // --- Menu Bar ---
        MenuBar menuBar = new MenuBar();

        // == Menu Fichier ==
        Menu fileMenu = new Menu("Fichier");

        MenuItem newDocItem = new MenuItem("Nouveau doc");
        newDocItem.setAccelerator(KeyCombination.keyCombination("Ctrl+N"));
        newDocItem.setOnAction(e -> addMarkdownTab(tabPane, preview));

        MenuItem openProjectItem = new MenuItem("Ouvrir un projet...");
        openProjectItem.setOnAction(e -> openProject(stage, tabPane, preview));

        MenuItem openItem = new MenuItem("Ouvrir un fichier...");
        openItem.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
        openItem.setOnAction(e -> loadFile(stage, tabPane, preview));

        // Sous-menu fichiers récents
        recentMenu = new Menu("Fichiers récents");
        refreshRecentMenu(tabPane, preview);

        MenuItem saveItem = new MenuItem("Sauvegarder");
        saveItem.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));
        saveItem.setOnAction(e -> saveFile(stage, tabPane, false));

        MenuItem saveAsItem = new MenuItem("Sauvegarder sous...");
        saveAsItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Shift+S"));
        saveAsItem.setOnAction(e -> saveFile(stage, tabPane, true));

        MenuItem quitItem = new MenuItem("Quitter");
        quitItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Q"));
        quitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(newDocItem, new SeparatorMenuItem(), openProjectItem, openItem,
                new SeparatorMenuItem(), recentMenu, new SeparatorMenuItem(), saveItem, saveAsItem,
                new SeparatorMenuItem(), quitItem);

        // == Menu Affichage ==
        Menu viewMenu = new Menu("Affichage");

        CheckMenuItem showProjectPanel = new CheckMenuItem("Exploration du projet");
        showProjectPanel.setAccelerator(KeyCombination.keyCombination("Ctrl+E"));
        showProjectPanel.setSelected(true);
        showProjectPanel.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                if (!mainSplit.getItems().contains(projectPane)) {
                    mainSplit.getItems().addFirst(projectPane);
                    mainSplit.setDividerPositions(0.2);
                }
            } else {
                mainSplit.getItems().remove(projectPane);
            }
        });

        // Bouton [x] du project explorer décoche le menu
        closeProjectBtn.setOnAction(e -> showProjectPanel.setSelected(false));

        CheckMenuItem showPreviewPanel = new CheckMenuItem("Panneau de preview");
        showPreviewPanel.setAccelerator(KeyCombination.keyCombination("Ctrl+P"));
        showPreviewPanel.setSelected(true);
        showPreviewPanel.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                if (!editorSplit.getItems().contains(previewPane)) {
                    editorSplit.getItems().add(previewPane);
                    editorSplit.setDividerPositions(0.5);
                }
            } else {
                editorSplit.getItems().remove(previewPane);
            }
        });

        // Bouton [x] du preview décoche le menu
        closePreviewBtn.setOnAction(e -> showPreviewPanel.setSelected(false));

        viewMenu.getItems().addAll(showProjectPanel, showPreviewPanel);

        // == Menu Aide ==
        Menu helpMenu = new Menu("Aide");

        MenuItem optionsItem = new MenuItem("Options...");
        optionsItem.setOnAction(e -> showOptionsDialog(tabPane, preview));

        helpMenu.getItems().add(optionsItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);

        root.setTop(menuBar);

        // Rouvrir le dernier projet si l'option est activée
        if (reopenLastProject && !recentDirs.isEmpty()) {
            File lastDir = new File(recentDirs.getFirst());
            if (lastDir.exists() && lastDir.isDirectory()) {
                Alert reopenAlert = new Alert(Alert.AlertType.CONFIRMATION);
                reopenAlert.setTitle("Rouvrir le projet");
                reopenAlert.setHeaderText("Rouvrir le dernier projet ?");
                reopenAlert.setContentText("Voulez-vous rouvrir le projet \"" + lastDir.getName() + "\" ?");
                Optional<ButtonType> result = reopenAlert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    projectDir = lastDir;
                    stage.setTitle("GameIDE - " + lastDir.getName());
                    refreshProjectTree();
                }
            }
        }

        // Premier onglet (optionnel)
        if (openDocOnStart) {
            addMarkdownTab(tabPane, preview);
        }

        Scene scene = new Scene(root, 1200, 700);
        if (projectDir == null) {
            stage.setTitle("GameIDE - Éditeur Markdown");
        }
        stage.setScene(scene);
        stage.show();
    }

    // ========================================
    // Configuration persistante
    // ========================================

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists())
            return;

        try {
            List<String> lines = Files.readAllLines(configFile.toPath());
            for (String line : lines) {
                if (line.startsWith("maxRecentItems=")) {
                    try {
                        maxRecentItems = Integer.parseInt(line.substring("maxRecentItems=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("recentFile=")) {
                    String path = line.substring("recentFile=".length()).trim();
                    if (!path.isEmpty())
                        recentFiles.add(path);
                } else if (line.startsWith("recentDir=")) {
                    String path = line.substring("recentDir=".length()).trim();
                    if (!path.isEmpty())
                        recentDirs.add(path);
                } else if (line.startsWith("openDocOnStart=")) {
                    openDocOnStart = Boolean.parseBoolean(line.substring("openDocOnStart=".length()).trim());
                } else if (line.startsWith("reopenLastProject=")) {
                    reopenLastProject = Boolean.parseBoolean(line.substring("reopenLastProject=".length()).trim());
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void saveConfig() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("maxRecentItems=" + maxRecentItems);
            lines.add("openDocOnStart=" + openDocOnStart);
            lines.add("reopenLastProject=" + reopenLastProject);
            for (String f : recentFiles) {
                lines.add("recentFile=" + f);
            }
            for (String d : recentDirs) {
                lines.add("recentDir=" + d);
            }
            Files.writeString(Path.of(CONFIG_FILE), String.join("\n", lines));
        } catch (IOException ignored) {
        }
    }

    private void addRecentFile(File file) {
        String path = file.getAbsolutePath();
        recentFiles.remove(path);
        recentFiles.addFirst(path);
        while (recentFiles.size() > maxRecentItems) {
            recentFiles.removeLast();
        }
        saveConfig();
    }

    private void addRecentDir(File dir) {
        String path = dir.getAbsolutePath();
        recentDirs.remove(path);
        recentDirs.addFirst(path);
        while (recentDirs.size() > maxRecentItems) {
            recentDirs.removeLast();
        }
        saveConfig();
    }

    private void refreshRecentMenu(TabPane tabPane, WebView preview) {
        recentMenu.getItems().clear();

        if (recentFiles.isEmpty() && recentDirs.isEmpty()) {
            MenuItem emptyItem = new MenuItem("(aucun)");
            emptyItem.setDisable(true);
            recentMenu.getItems().add(emptyItem);
            return;
        }

        // Section fichiers
        if (!recentFiles.isEmpty()) {
            MenuItem filesHeader = new MenuItem("── Fichiers ──");
            filesHeader.setDisable(true);
            filesHeader.setStyle("-fx-font-weight: bold;");
            recentMenu.getItems().add(filesHeader);

            for (String path : recentFiles) {
                File f = new File(path);
                MenuItem item = new MenuItem(f.getName() + "  (" + f.getParent() + ")");
                item.setOnAction(e -> {
                    if (f.exists()) {
                        openFileInTab(f, tabPane, preview);
                    } else {
                        showError("Fichier introuvable", "Le fichier n'existe plus :\n" + path);
                        recentFiles.remove(path);
                        saveConfig();
                        refreshRecentMenu(tabPane, preview);
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
            MenuItem dirsHeader = new MenuItem("── Projets ──");
            dirsHeader.setDisable(true);
            dirsHeader.setStyle("-fx-font-weight: bold;");
            recentMenu.getItems().add(dirsHeader);

            for (String path : recentDirs) {
                File d = new File(path);
                MenuItem item = new MenuItem(d.getName() + "  (" + d.getParent() + ")");
                item.setOnAction(e -> {
                    if (d.exists() && d.isDirectory()) {
                        projectDir = d;
                        primaryStage.setTitle("GameIDE - " + d.getName());
                        addRecentDir(d);
                        refreshProjectTree();
                        refreshRecentMenu(tabPane, preview);
                    } else {
                        showError("Répertoire introuvable", "Le répertoire n'existe plus :\n" + path);
                        recentDirs.remove(path);
                        saveConfig();
                        refreshRecentMenu(tabPane, preview);
                    }
                });
                recentMenu.getItems().add(item);
            }
        }

        // Séparateur + effacer l'historique
        recentMenu.getItems().add(new SeparatorMenuItem());
        MenuItem clearItem = new MenuItem("Effacer l'historique");
        clearItem.setOnAction(e -> {
            recentFiles.clear();
            recentDirs.clear();
            saveConfig();
            refreshRecentMenu(tabPane, preview);
        });
        recentMenu.getItems().add(clearItem);
    }

    // ========================================
    // Dialogue Options
    // ========================================

    private void showOptionsDialog(TabPane tabPane, WebView preview) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setTitle("Options");

        javafx.scene.control.TabPane optionsTabs = new javafx.scene.control.TabPane();
        optionsTabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Onglet Misc. ---
        Tab miscTab = new Tab("Misc.");

        GridPane miscGrid = new GridPane();
        miscGrid.setHgap(10);
        miscGrid.setVgap(10);
        miscGrid.setPadding(new Insets(20));

        Label recentLabel = new Label("Nombre de fichiers/projets récents :");
        Spinner<Integer> recentSpinner = new Spinner<>(1, 50, maxRecentItems);
        recentSpinner.setEditable(true);
        recentSpinner.setPrefWidth(100);

        miscGrid.add(recentLabel, 0, 0);
        miscGrid.add(recentSpinner, 1, 0);

        Label openDocLabel = new Label("Créer un document au démarrage :");
        CheckBox openDocCheck = new CheckBox();
        openDocCheck.setSelected(openDocOnStart);
        miscGrid.add(openDocLabel, 0, 1);
        miscGrid.add(openDocCheck, 1, 1);

        Label reopenLabel = new Label("Rouvrir le dernier projet au démarrage :");
        CheckBox reopenCheck = new CheckBox();
        reopenCheck.setSelected(reopenLastProject);
        miscGrid.add(reopenLabel, 0, 2);
        miscGrid.add(reopenCheck, 1, 2);

        miscTab.setContent(miscGrid);
        optionsTabs.getTabs().add(miscTab);

        // --- Boutons OK / Annuler ---
        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Annuler");

        okBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);

        okBtn.setOnAction(e -> {
            maxRecentItems = recentSpinner.getValue();
            openDocOnStart = openDocCheck.isSelected();
            reopenLastProject = reopenCheck.isSelected();
            // Tronquer les listes si nécessaire
            while (recentFiles.size() > maxRecentItems)
                recentFiles.removeLast();
            while (recentDirs.size() > maxRecentItems)
                recentDirs.removeLast();
            saveConfig();
            refreshRecentMenu(tabPane, preview);
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
        dialog.showAndWait();
    }

    // ========================================
    // Ouvrir un projet (répertoire)
    // ========================================

    private void openProject(Stage stage, TabPane tabPane, WebView preview) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Ouvrir un répertoire de projet");
        if (projectDir != null && projectDir.exists()) {
            chooser.setInitialDirectory(projectDir);
        }
        File dir = chooser.showDialog(stage);
        if (dir == null)
            return;

        projectDir = dir;
        stage.setTitle("GameIDE - " + dir.getName());
        addRecentDir(dir);
        refreshProjectTree();
        refreshRecentMenu(tabPane, preview);
    }

    // --- Construire l'arborescence du projet ---
    private void refreshProjectTree() {
        if (projectDir == null || !projectDir.isDirectory())
            return;

        TreeItem<File> rootItem = buildTreeItem(projectDir);
        rootItem.setExpanded(true);
        projectTree.setRoot(rootItem);
    }

    private TreeItem<File> buildTreeItem(File file) {
        TreeItem<File> item = new TreeItem<>(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                // Dossiers d'abord, puis fichiers, triés alphabétiquement
                java.util.Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory())
                        return -1;
                    if (!a.isDirectory() && b.isDirectory())
                        return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File child : children) {
                    // Ignorer les fichiers/dossiers cachés
                    if (!child.getName().startsWith(".")) {
                        item.getChildren().add(buildTreeItem(child));
                    }
                }
            }
        }
        return item;
    }

    // --- Ouvrir un fichier depuis l'arbre projet ---
    private void openFileInTab(File file, TabPane tabPane, WebView preview) {
        // Vérifier si ce fichier est déjà ouvert
        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            if (file.equals(entry.getValue())) {
                tabPane.getSelectionModel().select(entry.getKey());
                return;
            }
        }
        try {
            String content = Files.readString(file.toPath());
            addMarkdownTab(tabPane, preview, file.getName(), content, file);
            addRecentFile(file);
            refreshRecentMenu(tabPane, preview);
        } catch (IOException ex) {
            showError("Erreur de lecture", ex.getMessage());
        }
    }

    // ========================================
    // Sauvegarde
    // ========================================

    private void saveFile(Stage stage, TabPane tabPane, boolean forceChoose) {
        Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
        if (currentTab == null)
            return;

        File file = tabFileMap.get(currentTab);

        if (file == null || forceChoose) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Sauvegarder le document");
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown"),
                    new FileChooser.ExtensionFilter("Texte", "*.txt"), new FileChooser.ExtensionFilter("Tous", "*.*"));
            if (file != null) {
                chooser.setInitialDirectory(file.getParentFile());
                chooser.setInitialFileName(file.getName());
            } else if (projectDir != null) {
                chooser.setInitialDirectory(projectDir);
            }
            file = chooser.showSaveDialog(stage);
        }
        if (file == null)
            return;

        try {
            TextArea editor = (TextArea) currentTab.getContent();
            Files.writeString(file.toPath(), editor.getText());
            tabFileMap.put(currentTab, file);
            tabSavedContent.put(currentTab, editor.getText());
            currentTab.setText(truncateTabName(file.getName()));
            currentTab.setTooltip(new Tooltip(file.getName()));
            addRecentFile(file);
            refreshRecentMenu(tabPane, null);
            // Rafraîchir l'arbre si le fichier est dans le projet
            if (projectDir != null && file.toPath().startsWith(projectDir.toPath())) {
                refreshProjectTree();
            }
        } catch (IOException ex) {
            showError("Erreur de sauvegarde", ex.getMessage());
        }
    }

    // ========================================
    // Chargement
    // ========================================

    private void loadFile(Stage stage, TabPane tabPane, WebView preview) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Ouvrir un document");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown"),
                new FileChooser.ExtensionFilter("Texte", "*.txt"), new FileChooser.ExtensionFilter("Tous", "*.*"));
        if (projectDir != null) {
            chooser.setInitialDirectory(projectDir);
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null)
            return;

        try {
            String content = Files.readString(file.toPath());
            addMarkdownTab(tabPane, preview, file.getName(), content, file);
            addRecentFile(file);
            refreshRecentMenu(tabPane, preview);
        } catch (IOException ex) {
            showError("Erreur de lecture", ex.getMessage());
        }
    }

    // ========================================
    // Gestion des onglets
    // ========================================

    private void addMarkdownTab(TabPane tabPane, WebView preview) {
        addMarkdownTab(tabPane, preview, "Doc " + (tabPane.getTabs().size() + 1),
                "# Nouveau document\n\nTape du *Markdown* ici.", null);
    }

    private void addMarkdownTab(TabPane tabPane, WebView preview, String title, String content, File file) {
        Tab tab = new Tab(truncateTabName(title));
        tab.setTooltip(new Tooltip(title));

        TextArea editor = new TextArea(content);
        editor.setWrapText(true);

        tab.setContent(editor);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        tabFileMap.put(tab, file);
        tabSavedContent.put(tab, content);

        // Nettoyer les maps quand l'onglet est fermé
        tab.setOnClosed(e -> {
            tabFileMap.remove(tab);
            tabSavedContent.remove(tab);
        });

        // Confirmation avant fermeture si modifié
        tab.setOnCloseRequest(e -> {
            if (isTabModified(tab)) {
                e.consume(); // empêcher la fermeture immédiate
                handleCloseConfirmation(tab, tabPane);
            }
        });

        // Listener : met à jour la preview quand le texte change (onglet actif)
        ChangeListener<String> textListener = (obs, oldText, newText) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                updatePreview(preview, newText);
            }
            // Mettre à jour l'indicateur de modification dans le titre
            updateTabTitle(tab);
        };
        editor.textProperty().addListener(textListener);

        // Quand on change d'onglet, on rafraîchit la preview avec le texte de l'onglet
        // courant
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() instanceof TextArea ta) {
                updatePreview(preview, ta.getText());
            }
        });

        // Init preview
        updatePreview(preview, editor.getText());
    }

    // ========================================
    // Preview Markdown
    // ========================================

    private void updatePreview(WebView preview, String markdown) {
        String html = htmlRenderer.render(markdownParser.parse(markdown));
        String htmlPage = """
                <html>
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    pre { background: #f0f0f0; padding: 0.5em; }
                    code { font-family: monospace; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(html);
        preview.getEngine().loadContent(htmlPage);
    }

    // ========================================
    // Utilitaires
    // ========================================

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- Gestion des modifications ---
    private boolean isTabModified(Tab tab) {
        if (tab.getContent() instanceof TextArea editor) {
            String saved = tabSavedContent.get(tab);
            return saved == null || !saved.equals(editor.getText());
        }
        return false;
    }

    private static final int MAX_TAB_NAME_LENGTH = 15;

    private String truncateTabName(String name) {
        if (name.length() <= MAX_TAB_NAME_LENGTH) return name;
        return name.substring(0, MAX_TAB_NAME_LENGTH - 1) + "\u2026";
    }

    private void updateTabTitle(Tab tab) {
        String baseName = tabFileMap.get(tab) != null ? tabFileMap.get(tab).getName()
                : tab.getText().replaceFirst("^\\*", "");
        String truncated = truncateTabName(baseName);
        if (isTabModified(tab)) {
            if (!tab.getText().startsWith("*")) {
                tab.setText("*" + truncated);
            }
        } else {
            tab.setText(truncated);
        }
        tab.setTooltip(new Tooltip(baseName));
    }

    private void handleCloseConfirmation(Tab tab, TabPane tabPane) {
        String docName = tab.getText().replaceFirst("^\\*", "");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Document modifié");
        alert.setHeaderText("Le document \"" + docName + "\" a été modifié.");
        alert.setContentText("Voulez-vous sauvegarder avant de fermer ?");

        ButtonType saveBtn = new ButtonType("Sauvegarder");
        ButtonType discardBtn = new ButtonType("Fermer sans sauvegarder");
        ButtonType cancelBtn = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == saveBtn) {
                // Sélectionner l'onglet avant de sauvegarder
                tabPane.getSelectionModel().select(tab);
                saveFile(primaryStage, tabPane, false);
                // Fermer seulement si la sauvegarde a réussi (contenu == saved)
                if (!isTabModified(tab)) {
                    tabPane.getTabs().remove(tab);
                }
            } else if (result.get() == discardBtn) {
                tabPane.getTabs().remove(tab);
            }
            // Annuler : ne rien faire
        }
    }
}
