package ui;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import utils.DocumentService;
import utils.FrontMatter;
import utils.IndexService;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
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

/**
 * Panel d'exploration de projet (arborescence de fichiers).
 */
public class ProjectExplorerPanel extends BasePanel {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    private final TreeView<File> treeView;
    private File projectDir;
    private Consumer<File> onFileDoubleClick;
    private Runnable onResetIndex;
    private Consumer<File> onFileCreated;
    private BiConsumer<File, File> onFileRenamed;
    private Consumer<File> onFileDeleted;
    private BiConsumer<List<File>, File> onFilesMoved;
    private BiConsumer<List<File>, File> onFilesCopied;

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

        setContent(treeView);
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

        menu.getItems().addAll(newFileItem, newFolderItem, new SeparatorMenuItem(), renameItem, new SeparatorMenuItem(), deleteItem, new SeparatorMenuItem(), resetIndexItem);

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
                    refresh();
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
                    refresh();
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
                    refresh();
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
                refresh();
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
     * Rafraîchit l'arborescence du projet.
     */
    public void refresh() {
        if (projectDir == null || !projectDir.isDirectory()) {
            treeView.setRoot(null);
            return;
        }

        TreeItem<File> rootItem = buildTreeItem(projectDir);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
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
                                    refresh();
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
                                    refresh();
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
                setGraphic(new ImageView(new Image(iconPath)));
            }
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
}
