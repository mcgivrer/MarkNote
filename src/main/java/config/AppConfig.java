package config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Gère la configuration persistante de l'application.
 */
public class AppConfig {

    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".marknote";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "config";

    private final LinkedList<String> recentFiles = new LinkedList<>();
    private final LinkedList<String> recentDirs = new LinkedList<>();
    private int maxRecentItems = 10;
    private boolean openDocOnStart = true;
    private boolean reopenLastProject = false;
    private boolean showWelcomePage = true;
    private boolean showSplashScreen = true;
    private String currentTheme = "light";
    private String language = "system";
    private boolean frontMatterExpandedByDefault = true;
    private boolean useLocalPlantUml = false;
    private String plantUmlJarPath = "";
    private boolean reattachDiagramOnTabClose = true;
    private final Map<String, PanelState> panelStates = new HashMap<>();

    // Git credentials (V1: SSH passphrase-less + HTTPS token)
    private String gitSshKeyPath = "";
    private String gitToken = "";
    private String gitUsername = "token";
    private String gitToolbarMode = "standard";

    // Window geometry — persisted so workspace survives hard kill
    private double windowX = -1;
    private double windowY = -1;
    private double windowWidth = 1200;
    private double windowHeight = 700;
    private boolean windowMaximized = false;
    private boolean windowFullscreen = false;

    // Workspace restore
    private boolean restoreWorkspaceOnStart = true;
    private boolean autoCheckUpdate = true;
    private String skipVersion = null;

    public record PanelState(boolean visible, boolean docked, String zone) {
    }

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
                } else if (line.startsWith("showSplashScreen=")) {
                    showSplashScreen = Boolean.parseBoolean(line.substring("showSplashScreen=".length()).trim());
                } else if (line.startsWith("currentTheme=")) {
                    currentTheme = line.substring("currentTheme=".length()).trim();
                } else if (line.startsWith("language=")) {
                    language = line.substring("language=".length()).trim();
                } else if (line.startsWith("frontMatterExpandedByDefault=")) {
                    frontMatterExpandedByDefault = Boolean
                            .parseBoolean(line.substring("frontMatterExpandedByDefault=".length()).trim());
                } else if (line.startsWith("useLocalPlantUml=")) {
                    useLocalPlantUml = Boolean.parseBoolean(line.substring("useLocalPlantUml=".length()).trim());
                } else if (line.startsWith("plantUmlJarPath=")) {
                    plantUmlJarPath = line.substring("plantUmlJarPath=".length()).trim();
                } else if (line.startsWith("reattachDiagramOnTabClose=")) {
                    reattachDiagramOnTabClose = Boolean
                            .parseBoolean(line.substring("reattachDiagramOnTabClose=".length()).trim());
                } else if (line.startsWith("gitSshKeyPath=")) {
                    gitSshKeyPath = line.substring("gitSshKeyPath=".length()).trim();
                } else if (line.startsWith("gitToken=")) {
                    gitToken = line.substring("gitToken=".length()).trim();
                } else if (line.startsWith("gitUsername=")) {
                    gitUsername = line.substring("gitUsername=".length()).trim();
                } else if (line.startsWith("gitToolbarMode=")) {
                    gitToolbarMode = line.substring("gitToolbarMode=".length()).trim();

                } else if (line.startsWith("windowX=")) {
                    try {
                        windowX = Double.parseDouble(line.substring("windowX=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("windowY=")) {
                    try {
                        windowY = Double.parseDouble(line.substring("windowY=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("windowWidth=")) {
                    try {
                        windowWidth = Double.parseDouble(line.substring("windowWidth=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("windowHeight=")) {
                    try {
                        windowHeight = Double.parseDouble(line.substring("windowHeight=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("windowMaximized=")) {
                    windowMaximized = Boolean.parseBoolean(line.substring("windowMaximized=".length()).trim());
                } else if (line.startsWith("windowFullscreen=")) {
                    windowFullscreen = Boolean.parseBoolean(line.substring("windowFullscreen=".length()).trim());
                } else if (line.startsWith("restoreWorkspaceOnStart=")) {
                    restoreWorkspaceOnStart = Boolean
                            .parseBoolean(line.substring("restoreWorkspaceOnStart=".length()).trim());

                } else if (line.startsWith("autoCheckUpdate=")) {
                    autoCheckUpdate = Boolean.parseBoolean(line.substring("autoCheckUpdate=".length()).trim());
                } else if (line.startsWith("skipVersion=")) {
                    String v = line.substring("skipVersion=".length()).trim();
                    skipVersion = v.isEmpty() ? null : v;

                } else if (line.startsWith("panelState=")) {
                    String raw = line.substring("panelState=".length()).trim();
                    String[] parts = raw.split("\\|", -1);
                    if (parts.length >= 4 && !parts[0].isBlank()) {
                        panelStates.put(parts[0], new PanelState(Boolean.parseBoolean(parts[1]),
                                Boolean.parseBoolean(parts[2]), parts[3].trim()));
                    }
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
            // S'assurer que le répertoire existe
            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            List<String> lines = new ArrayList<>();
            lines.add("maxRecentItems=" + maxRecentItems);
            lines.add("openDocOnStart=" + openDocOnStart);
            lines.add("reopenLastProject=" + reopenLastProject);
            lines.add("showWelcomePage=" + showWelcomePage);
            lines.add("showSplashScreen=" + showSplashScreen);
            lines.add("currentTheme=" + currentTheme);
            lines.add("language=" + language);
            lines.add("frontMatterExpandedByDefault=" + frontMatterExpandedByDefault);
            lines.add("useLocalPlantUml=" + useLocalPlantUml);
            lines.add("plantUmlJarPath=" + plantUmlJarPath);
            lines.add("reattachDiagramOnTabClose=" + reattachDiagramOnTabClose);
            lines.add("gitSshKeyPath=" + gitSshKeyPath);
            lines.add("gitToken=" + gitToken);
            lines.add("gitUsername=" + gitUsername);
            lines.add("gitToolbarMode=" + gitToolbarMode);
            lines.add("windowX=" + windowX);
            lines.add("windowY=" + windowY);
            lines.add("windowWidth=" + windowWidth);
            lines.add("windowHeight=" + windowHeight);
            lines.add("windowMaximized=" + windowMaximized);
            lines.add("windowFullscreen=" + windowFullscreen);
            lines.add("restoreWorkspaceOnStart=" + restoreWorkspaceOnStart);
            lines.add("autoCheckUpdate=" + autoCheckUpdate);
            lines.add("skipVersion=" + (skipVersion != null ? skipVersion : ""));
            for (Map.Entry<String, PanelState> entry : panelStates.entrySet()) {
                PanelState state = entry.getValue();
                lines.add("panelState=" + entry.getKey() + "|" + state.visible() + "|" + state.docked() + "|"
                        + state.zone());
            }
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

    public boolean isShowSplashScreen() {
        return showSplashScreen;
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    public boolean isFrontMatterExpandedByDefault() {
        return frontMatterExpandedByDefault;
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

    public void setShowSplashScreen(boolean showSplashScreen) {
        this.showSplashScreen = showSplashScreen;
    }

    public void setCurrentTheme(String currentTheme) {
        this.currentTheme = currentTheme;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setFrontMatterExpandedByDefault(boolean frontMatterExpandedByDefault) {
        this.frontMatterExpandedByDefault = frontMatterExpandedByDefault;
    }

    public boolean isUseLocalPlantUml() {
        return useLocalPlantUml;
    }

    public void setUseLocalPlantUml(boolean useLocalPlantUml) {
        this.useLocalPlantUml = useLocalPlantUml;
    }

    public String getPlantUmlJarPath() {
        return plantUmlJarPath;
    }

    public void setPlantUmlJarPath(String plantUmlJarPath) {
        this.plantUmlJarPath = plantUmlJarPath;
    }

    public boolean isReattachDiagramOnTabClose() {
        return reattachDiagramOnTabClose;
    }

    public void setReattachDiagramOnTabClose(boolean reattachDiagramOnTabClose) {
        this.reattachDiagramOnTabClose = reattachDiagramOnTabClose;
    }

    public String getGitSshKeyPath() {
        return gitSshKeyPath;
    }

    public void setGitSshKeyPath(String path) {
        this.gitSshKeyPath = path != null ? path : "";
    }

    public String getGitToken() {
        return gitToken;
    }

    public void setGitToken(String token) {
        this.gitToken = token != null ? token : "";
    }

    public String getGitUsername() {
        return gitUsername;
    }

    public void setGitUsername(String username) {
        this.gitUsername = username != null ? username : "token";
    }

    public String getGitToolbarMode() {
        return gitToolbarMode;
    }

    public void setGitToolbarMode(String mode) {
        this.gitToolbarMode = (mode != null && !mode.isBlank()) ? mode : "standard";
    }

    public boolean isAutoCheckUpdate() {
        return autoCheckUpdate;
    }

    public void setAutoCheckUpdate(boolean autoCheckUpdate) {
        this.autoCheckUpdate = autoCheckUpdate;
    }

    public String getSkipVersion() {
        return skipVersion;
    }

    public void setSkipVersion(String version) {
        this.skipVersion = version;
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

    public PanelState getPanelState(String panelId) {
        return panelStates.get(panelId);
    }

    public void setPanelState(String panelId, PanelState state) {
        if (panelId == null || panelId.isBlank() || state == null) {
            return;
        }
        panelStates.put(panelId, state);
    }

    public boolean hasPanelState(String panelId) {
        return panelStates.containsKey(panelId);
    }

    public boolean hasAnyPanelStates() {
        return !panelStates.isEmpty();
    }

    public double getWindowX() {
        return windowX;
    }

    public void setWindowX(double v) {
        this.windowX = v;
    }

    public double getWindowY() {
        return windowY;
    }

    public void setWindowY(double v) {
        this.windowY = v;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double v) {
        this.windowWidth = v;
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double v) {
        this.windowHeight = v;
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean v) {
        this.windowMaximized = v;
    }

    public boolean isWindowFullscreen() {
        return windowFullscreen;
    }

    public void setWindowFullscreen(boolean v) {
        this.windowFullscreen = v;
    }

    public boolean isRestoreWorkspaceOnStart() {
        return restoreWorkspaceOnStart;
    }

    public void setRestoreWorkspaceOnStart(boolean v) {
        this.restoreWorkspaceOnStart = v;
    }
}
