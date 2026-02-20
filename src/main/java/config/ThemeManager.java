package config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestionnaire de thèmes pour l'application.
 * Gère les thèmes intégrés et les thèmes personnalisés.
 */
public class ThemeManager {

    private static final String THEMES_DIR = System.getProperty("user.home") + File.separator + ".marknote" + File.separator + "themes";
    private static final String BUILTIN_THEMES_PATH = "/css/themes/";
    
    // Thèmes intégrés
    private static final String[] BUILTIN_THEMES = {
        "light",
        "dark", 
        "solarized-light",
        "solarized-dark",
        "high-contrast"
    };

    private static ThemeManager instance;
    private String currentTheme = "light";

    private ThemeManager() {
        ensureThemesDirExists();
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Assure que le répertoire des thèmes personnalisés existe.
     */
    private void ensureThemesDirExists() {
        File themesDir = new File(THEMES_DIR);
        if (!themesDir.exists()) {
            themesDir.mkdirs();
        }
    }

    /**
     * Retourne la liste de tous les thèmes disponibles (intégrés + personnalisés).
     */
    public List<String> getAvailableThemes() {
        List<String> themes = new ArrayList<>();
        
        // Ajouter les thèmes intégrés
        for (String theme : BUILTIN_THEMES) {
            themes.add(theme);
        }
        
        // Ajouter les thèmes personnalisés
        File themesDir = new File(THEMES_DIR);
        if (themesDir.exists() && themesDir.isDirectory()) {
            File[] customThemes = themesDir.listFiles((dir, name) -> name.endsWith(".css"));
            if (customThemes != null) {
                for (File file : customThemes) {
                    String themeName = file.getName().replace(".css", "");
                    if (!themes.contains(themeName)) {
                        themes.add(themeName);
                    }
                }
            }
        }
        
        return themes;
    }

    /**
     * Vérifie si un thème est intégré (non personnalisé).
     */
    public boolean isBuiltinTheme(String themeName) {
        for (String builtin : BUILTIN_THEMES) {
            if (builtin.equals(themeName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retourne le chemin CSS du thème spécifié.
     * Pour les thèmes intégrés, retourne une URL de ressource.
     * Pour les thèmes personnalisés, retourne une URL de fichier.
     */
    public String getThemeCssUrl(String themeName) {
        if (isBuiltinTheme(themeName)) {
            var resource = getClass().getResource(BUILTIN_THEMES_PATH + themeName + ".css");
            return resource != null ? resource.toExternalForm() : null;
        } else {
            File customTheme = new File(THEMES_DIR, themeName + ".css");
            if (customTheme.exists()) {
                return customTheme.toURI().toString();
            }
        }
        return null;
    }

    /**
     * Crée un nouveau thème personnalisé basé sur un thème existant.
     * 
     * @param basedOn Le nom du thème de base
     * @param newName Le nom du nouveau thème
     * @return Le fichier du nouveau thème, ou null en cas d'erreur
     */
    public File createCustomTheme(String basedOn, String newName) {
        ensureThemesDirExists();
        
        File newThemeFile = new File(THEMES_DIR, newName + ".css");
        
        try {
            String content;
            if (isBuiltinTheme(basedOn)) {
                // Copier depuis les ressources intégrées
                try (InputStream is = getClass().getResourceAsStream(BUILTIN_THEMES_PATH + basedOn + ".css")) {
                    if (is == null) {
                        return null;
                    }
                    content = new String(is.readAllBytes());
                }
            } else {
                // Copier depuis un thème personnalisé existant
                File sourceTheme = new File(THEMES_DIR, basedOn + ".css");
                if (!sourceTheme.exists()) {
                    return null;
                }
                content = Files.readString(sourceTheme.toPath());
            }
            
            // Ajouter un commentaire en en-tête
            String header = "/*\n * Custom Theme: " + newName + "\n * Based on: " + basedOn + "\n */\n\n";
            Files.writeString(newThemeFile.toPath(), header + content);
            
            return newThemeFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Supprime un thème personnalisé.
     * Les thèmes intégrés ne peuvent pas être supprimés.
     */
    public boolean deleteCustomTheme(String themeName) {
        if (isBuiltinTheme(themeName)) {
            return false;
        }
        
        File themeFile = new File(THEMES_DIR, themeName + ".css");
        if (themeFile.exists()) {
            return themeFile.delete();
        }
        return false;
    }

    /**
     * Retourne le fichier d'un thème personnalisé pour édition.
     */
    public File getCustomThemeFile(String themeName) {
        if (isBuiltinTheme(themeName)) {
            return null;
        }
        File themeFile = new File(THEMES_DIR, themeName + ".css");
        return themeFile.exists() ? themeFile : null;
    }

    /**
     * Retourne le répertoire des thèmes personnalisés.
     */
    public File getThemesDirectory() {
        return new File(THEMES_DIR);
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(String theme) {
        this.currentTheme = theme;
    }
}
