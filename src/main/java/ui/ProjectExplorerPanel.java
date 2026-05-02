package ui;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import utils.DocumentService;
import utils.FrontMatter;
import utils.GitService;
import utils.IndexService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Panel d'exploration de projet (arborescence de fichiers).
 */
public class ProjectExplorerPanel extends BasePanel {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    private final TreeView<File> treeView;
    private File projectDir;
    private GitService gitService;
    private Consumer<File> onFileDoubleClick;
    private Runnable onResetIndex;
    private Consumer<File> onFileCreated;
    private BiConsumer<File, File> onFileRenamed;
    private Consumer<File> onFileDeleted;
    private BiConsumer<List<File>, File> onFilesMoved;
    private BiConsumer<List<File>, File> onFilesCopied;

    // Git toolbar (buttons visibles selon mode)
    private HBox gitToolbar;
    private Button syncButton;
    private Button indexButton;
    // Advanced-mode buttons
    private Button pullButton;
    private Button pushButton;
    private Button commitButton;
    private Label  branchLabel;
    private ComboBox<String> branchCombo;
    private String gitToolbarMode = "standard";

    // Git action callbacks (set par MarkNote)
    private Runnable         onGitCommit;
    private Runnable         onGitInit;
    private Runnable         onGitAddRemote;
    private Consumer<File>   onGitAdd;
    private Consumer<File>   onGitRemoveFromIndex;
    private Runnable         onGitPull;
    private Runnable         onGitPush;
    private Runnable         onGitFetch;

    public ProjectExplorerPanel() {
        super("project.title", "project.close.tooltip");

        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        treeView.setCellFactory(tv -> new FileTreeCell());

        // Double-clic pour ouvrir un fichier
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue().isFile() && onFileDoubleClick != null) {
                    onFileDoubleClick.accept(selected.getValue());
                }
            }
        });

        // Menu contextuel
        ContextMenu contextMenu = createContextMenu();
        treeView.setContextMenu(contextMenu);

        // Barre d'outils git (cachée par défaut)
        syncButton = new Button("\u21C5");
        syncButton.setTooltip(new Tooltip(bundle.getString("git.sync.tooltip")));
        syncButton.getStyleClass().add("git-toolbar-button");
        syncButton.setOnAction(e -> { if (gitService != null) gitService.syncAsync(); });

        indexButton = new Button("\u21BB");
        indexButton.setTooltip(new Tooltip(bundle.getString("toolbar.index.tooltip")));
        indexButton.getStyleClass().add("git-toolbar-button");
        indexButton.setOnAction(e -> handleResetIndex());

        // Boutons mode avancé
        pullButton = new Button("\u2193");
        pullButton.setTooltip(new Tooltip(safeKey("git.pull", "Pull")));
        pullButton.getStyleClass().add("git-toolbar-button");
        pullButton.setOnAction(e -> { if (onGitPull   != null) onGitPull.run(); });

        pushButton = new Button("\u2191");
        pushButton.setTooltip(new Tooltip(safeKey("git.push", "Push")));
        pushButton.getStyleClass().add("git-toolbar-button");
        pushButton.setOnAction(e -> { if (onGitPush   != null) onGitPush.run(); });

        commitButton = new Button("\u2713");
        commitButton.setTooltip(new Tooltip(safeKey("git.commit", "Commit")));
        commitButton.getStyleClass().add("git-toolbar-button");
        commitButton.setOnAction(e -> { if (onGitCommit != null) onGitCommit.run(); });

        branchLabel = new Label();
        branchLabel.getStyleClass().add("git-branch-badge");

        branchCombo = new ComboBox<>();
        branchCombo.getStyleClass().add("git-branch-combo");
        branchCombo.setTooltip(new Tooltip(safeKey("git.branch", "Branche")));
        branchCombo.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        gitToolbar = new HBox(6, syncButton, pullButton, pushButton, commitButton, indexButton, spacer, branchCombo);
        gitToolbar.setPadding(new Insets(4, 6, 4, 6));
        gitToolbar.setAlignment(Pos.CENTER_LEFT);
        gitToolbar.getStyleClass().add("git-toolbar");
        gitToolbar.setVisible(false);
        gitToolbar.setManaged(false);

        VBox contentBox = new VBox(gitToolbar, treeView);
        VBox.setVgrow(treeView, Priority.ALWAYS);
        setContent(contentBox);
        setPrefWidth(250);
        setMaxHeight(Double.MAX_VALUE);
    }

    /**
     * Crée le menu contextuel pour les opérations sur les fichiers.
     */
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem newFileItem = new MenuItem(bundle.getString("context.newFile"));
        newFileItem.setOnAction(e -> handleNewFile());

        MenuItem newFolderItem = new MenuItem(bundle.getString("context.newFolder"));
        newFolderItem.setOnAction(e -> handleNewFolder());

        MenuItem renameItem = new MenuItem(bundle.getString("context.rename"));
        renameItem.setOnAction(e -> handleRename());

        MenuItem deleteItem = new MenuItem(bundle.getString("context.delete"));
        deleteItem.setOnAction(e -> handleDelete());

        MenuItem resetIndexItem = new MenuItem(bundle.getString("context.resetIndex"));
        resetIndexItem.setOnAction(e -> handleResetIndex());

        // --- Section git dans le menu contextuel ---
        SeparatorMenuItem gitSep1  = new SeparatorMenuItem();
        MenuItem gitAddItem        = new MenuItem(safeKey("git.add",              "git add"));
        MenuItem gitCommitItem     = new MenuItem(safeKey("git.commit",           "Commit\u2026"));
        SeparatorMenuItem gitSep2  = new SeparatorMenuItem();
        MenuItem gitPullItem       = new MenuItem(safeKey("git.pull",             "Pull"));
        MenuItem gitPushItem       = new MenuItem(safeKey("git.push",             "Push"));
        MenuItem gitFetchItem      = new MenuItem(safeKey("git.fetch",            "Fetch"));
        SeparatorMenuItem gitSep3  = new SeparatorMenuItem();
        MenuItem gitRemoveItem     = new MenuItem(safeKey("git.remove.from.index","Retirer de l'index"));
        SeparatorMenuItem gitSep4  = new SeparatorMenuItem();
        MenuItem gitInitItem       = new MenuItem(safeKey("git.init",             "Initialiser Git\u2026"));
        MenuItem gitAddRemoteItem  = new MenuItem(safeKey("git.add.remote",       "Ajouter un remote\u2026"));

        gitAddItem.setOnAction(e -> {
            if (onGitAdd != null) {
                TreeItem<File> sel = treeView.getSelectionModel().getSelectedItem();
                if (sel != null) onGitAdd.accept(sel.getValue());
            }
        });
        gitCommitItem.setOnAction(e -> { if (onGitCommit    != null) onGitCommit.run(); });
        gitPullItem.setOnAction(e   -> { if (onGitPull      != null) onGitPull.run(); });
        gitPushItem.setOnAction(e   -> { if (onGitPush      != null) onGitPush.run(); });
        gitFetchItem.setOnAction(e  -> { if (onGitFetch     != null) onGitFetch.run(); });
        gitRemoveItem.setOnAction(e -> {
            if (onGitRemoveFromIndex != null) {
                TreeItem<File> sel = treeView.getSelectionModel().getSelectedItem();
                if (sel != null) onGitRemoveFromIndex.accept(sel.getValue());
            }
        });
        gitInitItem.setOnAction(e      -> { if (onGitInit      != null) onGitInit.run(); });
        gitAddRemoteItem.setOnAction(e -> { if (onGitAddRemote != null) onGitAddRemote.run(); });

        menu.getItems().addAll(newFileItem, newFolderItem, new SeparatorMenuItem(), renameItem, new SeparatorMenuItem(), deleteItem, new SeparatorMenuItem(), resetIndexItem,
                gitSep1, gitAddItem, gitCommitItem,
                gitSep2, gitPullItem, gitPushItem, gitFetchItem,
                gitSep3, gitRemoveItem,
                gitSep4, gitInitItem, gitAddRemoteItem);

        // Désactiver les items si aucune sélection
        menu.setOnShowing(e -> {
            TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
            boolean hasSelection = selected != null;
            renameItem.setDisable(!hasSelection);
            deleteItem.setDisable(!hasSelection);
            // Nouveau fichier/dossier : actif si sélection est un dossier ou un fichier (on crée dans le parent)
            newFileItem.setDisable(!hasSelection);
            newFolderItem.setDisable(!hasSelection);
            // Reset index : uniquement sur le nœud racine
            boolean isRoot = hasSelection && selected == treeView.getRoot();
            resetIndexItem.setDisable(!isRoot);

            // Items git : visibles seulement si gitService actif
            boolean isGit    = gitService != null && gitService.isGitRepo();
            boolean hasRemote = isGit && gitService.hasRemote();
            boolean fileSelected = hasSelection && selected.getValue() != null && selected.getValue().isFile();
            boolean isStageable = fileSelected && isGit && gitService.getStatus(selected.getValue()) != GitService.GitStatus.CLEAN
                                                        && gitService.getStatus(selected.getValue()) != GitService.GitStatus.STAGED;
            boolean isIndexed   = fileSelected && isGit && gitService.getStatus(selected.getValue()) == GitService.GitStatus.STAGED;

            gitSep1.setVisible(isGit);
            gitAddItem.setVisible(isStageable);
            gitCommitItem.setVisible(isGit);
            gitSep2.setVisible(isGit && hasRemote);
            gitPullItem.setVisible(isGit && hasRemote);
            gitPushItem.setVisible(isGit && hasRemote);
            gitFetchItem.setVisible(isGit && hasRemote);
            gitSep3.setVisible(isIndexed);
            gitRemoveItem.setVisible(isIndexed);
            // Init/AddRemote (root entry)
            gitSep4.setVisible(!isGit || !hasRemote);
            gitInitItem.setVisible(!isGit && isRoot);
            gitAddRemoteItem.setVisible(isGit && !hasRemote && isRoot);
        });

        return menu;
    }

    /**
     * Retourne le répertoire cible pour les opérations (le dossier sélectionné ou le parent du fichier sélectionné).
     */
    private File getTargetDirectory() {
        TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) return projectDir;
        File file = selected.getValue();
        return file.isDirectory() ? file : file.getParentFile();
    }

    /**
     * Gère la création d'un nouveau fichier.
     */
    private void handleNewFile() {
        File targetDir = getTargetDirectory();
        if (targetDir == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(bundle.getString("context.newFile.title"));
        dialog.setHeaderText(bundle.getString("context.newFile.header"));
        dialog.setContentText(bundle.getString("context.newFile.prompt"));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isBlank()) {
                Optional<File> created = DocumentService.createFile(targetDir, name);
                if (created.isPresent()) {
                    refreshGitAware();
                    if (onFileCreated != null) onFileCreated.accept(created.get());
                } else {
                    showError(bundle.getString("context.error.create"), name);
                }
            }
        });
    }

    /**
     * Gère la création d'un nouveau répertoire.
     */
    private void handleNewFolder() {
        File targetDir = getTargetDirectory();
        if (targetDir == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(bundle.getString("context.newFolder.title"));
        dialog.setHeaderText(bundle.getString("context.newFolder.header"));
        dialog.setContentText(bundle.getString("context.newFolder.prompt"));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isBlank()) {
                Optional<File> created = DocumentService.createDirectory(targetDir, name);
                if (created.isPresent()) {
                    refreshGitAware();
                } else {
                    showError(bundle.getString("context.error.create"), name);
                }
            }
        });
    }

    /**
     * Gère le renommage d'un fichier ou répertoire.
     */
    private void handleRename() {
        TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        File file = selected.getValue();
        TextInputDialog dialog = new TextInputDialog(file.getName());
        dialog.setTitle(bundle.getString("context.rename.title"));
        dialog.setHeaderText(bundle.getString("context.rename.header").replace("{0}", file.getName()));
        dialog.setContentText(bundle.getString("context.rename.prompt"));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.isBlank() && !newName.equals(file.getName())) {
                Optional<File> renamed = DocumentService.rename(file, newName);
                if (renamed.isPresent()) {
                    refreshGitAware();
                    if (onFileRenamed != null) onFileRenamed.accept(file, renamed.get());
                } else {
                    showError(bundle.getString("context.error.rename"), file.getName());
                }
            }
        });
    }

    /**
     * Gère la suppression d'un fichier ou répertoire.
     */
    private void handleDelete() {
        TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        File file = selected.getValue();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("context.delete.title"));
        alert.setHeaderText(bundle.getString("context.delete.header").replace("{0}", file.getName()));

        if (file.isDirectory()) {
            long count = DocumentService.countFilesInDirectory(file);
            alert.setContentText(bundle.getString("context.delete.folder.content").replace("{0}", String.valueOf(count)));
        } else {
            alert.setContentText(bundle.getString("context.delete.file.content"));
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Notify before deletion (file still exists)
            if (onFileDeleted != null) onFileDeleted.accept(file);
            if (DocumentService.delete(file)) {
                refreshGitAware();
            } else {
                showError(bundle.getString("context.error.delete"), file.getName());
            }
        }
    }

    /**
     * Gère la réinitialisation de l'index du projet.
     */
    private void handleResetIndex() {
        if (onResetIndex != null) {
            onResetIndex.run();
        }
    }

    /**
     * Définit l'action à exécuter pour réinitialiser l'index.
     *
     * @param action L'action à exécuter
     */
    public void setOnResetIndex(Runnable action) {
        this.onResetIndex = action;
    }

    /**
     * Définit le callback appelé quand un fichier est créé.
     */
    public void setOnFileCreated(Consumer<File> action) {
        this.onFileCreated = action;
    }

    /**
     * Définit le callback appelé quand un fichier est renommé.
     * Le premier argument est l'ancien fichier, le second le nouveau.
     */
    public void setOnFileRenamed(BiConsumer<File, File> action) {
        this.onFileRenamed = action;
    }

    /**
     * Définit le callback appelé quand un fichier ou répertoire est supprimé.
     */
    public void setOnFileDeleted(Consumer<File> action) {
        this.onFileDeleted = action;
    }

    /**
     * Définit le callback appelé quand des fichiers sont déplacés (drag & drop interne).
     * Premier argument : les fichiers source (chemin avant déplacement).
     * Second argument : le répertoire de destination.
     */
    public void setOnFilesMoved(BiConsumer<List<File>, File> action) {
        this.onFilesMoved = action;
    }

    /**
     * Définit le callback appelé quand des fichiers sont copiés (drag & drop externe).
     * Premier argument : les fichiers source.
     * Second argument : le répertoire de destination.
     */
    public void setOnFilesCopied(BiConsumer<List<File>, File> action) {
        this.onFilesCopied = action;
    }

    /**
     * Affiche une boîte de dialogue d'erreur.
     */
    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Définit le répertoire de projet et rafraîchit l'arborescence.
     *
     * @param dir Le répertoire de projet
     */
    public void setProjectDirectory(File dir) {
        this.projectDir = dir;
        refresh();
    }

    /**
     * Retourne le répertoire de projet courant.
     *
     * @return Le répertoire de projet
     */
    public File getProjectDirectory() {
        return projectDir;
    }

    /**
     * Définit le service git à utiliser pour les indicateurs visuels et les
     * opérations pull/push.
     */
    public void setGitService(GitService service) {
        this.gitService = service;
        if (service != null) {
            service.currentBranchProperty().addListener((obs, oldVal, newVal) -> updateBranchCombo(newVal));
            updateBranchCombo(service.currentBranch());
        } else {
            branchCombo.getItems().clear();
        }
    }

    /** Définit le mode de la barre d'outils git ("standard" ou "advanced"). */
    public void setGitToolbarMode(String mode) {
        this.gitToolbarMode = (mode != null && !mode.isBlank()) ? mode : "standard";
        updateGitToolbar();
    }

    /**
     * Rafraîchit l'arborescence du projet en préservant l'état d'expansion des
     * répertoires et la sélection courante.
     */
    public void refresh() {
        if (projectDir == null || !projectDir.isDirectory()) {
            treeView.setRoot(null);
            updateGitToolbar();
            return;
        }

        // Mémoriser les répertoires actuellement dépliés et le fichier sélectionné
        Set<File> expandedDirs = TreeExpansionState.collectExpanded(asNode(treeView.getRoot()));
        File selectedFile = null;
        TreeItem<File> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            selectedFile = selectedItem.getValue();
        }

        TreeItem<File> rootItem = buildTreeItem(projectDir);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

        // Restaurer l'état d'expansion des répertoires
        TreeExpansionState.restoreExpanded(asNode(treeView.getRoot()), expandedDirs);

        updateGitToolbar();

        // Restaurer la sélection (révèle également tous les ancêtres)
        if (selectedFile != null) {
            revealFile(selectedFile);
        }
    }

    /**
     * Adapts a {@link TreeItem}{@code <File>} to {@link TreeExpansionState.Node}{@code <File>}.
     * Returns {@code null} when {@code item} is {@code null}.
     */
    private static TreeExpansionState.Node<File> asNode(TreeItem<File> item) {
        if (item == null) return null;
        return new TreeExpansionState.Node<File>() {
            @Override public File getValue() { return item.getValue(); }
            @Override public boolean isExpanded() { return item.isExpanded(); }
            @Override public void setExpanded(boolean expanded) { item.setExpanded(expanded); }
            @Override public List<TreeExpansionState.Node<File>> getChildren() {
                return item.getChildren().stream()
                        .map(ProjectExplorerPanel::asNode)
                        .collect(Collectors.toList());
            }
            @Override public boolean isCollectable() {
                return item.getValue() != null && item.getValue().isDirectory();
            }
        };
    }

    /**
     * Fait défiler et sélectionne l'entrée correspondant au fichier donné dans
     * l'arborescence : tous les nœuds parents sont dépliés et le nœud est mis
     * en surbrillance et rendu visible.
     *
     * @param file Fichier à révéler (peut être {@code null}, auquel cas rien ne se passe).
     */
    public void revealFile(File file) {
        if (file == null || treeView.getRoot() == null) return;
        TreeItem<File> item = findTreeItem(treeView.getRoot(), file);
        if (item == null) return;
        // Déplie tous les ancêtres
        TreeItem<File> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
        // Sélectionne et fait défiler jusqu'au nœud
        treeView.getSelectionModel().clearSelection();
        treeView.getSelectionModel().select(item);
        int row = treeView.getRow(item);
        if (row >= 0) {
            treeView.scrollTo(row);
        }
    }

    /** Recherche récursivement le {@link TreeItem} dont la valeur correspond à {@code target}. */
    private TreeItem<File> findTreeItem(TreeItem<File> node, File target) {
        if (node.getValue() != null && node.getValue().equals(target)) return node;
        for (TreeItem<File> child : node.getChildren()) {
            TreeItem<File> found = findTreeItem(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private void updateBranchCombo(String branch) {
        if (branch == null || branch.isBlank()) {
            branchCombo.getItems().clear();
            return;
        }
        if (!branchCombo.getItems().contains(branch)) {
            branchCombo.getItems().setAll(branch);
        }
        branchCombo.getSelectionModel().select(branch);
    }

    /** Met à jour la visibilité de la barre d'outils git. */
    private void updateGitToolbar() {
        boolean isGit    = gitService != null && gitService.isGitRepo();
        boolean hasProj  = projectDir != null && projectDir.isDirectory();
        boolean hasRemote = isGit && gitService.hasRemote();
        boolean advanced  = "advanced".equals(gitToolbarMode);

        gitToolbar.setVisible(isGit || hasProj);
        gitToolbar.setManaged(isGit || hasProj);

        // Standard mode: Sync + Index
        syncButton.setVisible(isGit && !advanced);
        syncButton.setManaged(isGit && !advanced);

        // Advanced mode: Pull + Push + Commit + Branch badge
        pullButton.setVisible(isGit && advanced && hasRemote);
        pullButton.setManaged(isGit && advanced && hasRemote);
        pushButton.setVisible(isGit && advanced && hasRemote);
        pushButton.setManaged(isGit && advanced && hasRemote);
        commitButton.setVisible(isGit && advanced);
        commitButton.setManaged(isGit && advanced);
        branchCombo.setVisible(isGit && advanced);
        branchCombo.setManaged(isGit && advanced);

        // Index always visible when project loaded
        indexButton.setVisible(hasProj);
        indexButton.setManaged(hasProj);
    }

    /**
     * Rafraîchit l'arborescence en tenant compte du statut git.
     * Si git est actif, lance un refresh asynchrone du statut git puis reconstruit
     * l'arbre via le callback {@code onStatusUpdated} (positionné dans MarkNote).
     * Sinon, reconstruit l'arbre directement.
     */
    private void refreshGitAware() {
        if (gitService != null && gitService.isGitRepo()) {
            gitService.refreshStatusAsync();
        } else {
            refresh();
        }
    }

    /**
     * Définit l'action à exécuter lors d'un double-clic sur un fichier.
     *
     * @param action L'action à exécuter (reçoit le fichier cliqué)
     */
    public void setOnFileDoubleClick(Consumer<File> action) {
        this.onFileDoubleClick = action;
    }

    /**
     * Construit récursivement l'arborescence des fichiers.
     */
    private TreeItem<File> buildTreeItem(File file) {
        TreeItem<File> item = new TreeItem<>(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                // Dossiers d'abord, puis fichiers, triés alphabétiquement
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory())
                        return -1;
                    if (!a.isDirectory() && b.isDirectory())
                        return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File child : children) {
                    // Ignorer les fichiers/dossiers cachés et le fichier d'index
                    if (!child.getName().startsWith(".")
                            && !child.getName().equals(IndexService.INDEX_FILENAME)) {
                        item.getChildren().add(buildTreeItem(child));
                    }
                }
            }
        }
        return item;
    }

    /**
     * Cellule personnalisée pour afficher les fichiers avec des icônes et support du drag & drop.
     */
    private class FileTreeCell extends TreeCell<File> {
        
        public FileTreeCell() {
            // Drag detected - début du drag
            setOnDragDetected(event -> {
                if (getItem() == null) return;
                
                List<TreeItem<File>> selectedItems = treeView.getSelectionModel().getSelectedItems();
                if (selectedItems.isEmpty()) return;
                
                // Ne pas permettre de déplacer la racine
                if (selectedItems.stream().anyMatch(item -> item == treeView.getRoot())) return;
                
                Dragboard dragboard = startDragAndDrop(TransferMode.COPY, TransferMode.MOVE, TransferMode.LINK);
                ClipboardContent content = new ClipboardContent();
                
                List<File> files = selectedItems.stream()
                        .map(TreeItem::getValue)
                        .collect(Collectors.toList());
                content.putFiles(files);
                
                dragboard.setContent(content);
                event.consume();
            });
            
            // Drag over - survol pendant le drag
            setOnDragOver(event -> {
                if (event.getDragboard().hasFiles()) {
                    File target = getItem();
                    if (target != null && target.isDirectory()) {
                        // Interne = MOVE, Externe = COPY
                        if (event.getGestureSource() instanceof FileTreeCell) {
                            event.acceptTransferModes(TransferMode.MOVE);
                        } else {
                            event.acceptTransferModes(TransferMode.COPY);
                        }
                    }
                }
                event.consume();
            });
            
            // Drag entered - entrée dans la cellule
            setOnDragEntered(event -> {
                if (event.getDragboard().hasFiles()) {
                    File target = getItem();
                    if (target != null && target.isDirectory()) {
                        // Couleur différente pour copie externe vs déplacement interne
                        if (event.getGestureSource() instanceof FileTreeCell) {
                            setStyle("-fx-background-color: lightblue;");
                        } else {
                            setStyle("-fx-background-color: lightgreen;");
                        }
                    }
                }
            });
            
            // Drag exited - sortie de la cellule
            setOnDragExited(event -> {
                setStyle("");
            });
            
            // Drop - déposer les fichiers
            setOnDragDropped(event -> {
                Dragboard dragboard = event.getDragboard();
                boolean success = false;
                boolean isExternalDrop = !(event.getGestureSource() instanceof FileTreeCell);
                
                if (dragboard.hasFiles()) {
                    File targetDir = getItem();
                    List<File> files = dragboard.getFiles();
                    
                    if (targetDir != null && targetDir.isDirectory() && !files.isEmpty()) {
                        if (isExternalDrop) {
                            // Copie depuis l'extérieur
                            if (confirmCopy(files, targetDir)) {
                                int copied = DocumentService.copyAll(files, targetDir);
                                if (copied > 0) {
                                    refreshGitAware();
                                    if (onFilesCopied != null) onFilesCopied.accept(files, targetDir);
                                    success = true;
                                } else {
                                    showError(bundle.getString("context.error.copy"), targetDir.getName());
                                }
                            }
                        } else {
                            // Déplacement interne
                            List<File> validFiles = files.stream()
                                    .filter(f -> !f.equals(targetDir))
                                    .filter(f -> !targetDir.toPath().startsWith(f.toPath()))
                                    .collect(Collectors.toList());
                            
                            if (!validFiles.isEmpty() && confirmMove(validFiles, targetDir)) {
                                // Capture source paths before move
                                List<File> sourceFiles = validFiles.stream()
                                        .map(f -> new File(f.getAbsolutePath()))
                                        .collect(Collectors.toList());
                                int moved = DocumentService.moveAll(validFiles, targetDir);
                                if (moved > 0) {
                                    refreshGitAware();
                                    if (onFilesMoved != null) onFilesMoved.accept(sourceFiles, targetDir);
                                    success = true;
                                } else {
                                    showError(bundle.getString("context.error.move"), targetDir.getName());
                                }
                            }
                        }
                    }
                }
                
                event.setDropCompleted(success);
                event.consume();
            });
            
            // Drag done - fin du drag
            setOnDragDone(DragEvent::consume);
        }
        
        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                String displayName = item.getName().isEmpty() ? item.getPath() : item.getName();

                // Pour les fichiers .md, tenter d'afficher le titre du front matter
                if (!item.isDirectory() && item.getName().toLowerCase().endsWith(".md")) {
                    String fmTitle = extractFrontMatterTitle(item);
                    if (fmTitle != null && !fmTitle.isBlank()) {
                        displayName = fmTitle;
                        setTooltip(new javafx.scene.control.Tooltip(item.getName() + " \u2014 " + fmTitle));
                    }
                }

                setText(displayName);
                String iconPath = item.isDirectory()
                        ? "images/icons/folder-invoices--v1.png"
                        : "images/icons/file.png";
                ImageView iconView = new ImageView(new Image(iconPath));

                // Indicateur git (uniquement pour les fichiers, pas les dossiers)
                if (!item.isDirectory() && gitService != null && gitService.isGitRepo()) {
                    GitService.GitStatus status = gitService.getStatus(item);
                    if (status != null) {
                        Circle dot = new Circle(4, statusColor(status));
                        HBox graphic = new HBox(3, dot, iconView);
                        graphic.setAlignment(Pos.CENTER_LEFT);
                        setGraphic(graphic);
                    } else {
                        setGraphic(iconView);
                    }
                } else {
                    setGraphic(iconView);
                }
            }
        }

        /** Retourne la couleur associée au statut git d'un fichier. */
        private Color statusColor(GitService.GitStatus status) {
            return switch (status) {
                case UNTRACKED -> Color.web("#e74c3c"); // rouge
                case MODIFIED  -> Color.web("#f39c12"); // orange
                case STAGED    -> Color.web("#3498db"); // bleu
                case CLEAN     -> Color.web("#2ecc71"); // vert
            };
        }

        /**
         * Lit uniquement les premières lignes d'un fichier .md pour en extraire
         * le titre du front matter, sans charger tout le fichier.
         */
        private String extractFrontMatterTitle(File file) {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
                if (lines.isEmpty() || !lines.get(0).trim().equals("---")) return null;

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lines.size(); i++) {
                    sb.append(lines.get(i)).append('\n');
                    // Dès qu'on trouve le second ---, on arrête
                    if (i > 0 && lines.get(i).trim().equals("---")) break;
                }
                FrontMatter fm = FrontMatter.parse(sb.toString());
                return fm != null ? fm.getTitle() : null;
            } catch (Exception e) {
                return null;
            }
        }
    }
    
    /**
     * Demande confirmation pour le déplacement de fichiers.
     *
     * @param files     Les fichiers à déplacer
     * @param targetDir Le répertoire de destination
     * @return true si l'utilisateur confirme
     */
    private boolean confirmMove(List<File> files, File targetDir) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("context.move.title"));
        
        if (files.size() == 1) {
            alert.setHeaderText(bundle.getString("context.move.header.single")
                    .replace("{0}", files.get(0).getName())
                    .replace("{1}", targetDir.getName()));
        } else {
            alert.setHeaderText(bundle.getString("context.move.header.multi")
                    .replace("{0}", String.valueOf(files.size()))
                    .replace("{1}", targetDir.getName()));
        }
        
        if (files.size() < 4) {
            String fileNames = files.stream()
                    .map(File::getName)
                    .collect(Collectors.joining(", "));
            alert.setContentText(bundle.getString("context.move.content.files")
                    .replace("{0}", fileNames));
        } else {
            alert.setContentText(bundle.getString("context.move.content.count")
                    .replace("{0}", String.valueOf(files.size())));
        }
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * Demande confirmation pour la copie de fichiers externes.
     *
     * @param files     Les fichiers à copier
     * @param targetDir Le répertoire de destination
     * @return true si l'utilisateur confirme
     */
    private boolean confirmCopy(List<File> files, File targetDir) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("context.copy.title"));
        
        if (files.size() == 1) {
            alert.setHeaderText(bundle.getString("context.copy.header.single")
                    .replace("{0}", files.get(0).getName())
                    .replace("{1}", targetDir.getName()));
            alert.setContentText(bundle.getString("context.copy.content.single"));
        } else {
            alert.setHeaderText(bundle.getString("context.copy.header.multi")
                    .replace("{0}", String.valueOf(files.size()))
                    .replace("{1}", targetDir.getName()));
            alert.setContentText(bundle.getString("context.copy.content.count")
                    .replace("{0}", String.valueOf(files.size())));
        }
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    // -------------------------------------------------------------------------
    // Setters pour les callbacks git
    // -------------------------------------------------------------------------

    public void setOnGitCommit(Runnable cb)               { this.onGitCommit         = cb; }
    public void setOnGitInit(Runnable cb)                  { this.onGitInit           = cb; }
    public void setOnGitAddRemote(Runnable cb)             { this.onGitAddRemote      = cb; }
    public void setOnGitAdd(Consumer<File> cb)             { this.onGitAdd            = cb; }
    public void setOnGitRemoveFromIndex(Consumer<File> cb) { this.onGitRemoveFromIndex = cb; }
    public void setOnGitPull(Runnable cb)                  { this.onGitPull           = cb; }
    public void setOnGitPush(Runnable cb)                  { this.onGitPush           = cb; }
    public void setOnGitFetch(Runnable cb)                 { this.onGitFetch          = cb; }

    // -------------------------------------------------------------------------
    // Utilitaire i18n
    // -------------------------------------------------------------------------

    private static String safeKey(String key, String fallback) {
        try { return bundle.getString(key); } catch (Exception e) { return fallback; }
    }
}
