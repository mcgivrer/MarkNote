package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gère la persistance de la session d'un projet dans un fichier {@code .marknote}
 * situé à la racine du répertoire de projet.
 * <p>
 * Le fichier stocke les chemins absolus des documents ouverts, un par ligne,
 * sous la section {@code [open_files]}.
 */
public class ProjectSessionService {

    private static final String SESSION_FILE = ".marknote";
    private static final String SECTION_OPEN_FILES = "[open_files]";

    private final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "ProjectSessionService";

    /**
     * Sauvegarde la liste des fichiers ouverts dans {@code <projectDir>/.marknote}.
     *
     * @param projectDir répertoire racine du projet
     * @param openFiles  chemins absolus des fichiers actuellement ouverts
     */
    public void saveSession(File projectDir, List<File> openFiles) {
        if (projectDir == null) return;

        Path sessionPath = projectDir.toPath().resolve(SESSION_FILE);
        List<String> lines = new ArrayList<>();
        lines.add(SECTION_OPEN_FILES);
        for (File f : openFiles) {
            // Stocker le chemin relatif au répertoire projet pour la portabilité
            try {
                String relative = projectDir.toPath().relativize(f.toPath()).toString();
                lines.add(relative);
            } catch (IllegalArgumentException e) {
                // Fichier hors du projet : chemin absolu en fallback
                lines.add(f.getAbsolutePath());
            }
        }

        try {
            Files.writeString(sessionPath, String.join("\n", lines) + "\n");
            log.info(LOG_SOURCE, "Session saved: " + openFiles.size() + " file(s)");
        } catch (IOException e) {
            log.error(LOG_SOURCE, "Failed to save session: " + e.getMessage());
        }
    }

    /**
     * Charge la liste des fichiers à rouvrir depuis {@code <projectDir>/.marknote}.
     * Seuls les fichiers qui existent réellement sur le disque sont retournés.
     *
     * @param projectDir répertoire racine du projet
     * @return liste des fichiers à rouvrir (jamais null)
     */
    public List<File> loadSession(File projectDir) {
        List<File> result = new ArrayList<>();
        if (projectDir == null) return result;

        Path sessionPath = projectDir.toPath().resolve(SESSION_FILE);
        if (!Files.exists(sessionPath)) return result;

        try {
            List<String> lines = Files.readAllLines(sessionPath);
            boolean inSection = false;
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) continue;
                if (SECTION_OPEN_FILES.equals(trimmed)) {
                    inSection = true;
                    continue;
                }
                if (trimmed.startsWith("[")) {
                    inSection = false;
                    continue;
                }
                if (inSection) {
                    File f = resolveFile(projectDir, trimmed);
                    if (f != null && f.exists() && f.isFile()) {
                        result.add(f);
                    }
                }
            }
            log.info(LOG_SOURCE, "Session loaded: " + result.size() + " file(s) to reopen");
        } catch (IOException e) {
            log.error(LOG_SOURCE, "Failed to load session: " + e.getMessage());
        }
        return result;
    }

    private File resolveFile(File projectDir, String path) {
        File f = new File(path);
        if (f.isAbsolute()) return f;
        return projectDir.toPath().resolve(path).toFile();
    }
}
