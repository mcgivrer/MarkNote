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
    private static final String SECTION_ACTIVE_FILE = "[active_file]";

    private final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "ProjectSessionService";

    /** État de session d'un projet : fichiers ouverts + onglet actif. */
    public record SessionData(List<File> openFiles, File activeFile) {}

    /**
     * Sauvegarde la session du projet (fichiers ouverts + onglet actif) dans
     * {@code <projectDir>/.marknote}.
     *
     * @param projectDir répertoire racine du projet
     * @param openFiles  fichiers actuellement ouverts
     * @param activeFile fichier actif (onglet sélectionné), peut être null
     */
    public void saveSession(File projectDir, List<File> openFiles, File activeFile) {
        if (projectDir == null) return;

        Path sessionPath = projectDir.toPath().resolve(SESSION_FILE);
        List<String> lines = new ArrayList<>();
        lines.add(SECTION_OPEN_FILES);
        for (File f : openFiles) {
            try {
                String relative = projectDir.toPath().relativize(f.toPath()).toString();
                lines.add(relative);
            } catch (IllegalArgumentException e) {
                lines.add(f.getAbsolutePath());
            }
        }
        if (activeFile != null) {
            lines.add(SECTION_ACTIVE_FILE);
            try {
                lines.add(projectDir.toPath().relativize(activeFile.toPath()).toString());
            } catch (IllegalArgumentException e) {
                lines.add(activeFile.getAbsolutePath());
            }
        }

        try {
            Files.writeString(sessionPath, String.join("\n", lines) + "\n");
            log.info(LOG_SOURCE, "Session saved: " + openFiles.size() + " file(s), active=" +
                    (activeFile != null ? activeFile.getName() : "none"));
        } catch (IOException e) {
            log.error(LOG_SOURCE, "Failed to save session: " + e.getMessage());
        }
    }

    /**
     * Charge la session depuis {@code <projectDir>/.marknote}.
     *
     * @param projectDir répertoire racine du projet
     * @return données de session (jamais null)
     */
    public SessionData loadSession(File projectDir) {
        List<File> openFiles = new ArrayList<>();
        File activeFile = null;
        if (projectDir == null) return new SessionData(openFiles, null);

        Path sessionPath = projectDir.toPath().resolve(SESSION_FILE);
        if (!Files.exists(sessionPath)) return new SessionData(openFiles, null);

        try {
            List<String> lines = Files.readAllLines(sessionPath);
            String currentSection = null;
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("[")) {
                    currentSection = trimmed;
                    continue;
                }
                if (SECTION_OPEN_FILES.equals(currentSection)) {
                    File f = resolveFile(projectDir, trimmed);
                    if (f != null && f.exists() && f.isFile()) {
                        openFiles.add(f);
                    }
                } else if (SECTION_ACTIVE_FILE.equals(currentSection)) {
                    File f = resolveFile(projectDir, trimmed);
                    if (f != null && f.exists() && f.isFile()) {
                        activeFile = f;
                    }
                }
            }
            log.info(LOG_SOURCE, "Session loaded: " + openFiles.size() + " file(s), active=" +
                    (activeFile != null ? activeFile.getName() : "none"));
        } catch (IOException e) {
            log.error(LOG_SOURCE, "Failed to load session: " + e.getMessage());
        }
        return new SessionData(openFiles, activeFile);
    }

    private File resolveFile(File projectDir, String path) {
        File f = new File(path);
        if (f.isAbsolute()) return f;
        return projectDir.toPath().resolve(path).toFile();
    }
}
