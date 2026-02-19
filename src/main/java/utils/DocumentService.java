package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service centralisé pour les opérations sur les fichiers et répertoires.
 * Fournit des méthodes utilitaires pour la lecture, l'écriture, la création,
 * le renommage et la suppression de fichiers/répertoires.
 */
public final class DocumentService {

    private DocumentService() {
        // Classe utilitaire non instanciable
    }

    /**
     * Lit le contenu d'un fichier.
     *
     * @param file Le fichier à lire
     * @return Le contenu du fichier, ou Optional.empty() en cas d'erreur
     */
    public static Optional<String> readFile(File file) {
        try {
            return Optional.of(Files.readString(file.toPath()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Écrit du contenu dans un fichier.
     *
     * @param file    Le fichier cible
     * @param content Le contenu à écrire
     * @return true si l'écriture a réussi
     */
    public static boolean writeFile(File file, String content) {
        try {
            Files.writeString(file.toPath(), content);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Crée un nouveau fichier dans un répertoire.
     *
     * @param parentDir Le répertoire parent
     * @param name      Le nom du fichier
     * @return Le fichier créé, ou Optional.empty() en cas d'erreur
     */
    public static Optional<File> createFile(File parentDir, String name) {
        if (parentDir == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        File newFile = new File(parentDir, name);
        try {
            if (newFile.createNewFile()) {
                return Optional.of(newFile);
            }
        } catch (IOException e) {
            // Erreur de création
        }
        return Optional.empty();
    }

    /**
     * Crée un nouveau répertoire dans un répertoire parent.
     *
     * @param parentDir Le répertoire parent
     * @param name      Le nom du répertoire
     * @return Le répertoire créé, ou Optional.empty() en cas d'erreur
     */
    public static Optional<File> createDirectory(File parentDir, String name) {
        if (parentDir == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        File newDir = new File(parentDir, name);
        if (newDir.mkdir()) {
            return Optional.of(newDir);
        }
        return Optional.empty();
    }

    /**
     * Renomme un fichier ou répertoire.
     *
     * @param file    Le fichier/répertoire à renommer
     * @param newName Le nouveau nom
     * @return Le fichier renommé, ou Optional.empty() en cas d'erreur
     */
    public static Optional<File> rename(File file, String newName) {
        if (file == null || newName == null || newName.isBlank()) {
            return Optional.empty();
        }
        File newFile = new File(file.getParentFile(), newName);
        if (file.renameTo(newFile)) {
            return Optional.of(newFile);
        }
        return Optional.empty();
    }

    /**
     * Supprime un fichier ou répertoire (récursivement pour les répertoires).
     *
     * @param file Le fichier/répertoire à supprimer
     * @return true si la suppression a réussi
     */
    public static boolean delete(File file) {
        if (file == null) {
            return false;
        }
        try {
            deleteRecursively(file.toPath());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Supprime récursivement un fichier ou répertoire.
     *
     * @param path Le chemin à supprimer
     * @throws IOException En cas d'erreur de suppression
     */
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }

    /**
     * Compte le nombre d'éléments dans un répertoire (récursivement).
     *
     * @param dir Le répertoire
     * @return Le nombre d'éléments (fichiers et sous-répertoires)
     */
    public static long countFilesInDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(p -> !p.equals(dir.toPath())).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Vérifie si un fichier existe.
     *
     * @param file Le fichier à vérifier
     * @return true si le fichier existe
     */
    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    /**
     * Vérifie si un chemin est un répertoire.
     *
     * @param file Le fichier à vérifier
     * @return true si c'est un répertoire
     */
    public static boolean isDirectory(File file) {
        return file != null && file.isDirectory();
    }

    /**
     * Déplace un fichier ou répertoire vers un répertoire cible.
     *
     * @param file      Le fichier/répertoire à déplacer
     * @param targetDir Le répertoire de destination
     * @return Le fichier déplacé, ou Optional.empty() en cas d'erreur
     */
    public static Optional<File> move(File file, File targetDir) {
        if (file == null || targetDir == null || !targetDir.isDirectory()) {
            return Optional.empty();
        }
        // Ne pas déplacer un dossier dans lui-même ou un de ses sous-dossiers
        if (file.isDirectory() && targetDir.toPath().startsWith(file.toPath())) {
            return Optional.empty();
        }
        File destination = new File(targetDir, file.getName());
        try {
            Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(destination);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Déplace plusieurs fichiers/répertoires vers un répertoire cible.
     *
     * @param files     La liste des fichiers/répertoires à déplacer
     * @param targetDir Le répertoire de destination
     * @return Le nombre de fichiers déplacés avec succès
     */
    public static int moveAll(List<File> files, File targetDir) {
        if (files == null || targetDir == null || !targetDir.isDirectory()) {
            return 0;
        }
        int successCount = 0;
        for (File file : files) {
            if (move(file, targetDir).isPresent()) {
                successCount++;
            }
        }
        return successCount;
    }
}
