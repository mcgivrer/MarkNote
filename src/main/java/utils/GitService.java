package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;

/**
 * Service gérant les opérations git pour le projet courant (V1).
 * <p>
 * Détecte automatiquement si le répertoire de projet contient un sous-dossier
 * {@code .git/}. Si c'est le cas, il analyse le statut de chaque fichier via
 * {@code git status --porcelain} et expose l'opération de synchronisation.
 * <p>
 * Synchronisation : commit local (si modifiés) → pull --rebase → push.
 * <p>
 * Authentification supportée (V1) :
 * <ul>
 *   <li>SSH avec clé sans passphrase ({@code GIT_SSH_COMMAND}).</li>
 *   <li>Token personnel GitHub/GitLab via {@code GIT_ASKPASS} (script shell temporaire).</li>
 * </ul>
 */
public class GitService {

    // -------------------------------------------------------------------------
    // Statut git d'un fichier
    // -------------------------------------------------------------------------

    public enum GitStatus {
        /** Fichier suivi par git et non modifié. */
        CLEAN,
        /** Fichier suivi et modifié (ou supprimé) dans le working tree ou l'index. */
        MODIFIED,
        /** Fichier ajouté à l'index (staged) mais pas encore commité. */
        STAGED,
        /** Fichier non suivi (untracked) par git. */
        UNTRACKED
    }

    // -------------------------------------------------------------------------
    // État
    // -------------------------------------------------------------------------

    private File projectDir;
    private volatile Map<String, GitStatus> statusMap = new HashMap<>();
    private volatile boolean isGitRepo = false;

    // Credentials (V1)
    private String sshKeyPath  = "";
    private String gitToken    = "";
    private String gitUsername = "token";  // default for GitHub/GitLab token auth

    // Callbacks
    private Runnable         onStatusUpdated;
    private Consumer<String> onOperationResult;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Définit le répertoire du projet.
     * Détecte automatiquement si c'est un dépôt git et lance un refresh asynchrone.
     */
    public void setProject(File dir) {
        this.projectDir = dir;
        this.isGitRepo  = dir != null && new File(dir, ".git").isDirectory();
        statusMap       = new HashMap<>();
        if (isGitRepo) {
            refreshStatusAsync();
        } else if (onStatusUpdated != null) {
            Platform.runLater(onStatusUpdated);
        }
    }

    /** @return {@code true} si le projet courant est un dépôt git. */
    public boolean isGitRepo() {
        return isGitRepo;
    }

    /**
     * Retourne le statut git d'un fichier ou {@code null} si git n'est pas actif.
     * Les fichiers suivis et non modifiés (CLEAN) qui n'apparaissent pas dans
     * {@code git status --porcelain} sont signalés comme {@link GitStatus#CLEAN}.
     *
     * @param file Fichier absolu appartenant au projet.
     */
    public GitStatus getStatus(File file) {
        if (!isGitRepo || projectDir == null) return null;
        try {
            String relative = projectDir.toPath().relativize(file.toPath()).toString();
            return statusMap.getOrDefault(relative, GitStatus.CLEAN);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Rafraîchit le statut git en arrière-plan, puis appelle
     * {@code onStatusUpdated} sur le thread JavaFX.
     */
    public void refreshStatusAsync() {
        Thread t = new Thread(() -> {
            refreshStatus();
            if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
        }, "git-status-refresh");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Synchronise le dépôt local avec le distant en trois étapes :
     * <ol>
     *   <li>Si des fichiers sont modifiés/non suivis : {@code git add -A} puis
     *       {@code git commit} avec un message auto-généré (date, machine, liste
     *       des fichiers affectés).</li>
     *   <li>{@code git pull --rebase} pour intégrer les modifications distantes.</li>
     *   <li>{@code git push} pour envoyer les commits locaux.</li>
     * </ol>
     * Le résultat complet (sorties des commandes ou message d'erreur) est
     * transmis via {@code onOperationResult} sur le thread JavaFX.
     */
    public void syncAsync() {
        Thread t = new Thread(() -> {
            StringBuilder log = new StringBuilder();
            try {
                // 1. Recenser les fichiers modifiés avant staging
                refreshStatus();
                List<String> changedFiles = statusMap.entrySet().stream()
                        .filter(e -> e.getValue() != GitStatus.CLEAN)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .collect(Collectors.toList());

                // 2. Commit si des changements existent
                if (!changedFiles.isEmpty()) {
                    String message = buildCommitMessage(changedFiles);
                    runGit("add", "-A");
                    List<String> commitOut = runGit("commit", "-m", message);
                    log.append("Committed:\n");
                    changedFiles.forEach(f -> log.append("  ").append(f).append("\n"));
                    log.append("\n");
                    commitOut.forEach(l -> log.append(l).append("\n"));
                    log.append("\n");
                }

                // 3. Pull (rebase pour conserver un historique propre)
                try {
                    List<String> pullOut = runGitWithAuth("pull", "--rebase");
                    pullOut.forEach(l -> log.append(l).append("\n"));
                    if (!pullOut.isEmpty()) log.append("\n");
                } catch (GitException e) {
                    log.append("Pull error:\n").append(e.getMessage()).append("\n\n");
                }

                // 4. Push
                List<String> pushOut = runGitWithAuth("push");
                pushOut.forEach(l -> log.append(l).append("\n"));

                refreshStatus();
                String result = log.toString().strip();
                if (onStatusUpdated   != null) Platform.runLater(onStatusUpdated);
                if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(result));

            } catch (GitException e) {
                refreshStatus();
                if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
                String partial = log.toString().strip();
                String errMsg  = (partial.isBlank() ? "" : partial + "\n\n") + "Error:\n" + e.getMessage();
                if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(errMsg));
            }
        }, "git-sync");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Construit le message de commit automatique.
     * Format : {@code [MarkNote sync] yyyy-MM-dd HH:mm:ss @ hostname}
     * suivi de la liste des fichiers affectés.
     */
    private String buildCommitMessage(List<String> changedFiles) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        String fileList = changedFiles.stream()
                .map(f -> "  - " + f)
                .collect(Collectors.joining("\n"));
        return "[MarkNote sync] " + date + " @ " + host + "\n\nModified:\n" + fileList;
    }

    // -------------------------------------------------------------------------
    // Setters credentials & callbacks
    // -------------------------------------------------------------------------

    public void setSshKeyPath(String path)     { this.sshKeyPath  = path     != null ? path     : ""; }
    public void setGitToken(String token)      { this.gitToken    = token    != null ? token    : ""; }
    public void setGitUsername(String username){ this.gitUsername = username != null ? username : "token"; }

    public void setOnStatusUpdated(Runnable callback)          { this.onStatusUpdated   = callback; }
    public void setOnOperationResult(Consumer<String> callback){ this.onOperationResult = callback; }

    // -------------------------------------------------------------------------
    // Implémentation interne
    // -------------------------------------------------------------------------

    private void refreshStatus() {
        if (!isGitRepo || projectDir == null) return;
        Map<String, GitStatus> newMap = new HashMap<>();
        try {
            List<String> lines = runGit("status", "--porcelain");
            for (String line : lines) {
                if (line.length() < 3) continue;
                char indexStatus = line.charAt(0);
                char workStatus  = line.charAt(1);
                String path      = line.substring(3).trim();
                // Rename notation: "old -> new"
                if (path.contains(" -> ")) {
                    path = path.substring(path.indexOf(" -> ") + 4);
                }
                if (indexStatus == '?' || workStatus == '?') {
                    newMap.put(path, GitStatus.UNTRACKED);
                } else if (indexStatus == 'A') {
                    newMap.put(path, GitStatus.STAGED);
                } else if (indexStatus != ' ' || workStatus == 'M' || workStatus == 'D') {
                    newMap.put(path, GitStatus.MODIFIED);
                }
            }
        } catch (Exception e) {
            // git not found or not a repo — keep empty map
        }
        statusMap = newMap;
    }

    /** Exécute git sans credentials supplémentaires. */
    private List<String> runGit(String... args) throws GitException {
        return runGitWithEnv(Map.of(), args);
    }

    /**
     * Exécute git avec les credentials configurés (SSH key ou token HTTPS).
     * <p>
     * Pour SSH : positionne {@code GIT_SSH_COMMAND} avec la clé privée.
     * Pour HTTPS token : écrit un script {@code GIT_ASKPASS} temporaire qui
     * répond aux demandes de username/password sans interaction utilisateur.
     */
    private List<String> runGitWithAuth(String... args) throws GitException {
        Map<String, String> env = new HashMap<>();

        // SSH (passphrase-less) — V1
        if (!sshKeyPath.isBlank()) {
            env.put("GIT_SSH_COMMAND",
                    "ssh -i \"" + sshKeyPath + "\" -o StrictHostKeyChecking=accept-new -o BatchMode=yes");
        }

        // HTTPS token via GIT_ASKPASS — V1
        File askpassScript = null;
        if (!gitToken.isBlank()) {
            try {
                askpassScript = File.createTempFile("marknote-askpass-", ".sh");
                askpassScript.deleteOnExit();
                String username = gitUsername.isBlank() ? "token" : gitUsername;
                String script =
                        "#!/bin/sh\n" +
                        "case \"$1\" in\n" +
                        "  *Username*) echo \"" + escapeSh(username)  + "\" ;;\n" +
                        "  *Password*) echo \"" + escapeSh(gitToken)  + "\" ;;\n" +
                        "  *)          echo \"\" ;;\n" +
                        "esac\n";
                Files.writeString(askpassScript.toPath(), script);
                Set<PosixFilePermission> perms = new HashSet<>(
                        Files.getPosixFilePermissions(askpassScript.toPath()));
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(askpassScript.toPath(), perms);
                env.put("GIT_ASKPASS", askpassScript.getAbsolutePath());
                env.put("GIT_TERMINAL_PROMPT", "0");
            } catch (IOException e) {
                // Proceed without askpass — git may prompt or fail
            }
        }

        try {
            return runGitWithEnv(env, args);
        } finally {
            if (askpassScript != null) askpassScript.delete();
        }
    }

    private List<String> runGitWithEnv(Map<String, String> extraEnv, String... args) throws GitException {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectDir);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.environment().putAll(extraEnv);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<String> lines;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                lines = reader.lines().collect(Collectors.toList());
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitException(String.join("\n", lines));
            }
            return lines;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /** Échappe les caractères dangereux pour une insertion dans une chaîne entre guillemets shell. */
    private static String escapeSh(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$");
    }

    // -------------------------------------------------------------------------
    // Exception interne
    // -------------------------------------------------------------------------

    public static class GitException extends Exception {
        public GitException(String message) { super(message); }
    }
}
