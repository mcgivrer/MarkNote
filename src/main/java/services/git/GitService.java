package services.git;

import utils.LogService;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.util.FS;

/**
 * Service gérant les opérations git pour le projet courant (V2 — JGit).
 * <p>
 * Utilise la bibliothèque JGit (pur Java) pour toutes les opérations.
 * Authentication supportée :
 * <ul>
 *   <li>SSH avec fichier de clé privée via Apache MINA SSHD ({@code jgit.ssh.apache}).</li>
 *   <li>HTTPS / Token via {@link UsernamePasswordCredentialsProvider}.</li>
 * </ul>
 */
public class GitService {

    private static final String LOG_SOURCE = "GitService";
    private final LogService log = LogService.getInstance();

    // -------------------------------------------------------------------------
    // Statut git d'un fichier
    // -------------------------------------------------------------------------

    public enum GitStatus {
        /** Fichier suivi et non modifié. */
        CLEAN,
        /** Fichier suivi et modifié (ou supprimé) dans le working tree. */
        MODIFIED,
        /** Fichier dans l'index (staged). */
        STAGED,
        /** Fichier non suivi (untracked). */
        UNTRACKED
    }

    // -------------------------------------------------------------------------
    // Record pour un fichier staged (utilisé par CommitDialog)
    // -------------------------------------------------------------------------

    /**
     * Représente un fichier présent dans l'index (staged).
     * {@code status} vaut 'A' (added), 'M' (modified) ou 'D' (deleted).
     */
    public record StagedFile(String path, char status) {
        @Override
        public String toString() {
            return status + " " + path;
        }
    }

    // -------------------------------------------------------------------------
    // État
    // -------------------------------------------------------------------------

    private File projectDir;
    private volatile Map<String, GitStatus> statusMap = new HashMap<>();
    private volatile boolean isGitRepo = false;
    private Git jgit;  // null quand pas de dépôt git ouvert

    // Credentials
    private String sshKeyPath  = "";
    private String gitToken    = "";
    private String gitUsername = "token";

    // Propriété JavaFX pour la branche courante (bindable depuis l'UI)
    private final StringProperty currentBranchProperty = new SimpleStringProperty("");

    // Callbacks
    private Runnable         onStatusUpdated;
    private Consumer<String> onOperationResult;

    // -------------------------------------------------------------------------
    // API publique — gestion du projet
    // -------------------------------------------------------------------------

    /**
     * Définit le répertoire du projet.
     * Détecte automatiquement si c'est un dépôt git et lance un refresh asynchrone.
     */
    public void setProject(File dir) {
        closeJGit();
        this.projectDir = dir;
        statusMap = new HashMap<>();
        if (dir != null && new File(dir, ".git").isDirectory()) {
            try {
                jgit = Git.open(dir);
                isGitRepo = true;
                refreshStatusAsync();
            } catch (IOException e) {
                log.error(LOG_SOURCE, "Cannot open git repo: " + e.getMessage());
                isGitRepo = false;
                updateBranchProperty("");
                if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
            }
        } else {
            isGitRepo = false;
            updateBranchProperty("");
            if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
        }
    }

    /** @return {@code true} si le projet courant est un dépôt git. */
    public boolean isGitRepo() {
        return isGitRepo;
    }

    /**
     * Retourne le statut git d'un fichier.
     * Les fichiers suivis non modifiés retournent {@link GitStatus#CLEAN}.
     */
    public GitStatus getStatus(File file) {
        if (!isGitRepo || projectDir == null) return null;
        try {
            String relative = projectDir.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');
            return statusMap.getOrDefault(relative, GitStatus.CLEAN);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Propriété JavaFX
    // -------------------------------------------------------------------------

    /** Propriété JavaFX bindable pour la branche courante. */
    public StringProperty currentBranchProperty() {
        return currentBranchProperty;
    }

    /** @return nom de la branche courante, ou chaîne vide. */
    public String currentBranch() {
        if (!isGitRepo || jgit == null) return "";
        try {
            String branch = jgit.getRepository().getBranch();
            return branch != null ? branch : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** @return {@code true} si au moins un remote est configuré. */
    public boolean hasRemote() {
        if (!isGitRepo || jgit == null) return false;
        try {
            return !jgit.remoteList().call().isEmpty();
        } catch (GitAPIException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // API — init & configure
    // -------------------------------------------------------------------------

    /**
     * Initialise un nouveau dépôt git dans {@code dir} (branche principale : main),
     * puis charge le projet via {@link #setProject(File)}.
     */
    public void init(File dir) throws GitAPIException, IOException {
        log.startOperation(LOG_SOURCE, "git init");
        try (Git newRepo = Git.init()
                .setDirectory(dir)
                .setInitialBranch("main")
                .call()) {
            // Repo créé ; on le referme immédiatement puis on rouvre via setProject
        }
        log.endOperation(LOG_SOURCE, "git init", "OK — " + dir.getName());
        setProject(dir);
    }

    /**
     * Ajoute un remote.
     *
     * @param name nom du remote (typiquement "origin")
     * @param url  URL du dépôt distant
     */
    public void addRemote(String name, String url) throws GitAPIException, URISyntaxException {
        if (!isGitRepo || jgit == null) throw new IllegalStateException("Not a git repo");
        log.info(LOG_SOURCE, "git remote add " + name + " " + url);
        RemoteAddCommand cmd = jgit.remoteAdd();
        cmd.setName(name);
        cmd.setUri(new URIish(url));
        cmd.call();
        log.info(LOG_SOURCE, "Remote '" + name + "' added.");
    }

    /**
     * Récupère l'URL d'un remote.
     *
     * @param name nom du remote (typiquement "origin")
     * @return URL du remote, ou null si non trouvé
     */
    public String getRemoteUrl(String name) throws GitAPIException {
        if (!isGitRepo || jgit == null) return null;
        List<RemoteConfig> remotes = jgit.remoteList().call();
        for (RemoteConfig remote : remotes) {
            if (remote.getName().equals(name)) {
                if (!remote.getURIs().isEmpty()) {
                    return remote.getURIs().get(0).toString();
                }
            }
        }
        return null;
    }

    /**
     * Lit l'identité (user.name, user.email) depuis la config locale du dépôt,
     * ou à défaut depuis {@code ~/.gitconfig}.
     *
     * @return tableau {name, email}, toujours non-null mais potentiellement vide.
     */
    public String[] getLocalIdentity() {
        String name = "", email = "";
        if (isGitRepo && jgit != null) {
            StoredConfig local = jgit.getRepository().getConfig();
            name  = nvl(local.getString("user", null, "name"));
            email = nvl(local.getString("user", null, "email"));
        }
        // Fallback vers ~/.gitconfig
        if (name.isBlank() || email.isBlank()) {
            try {
                File globalConfigFile = new File(System.getProperty("user.home"), ".gitconfig");
                if (globalConfigFile.exists()) {
                    FileBasedConfig global = new FileBasedConfig(globalConfigFile, FS.DETECTED);
                    global.load();
                    if (name.isBlank())  name  = nvl(global.getString("user", null, "name"));
                    if (email.isBlank()) email = nvl(global.getString("user", null, "email"));
                }
            } catch (Exception ignored) {
                // Pas bloquant
            }
        }
        return new String[]{name, email};
    }

    /**
     * Enregistre l'identité dans la config locale du dépôt, et optionnellement
     * dans {@code ~/.gitconfig}.
     */
    public void setLocalIdentity(String name, String email, boolean global) throws IOException {
        if (isGitRepo && jgit != null) {
            StoredConfig local = jgit.getRepository().getConfig();
            local.setString("user", null, "name",  name);
            local.setString("user", null, "email", email);
            local.save();
        }
        if (global) {
            File globalConfigFile = new File(System.getProperty("user.home"), ".gitconfig");
            FileBasedConfig globalConfig = new FileBasedConfig(globalConfigFile, FS.DETECTED);
            try { globalConfig.load(); } catch (Exception ignored) {}
            globalConfig.setString("user", null, "name",  name);
            globalConfig.setString("user", null, "email", email);
            globalConfig.save();
        }
    }

    // -------------------------------------------------------------------------
    // API — opérations asynchrones
    // -------------------------------------------------------------------------

    /**
     * Rafraîchit le statut git en arrière-plan, puis notifie {@code onStatusUpdated}
     * sur le thread JavaFX.
     */
    public void refreshStatusAsync() {
        final Git jgitLocal = this.jgit;
        Thread t = new Thread(() -> {
            refreshStatus(jgitLocal);
            updateBranchProperty(currentBranchFrom(jgitLocal));
            if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
        }, "git-status-refresh");
        t.setDaemon(true);
        t.start();
    }

    /** Stage un fichier individuel (git add <file>). */
    public void addAsync(File file) {
        final Git jgitLocal    = this.jgit;
        final File dirLocal    = this.projectDir;
        final boolean gitLocal = this.isGitRepo;
        runAsync("git-add", () -> {
            if (!gitLocal || jgitLocal == null) return;
            String relative = dirLocal.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');
            log.info(LOG_SOURCE, "git add " + relative);
            jgitLocal.add().addFilepattern(relative).call();
            log.debug(LOG_SOURCE, "Staged: " + relative);
            refreshStatus(jgitLocal);
        });
    }

    /**
     * Stage tous les changements (équivalent git add -A) de manière asynchrone.
     */
    public void addAllAsync() {
        final Git jgitLocal = this.jgit;
        runAsync("git-add-all", () -> {
            log.info(LOG_SOURCE, "git add -A");
            addAllInternal(jgitLocal);
            log.debug(LOG_SOURCE, "Stage all complete.");
        });
    }

    /**
     * Stage tous les changements de manière synchrone (appelé depuis CommitDialog
     * qui tourne déjà sur un thread daemon).
     */
    public void addAll() throws GitAPIException {
        addAllInternal(this.jgit);
    }

    /** Retire un fichier de l'index sans supprimer le fichier (git rm --cached). */
    public void removeFromIndexAsync(File file) {
        final Git jgitLocal    = this.jgit;
        final File dirLocal    = this.projectDir;
        final boolean gitLocal = this.isGitRepo;
        runAsync("git-rm-cached", () -> {
            if (!gitLocal || jgitLocal == null) return;
            String relative = dirLocal.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');
            log.info(LOG_SOURCE, "git rm --cached " + relative);
            jgitLocal.rm().addFilepattern(relative).setCached(true).call();
            log.debug(LOG_SOURCE, "Removed from index: " + relative);
            refreshStatus(jgitLocal);
        });
    }

    /** Crée un commit avec le message donné (asynchrone). */
    public void commitAsync(String message) {
        final Git jgitLocal    = this.jgit;
        final boolean gitLocal = this.isGitRepo;
        runAsync("git-commit", () -> {
            if (!gitLocal || jgitLocal == null) return;
            log.startOperation(LOG_SOURCE, "git commit");
            PersonIdent author = buildPersonIdent();
            jgitLocal.commit()
                    .setMessage(message)
                    .setAuthor(author)
                    .setCommitter(author)
                    .call();
            log.endOperation(LOG_SOURCE, "git commit", "OK — " + firstLine(message));
            refreshStatus(jgitLocal);
            updateBranchProperty(currentBranchFrom(jgitLocal));
        });
    }

    /** Crée un commit de manière synchrone. */
    public void commit(String message) throws GitAPIException {
        final Git jgitLocal = this.jgit;
        if (!isGitRepo || jgitLocal == null) throw new IllegalStateException("Not a git repo");
        log.startOperation(LOG_SOURCE, "git commit");
        PersonIdent author = buildPersonIdent();
        jgitLocal.commit()
                .setMessage(message)
                .setAuthor(author)
                .setCommitter(author)
                .call();
        log.endOperation(LOG_SOURCE, "git commit", "OK — " + firstLine(message));
        refreshStatus(jgitLocal);
        updateBranchProperty(currentBranchFrom(jgitLocal));
    }

    /** Récupère les changements distants sans fusionner (git fetch). */
    public void fetchAsync() {
        final Git jgitLocal    = this.jgit;
        final boolean gitLocal = this.isGitRepo;
        runAsync("git-fetch", () -> {
            if (!gitLocal || jgitLocal == null) return;
            log.startOperation(LOG_SOURCE, "git fetch");
            FetchResult result = jgitLocal.fetch()
                    .setCredentialsProvider(buildCredentials())
                    .call();
            refreshStatus(jgitLocal);
            String msg = nvl(result.getMessages()).strip();
            log.endOperation(LOG_SOURCE, "git fetch", msg.isBlank() ? "OK" : msg);
            if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(msg));
        });
    }

    /** Tire les changements distants en fast-forward uniquement (git pull --ff-only). */
    public void pullAsync() {
        final Git jgitLocal    = this.jgit;
        final boolean gitLocal = this.isGitRepo;
        runAsync("git-pull", () -> {
            if (!gitLocal || jgitLocal == null) return;
            log.startOperation(LOG_SOURCE, "git pull --ff-only");
            PullResult result = jgitLocal.pull()
                    .setFastForward(FastForwardMode.FF_ONLY)
                    .setCredentialsProvider(buildCredentials())
                    .call();
            refreshStatus(jgitLocal);
            updateBranchProperty(currentBranchFrom(jgitLocal));
            String msg = result.isSuccessful() ? "" : "Pull failed: " + result;
            log.endOperation(LOG_SOURCE, "git pull --ff-only", result.isSuccessful() ? "OK" : "FAILED — " + result);
            if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(msg));
        });
    }

    /** Pousse les commits locaux vers le remote (git push). */
    public void pushAsync() {
        final Git jgitLocal    = this.jgit;
        final boolean gitLocal = this.isGitRepo;
        runAsync("git-push", () -> {
            if (!gitLocal || jgitLocal == null) return;
            log.startOperation(LOG_SOURCE, "git push");
            StringBuilder sb = new StringBuilder();
            Iterable<PushResult> results = jgitLocal.push()
                    .setCredentialsProvider(buildCredentials())
                    .call();
            for (PushResult pr : results) {
                for (RemoteRefUpdate rru : pr.getRemoteUpdates()) {
                    RemoteRefUpdate.Status s = rru.getStatus();
                    if (s != RemoteRefUpdate.Status.OK && s != RemoteRefUpdate.Status.UP_TO_DATE) {
                        String detail = "Error: " + s + " — " + nvl(rru.getMessage());
                        log.warn(LOG_SOURCE, detail);
                        sb.append(detail).append("\n");
                    } else {
                        log.debug(LOG_SOURCE, "Push OK: " + rru.getRemoteName() + " [" + s + "]");
                    }
                }
            }
            refreshStatus(jgitLocal);
            String msg = sb.toString().strip();
            log.endOperation(LOG_SOURCE, "git push", msg.isBlank() ? "OK" : "FAILED");
            if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(msg));
        });
    }

    /**
     * Synchronisation complète : commit automatique + pull --ff-only + push.
     * Conserve la sémantique du bouton "Sync" de la V1.
     */
    public void syncAsync() {
        final Git jgitLocal    = this.jgit;
        final boolean gitLocal = this.isGitRepo;
        Thread t = new Thread(() -> {
            StringBuilder logBuilder = new StringBuilder();
            log.startOperation(LOG_SOURCE, "Git Sync");
            try {
                if (!gitLocal || jgitLocal == null) throw new GitException("Not a git repository");

                refreshStatus(jgitLocal);
                List<String> changedFiles = statusMap.entrySet().stream()
                        .filter(e -> e.getValue() != GitStatus.CLEAN)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .collect(Collectors.toList());

                if (!changedFiles.isEmpty()) {
                    String message = buildCommitMessage(changedFiles);
                    addAllInternal(jgitLocal);
                    PersonIdent author = buildPersonIdent();
                    jgitLocal.commit()
                            .setMessage(message)
                            .setAuthor(author)
                            .setCommitter(author)
                            .call();
                    logBuilder.append("Committed:\n");
                    changedFiles.forEach(f -> logBuilder.append("  ").append(f).append("\n"));
                    logBuilder.append("\n");
                }

                CredentialsProvider creds = buildCredentials();

                // Pull (ff-only)
                try {
                    PullResult pullResult = jgitLocal.pull()
                            .setFastForward(FastForwardMode.FF_ONLY)
                            .setCredentialsProvider(creds)
                            .call();
                    if (!pullResult.isSuccessful()) {
                        logBuilder.append("Pull warning: ").append(pullResult).append("\n\n");
                    }
                } catch (GitAPIException e) {
                    logBuilder.append("Pull error: ").append(e.getMessage()).append("\n\n");
                }

                // Push
                Iterable<PushResult> pushResults = jgitLocal.push()
                        .setCredentialsProvider(creds)
                        .call();
                for (PushResult pr : pushResults) {
                    for (RemoteRefUpdate rru : pr.getRemoteUpdates()) {
                        RemoteRefUpdate.Status s = rru.getStatus();
                        if (s != RemoteRefUpdate.Status.OK && s != RemoteRefUpdate.Status.UP_TO_DATE) {
                            logBuilder.append("Push error: ").append(s)
                                      .append(" — ").append(nvl(rru.getMessage())).append("\n");
                        }
                    }
                }

                refreshStatus(jgitLocal);
                updateBranchProperty(currentBranchFrom(jgitLocal));
                String result = logBuilder.toString().strip();
                log.endOperation(LOG_SOURCE, "Git Sync", "SUCCESS");
                if (onStatusUpdated   != null) Platform.runLater(onStatusUpdated);
                if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(result));

            } catch (Exception e) {
                log.error(LOG_SOURCE, "Git sync failed: " + e.getMessage());
                refreshStatus(jgitLocal);
                if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
                String partial = logBuilder.toString().strip();
                String errMsg  = (partial.isBlank() ? "" : partial + "\n\n") + "Error:\n" + e.getMessage();
                log.endOperation(LOG_SOURCE, "Git Sync", "FAILED");
                if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(errMsg));
            }
        }, "git-sync");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Retourne la liste des fichiers actuellement dans l'index (staged),
     * utilisée pour alimenter CommitDialog.
     */
    public List<StagedFile> listStagedFiles() {
        if (!isGitRepo || jgit == null) return List.of();
        try {
            Status status = jgit.status().call();
            List<StagedFile> result = new ArrayList<>();
            status.getAdded().forEach(p   -> result.add(new StagedFile(p, 'A')));
            status.getChanged().forEach(p -> result.add(new StagedFile(p, 'M')));
            status.getRemoved().forEach(p -> result.add(new StagedFile(p, 'D')));
            result.sort(Comparator.comparing(StagedFile::path));
            return result;
        } catch (GitAPIException e) {
            return List.of();
        }
    }

    /**
     * Teste la connectivité à une URL de remote (ls-remote).
     *
     * @return chaîne vide si succès, message d'erreur sinon.
     */
    public String testRemoteConnection(String url) {
        log.info(LOG_SOURCE, "Testing remote connection: " + url);
        try {
            Git.lsRemoteRepository()
                    .setRemote(url)
                    .setCredentialsProvider(buildCredentials())
                    .call();
            log.info(LOG_SOURCE, "Remote connection OK: " + url);
            return "";
        } catch (GitAPIException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn(LOG_SOURCE, "Remote connection FAILED: " + msg);
            return msg;
        }
    }

    // -------------------------------------------------------------------------
    // Setters credentials & callbacks
    // -------------------------------------------------------------------------

    public void setSshKeyPath(String path) {
        this.sshKeyPath = path != null ? path : "";
        applySSHFactory();
    }

    public void setGitToken(String token)      { this.gitToken    = token    != null ? token    : ""; }
    public void setGitUsername(String username){ this.gitUsername = username != null ? username : "token"; }

    public void setOnStatusUpdated(Runnable callback)          { this.onStatusUpdated   = callback; }
    public void setOnOperationResult(Consumer<String> callback){ this.onOperationResult = callback; }

    // -------------------------------------------------------------------------
    // Implémentation interne
    // -------------------------------------------------------------------------

    private void addAllInternal(Git git) throws GitAPIException {
        if (git == null) return;
        // Stage new files + modifications
        git.add().addFilepattern(".").call();
        // Stage deletions (fichiers disparus du working tree)
        Status s = git.status().call();
        if (!s.getMissing().isEmpty()) {
            RmCommand rm = git.rm();
            s.getMissing().forEach(rm::addFilepattern);
            rm.call();
        }
        refreshStatus(git);
    }

    private void refreshStatus(Git git) {
        if (git == null) return;
        Map<String, GitStatus> newMap = new HashMap<>();
        try {
            Status status = git.status().call();
            status.getAdded().forEach(p       -> newMap.put(p, GitStatus.STAGED));
            status.getChanged().forEach(p     -> newMap.put(p, GitStatus.STAGED));
            status.getRemoved().forEach(p     -> newMap.put(p, GitStatus.STAGED));
            status.getModified().forEach(p    -> newMap.put(p, GitStatus.MODIFIED));
            status.getMissing().forEach(p     -> newMap.put(p, GitStatus.MODIFIED));
            status.getConflicting().forEach(p -> newMap.put(p, GitStatus.MODIFIED));
            status.getUntracked().forEach(p   -> newMap.put(p, GitStatus.UNTRACKED));
        } catch (Exception e) {
            log.debug(LOG_SOURCE, "Status check failed: " + e.getMessage());
        }
        statusMap = newMap;
    }

    private static String currentBranchFrom(Git git) {
        if (git == null) return "";
        try {
            String branch = git.getRepository().getBranch();
            return branch != null ? branch : "";
        } catch (IOException e) {
            return "";
        }
    }

    private CredentialsProvider buildCredentials() {
        if (!gitToken.isBlank()) {
            String user = gitUsername.isBlank() ? "token" : gitUsername;
            return new UsernamePasswordCredentialsProvider(user, gitToken);
        }
        return null;
    }

    /**
     * Configure la session SSH factory globale JGit avec le chemin de clé privée.
     * Appelé lors du changement de {@code sshKeyPath}.
     */
    private void applySSHFactory() {
        if (sshKeyPath.isBlank()) return;
        String expanded = sshKeyPath.replace("~", System.getProperty("user.home"));
        Path keyFile = Path.of(expanded);
        if (!Files.exists(keyFile)) {
            log.debug(LOG_SOURCE, "SSH key file not found: " + keyFile);
            return;
        }
        try {
            SshdSessionFactory factory = new SshdSessionFactoryBuilder()
                    .setPreferredAuthentications("publickey")
                    .setDefaultIdentities(sshDir -> List.of(keyFile))
                    .build(new JGitKeyCache());
            SshSessionFactory.setInstance(factory);
        } catch (Exception e) {
            log.error(LOG_SOURCE, "SSH factory setup failed: " + e.getMessage());
        }
    }

    private PersonIdent buildPersonIdent() {
        String[] identity = getLocalIdentity();
        String name  = identity[0].isBlank() ? "MarkNote" : identity[0];
        String email = identity[1].isBlank() ? "marknote@local" : identity[1];
        return new PersonIdent(name, email);
    }

    private static String firstLine(String s) {
        if (s == null || s.isBlank()) return "(no message)";
        String first = s.strip().lines().findFirst().orElse("");
        return first.length() > 72 ? first.substring(0, 72) + "…" : first;
    }

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

    private void updateBranchProperty(String branch) {
        Platform.runLater(() -> currentBranchProperty.set(branch));
    }

    /**
     * Lance une opération git dans un thread daemon.
     * En cas d'erreur, notifie {@code onOperationResult} avec "Error: …".
     */
    private void runAsync(String threadName, GitOperation op) {
        final Git jgitLocal = this.jgit;
        Thread t = new Thread(() -> {
            try {
                op.run();
                if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
            } catch (Exception e) {
                log.error(LOG_SOURCE, threadName + " failed: " + e.getMessage());
                refreshStatus(jgitLocal);
                if (onStatusUpdated != null) Platform.runLater(onStatusUpdated);
                String msg = "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                if (onOperationResult != null) Platform.runLater(() -> onOperationResult.accept(msg));
            }
        }, threadName);
        t.setDaemon(true);
        t.start();
    }

    @FunctionalInterface
    private interface GitOperation {
        void run() throws Exception;
    }

    private void closeJGit() {
        if (jgit != null) {
            jgit.close();
            jgit = null;
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    // -------------------------------------------------------------------------
    // Exception interne (maintenue pour compatibilité)
    // -------------------------------------------------------------------------

    public static class GitException extends Exception {
        public GitException(String message) {
            super(message);
        }
    }
}
