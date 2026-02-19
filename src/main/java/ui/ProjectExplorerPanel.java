package ui;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;

/**
 * Panel d'exploration de projet (arborescence de fichiers).
 */
public class ProjectExplorerPanel extends BasePanel {

    private final TreeView<File> treeView;
    private File projectDir;
    private Consumer<File> onFileDoubleClick;

    public ProjectExplorerPanel() {
        super("project.title", "project.close.tooltip");

        treeView = new TreeView<>();
        treeView.setShowRoot(true);
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

        setContent(treeView);
        setPrefWidth(250);
        setMaxHeight(Double.MAX_VALUE);
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
     * Cellule personnalisée pour afficher les fichiers avec des icônes.
     */
    private static class FileTreeCell extends TreeCell<File> {
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
}
