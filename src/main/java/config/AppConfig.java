package config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Gère la configuration persistante de l'application.
 */
public class AppConfig {

    private static final String CONFIG_FILE = System.getProperty("user.home") + File.separator + ".marknote.conf";

    private final LinkedList<String> recentFiles = new LinkedList<>();
    private final LinkedList<String> recentDirs = new LinkedList<>();
    private int maxRecentItems = 10;
    private boolean openDocOnStart = true;
    private boolean reopenLastProject = false;
    private boolean showWelcomePage = true;

    /**
     * Charge la configuration depuis le fichier.
     */
    public void load() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

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
                    if (!path.isEmpty()) {
                        recentFiles.add(path);
                    }
                } else if (line.startsWith("recentDir=")) {
                    String path = line.substring("recentDir=".length()).trim();
                    if (!path.isEmpty()) {
                        recentDirs.add(path);
                    }
                } else if (line.startsWith("openDocOnStart=")) {
                    openDocOnStart = Boolean.parseBoolean(line.substring("openDocOnStart=".length()).trim());
                } else if (line.startsWith("reopenLastProject=")) {
                    reopenLastProject = Boolean.parseBoolean(line.substring("reopenLastProject=".length()).trim());
                } else if (line.startsWith("showWelcomePage=")) {
                    showWelcomePage = Boolean.parseBoolean(line.substring("showWelcomePage=".length()).trim());
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Sauvegarde la configuration dans le fichier.
     */
    public void save() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("maxRecentItems=" + maxRecentItems);
            lines.add("openDocOnStart=" + openDocOnStart);
            lines.add("reopenLastProject=" + reopenLastProject);
            lines.add("showWelcomePage=" + showWelcomePage);
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

    /**
     * Ajoute un fichier à la liste des fichiers récents.
     *
     * @param file Le fichier à ajouter
     */
    public void addRecentFile(File file) {
        String path = file.getAbsolutePath();
        recentFiles.remove(path);
        recentFiles.addFirst(path);
        while (recentFiles.size() > maxRecentItems) {
            recentFiles.removeLast();
        }
        save();
    }

    /**
     * Ajoute un répertoire à la liste des répertoires récents.
     *
     * @param dir Le répertoire à ajouter
     */
    public void addRecentDir(File dir) {
        String path = dir.getAbsolutePath();
        recentDirs.remove(path);
        recentDirs.addFirst(path);
        while (recentDirs.size() > maxRecentItems) {
            recentDirs.removeLast();
        }
        save();
    }

    /**
     * Efface l'historique des fichiers et répertoires récents.
     */
    public void clearHistory() {
        recentFiles.clear();
        recentDirs.clear();
        save();
    }

    // --- Getters ---

    public LinkedList<String> getRecentFiles() {
        return recentFiles;
    }

    public LinkedList<String> getRecentDirs() {
        return recentDirs;
    }

    public int getMaxRecentItems() {
        return maxRecentItems;
    }

    public boolean isOpenDocOnStart() {
        return openDocOnStart;
    }

    public boolean isReopenLastProject() {
        return reopenLastProject;
    }

    public boolean isShowWelcomePage() {
        return showWelcomePage;
    }

    // --- Setters ---

    public void setMaxRecentItems(int maxRecentItems) {
        this.maxRecentItems = maxRecentItems;
        // Tronquer les listes si nécessaire
        while (recentFiles.size() > maxRecentItems) {
            recentFiles.removeLast();
        }
        while (recentDirs.size() > maxRecentItems) {
            recentDirs.removeLast();
        }
    }

    public void setOpenDocOnStart(boolean openDocOnStart) {
        this.openDocOnStart = openDocOnStart;
    }

    public void setReopenLastProject(boolean reopenLastProject) {
        this.reopenLastProject = reopenLastProject;
    }

    public void setShowWelcomePage(boolean showWelcomePage) {
        this.showWelcomePage = showWelcomePage;
    }

    /**
     * Supprime un fichier de la liste des récents.
     *
     * @param path Le chemin du fichier
     */
    public void removeRecentFile(String path) {
        recentFiles.remove(path);
        save();
    }

    /**
     * Supprime un répertoire de la liste des récents.
     *
     * @param path Le chemin du répertoire
     */
    public void removeRecentDir(String path) {
        recentDirs.remove(path);
        save();
    }
}
