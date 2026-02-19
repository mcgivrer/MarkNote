package ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import utils.DocumentService;

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
import javafx.scene.input.DataFormat;
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

        menu.getItems().addAll(newFileItem, newFolderItem, new SeparatorMenuItem(), renameItem, new SeparatorMenuItem(), deleteItem);

        // Désactiver les items si aucune sélection
        menu.setOnShowing(e -> {
            TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
            boolean hasSelection = selected != null;
            renameItem.setDisable(!hasSelection);
            deleteItem.setDisable(!hasSelection);
            // Nouveau fichier/dossier : actif si sélection est un dossier ou un fichier (on crée dans le parent)
            newFileItem.setDisable(!hasSelection);
            newFolderItem.setDisable(!hasSelection);
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
            if (DocumentService.delete(file)) {
                refresh();
            } else {
                showError(bundle.getString("context.error.delete"), file.getName());
            }
        }
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
                    // Ignorer les fichiers/dossiers cachés
                    if (!child.getName().startsWith(".")) {
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
        
        private static final DataFormat FILE_LIST_FORMAT = new DataFormat("application/x-java-file-list");
        
        public FileTreeCell() {
            // Drag detected - début du drag
            setOnDragDetected(event -> {
                if (getItem() == null) return;
                
                List<TreeItem<File>> selectedItems = treeView.getSelectionModel().getSelectedItems();
                if (selectedItems.isEmpty()) return;
                
                // Ne pas permettre de déplacer la racine
                if (selectedItems.stream().anyMatch(item -> item == treeView.getRoot())) return;
                
                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
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
                if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                    File target = getItem();
                    if (target != null && target.isDirectory()) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });
            
            // Drag entered - entrée dans la cellule
            setOnDragEntered(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                    File target = getItem();
                    if (target != null && target.isDirectory()) {
                        setStyle("-fx-background-color: lightblue;");
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
                
                if (dragboard.hasFiles()) {
                    File targetDir = getItem();
                    List<File> files = dragboard.getFiles();
                    
                    if (targetDir != null && targetDir.isDirectory() && !files.isEmpty()) {
                        // Filtrer les fichiers qui ne peuvent pas être déplacés vers ce dossier
                        List<File> validFiles = files.stream()
                                .filter(f -> !f.equals(targetDir))
                                .filter(f -> !targetDir.toPath().startsWith(f.toPath()))
                                .collect(Collectors.toList());
                        
                        if (!validFiles.isEmpty() && confirmMove(validFiles, targetDir)) {
                            int moved = DocumentService.moveAll(validFiles, targetDir);
                            if (moved > 0) {
                                refresh();
                                success = true;
                            } else {
                                showError(bundle.getString("context.error.move"), targetDir.getName());
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
            } else {
                setText(item.getName().isEmpty() ? item.getPath() : item.getName());
                String iconPath = item.isDirectory() 
                        ? "images/icons/folder-invoices--v1.png" 
                        : "images/icons/file.png";
                setGraphic(new ImageView(new Image(iconPath)));
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
}
