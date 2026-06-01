package ui;

import java.util.Locale;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import config.AppConfig;
import services.git.GitService;
import services.git.RemoteConnector.RemoteRepo;
import services.git.RemoteConnectorFactory;
import ui.git.CreateRemoteRepositoryDialog;
import ui.git.RemoteRepositoryBrowserDialog;
import utils.LogService;

/**
 * Dialogue modal pour ajouter (ou modifier) le remote "origin" d'un dépôt git.
 * <p>
 * Fonctionnalités :
 * <ul>
 *   <li>Saisie de l'URL du remote.</li>
 *   <li>Choix du mode d'authentification : aucune, basic, token, SSH.</li>
 *   <li>Affichage dynamique des champs selon le mode d'auth.</li>
 *   <li>Bouton "Tester la connexion" (asynchrone).</li>
 * </ul>
 * Retourne {@code true} depuis {@link #showAndWait()} si l'utilisateur a sauvegardé.
 */
public class AddRemoteDialog {

    private static final String AUTH_NONE  = "none";
    private static final String AUTH_BASIC = "basic";
    private static final String AUTH_TOKEN = "token";
    private static final String AUTH_SSH   = "ssh";

    private static final String LOG_SOURCE = "AddRemoteDialog";

    private final Stage dialog;
    private final GitService gitService;
    private final AppConfig config;
    private final ResourceBundle messages;
    private final LogService log = LogService.getInstance();

    // Form fields
    private final TextField     urlField;
    private final Label         platformDetectionLabel;
    private final ComboBox<String> authCombo;
    private final TextField     usernameField;
    private final PasswordField passwordField;
    private final PasswordField tokenField;
    private final TextField     sshKeyPathField;
    private final PasswordField sshPassphraseField;

    // Auth rows (shown/hidden dynamically)
    private final HBox usernameRow;
    private final HBox passwordRow;
    private final HBox tokenRow;
    private final HBox sshKeyRow;
    private final HBox sshPassRow;

    private final Label testResultLabel;
    private final Button saveBtn;

    private boolean saved = false;

    /**
     * @param owner      fenêtre propriétaire
     * @param gitService service git courant
     * @param config     configuration de l'application (pour pré-remplir les credentials)
     */
    public AddRemoteDialog(Window owner, GitService gitService, AppConfig config) {
        this.gitService = gitService;
        this.config     = config;
        this.messages   = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());

        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle(messages.getString("git.add.remote.dialog.title"));
        dialog.setResizable(false);

        // --- URL ---
        urlField = new TextField();
        urlField.setPromptText("https://github.com/user/repo.git");
        urlField.setPrefWidth(340);
        
        // Auto-detect platform from URL
        platformDetectionLabel = new Label();
        platformDetectionLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        urlField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                String platform = RemoteConnectorFactory.detectPlatform(newVal);
                if (platform != null) {
                    platformDetectionLabel.setText("Detected: " + platform);
                } else {
                    platformDetectionLabel.setText("");
                }
            } else {
                platformDetectionLabel.setText("");
            }
        });

        // --- Auth combo ---
        authCombo = new ComboBox<>();
        authCombo.getItems().addAll(
                s("git.add.remote.auth.none"),
                s("git.add.remote.auth.basic"),
                s("git.add.remote.auth.token"),
                s("git.add.remote.auth.ssh"));
        authCombo.getSelectionModel().selectFirst();
        authCombo.setMaxWidth(Double.MAX_VALUE);

        // --- Auth fields ---
        usernameField     = new TextField(config.getGitUsername());
        passwordField     = new PasswordField();
        tokenField        = new PasswordField();
        tokenField.setText(config.getGitToken());
        sshKeyPathField   = new TextField(config.getGitSshKeyPath());
        sshPassphraseField = new PasswordField();

        Button browseBtn = new Button(s("options.git.browse"));
        browseBtn.setOnAction(e -> browseSSHKey());

        usernameRow = labeledRow(s("git.add.remote.username"), usernameField);
        passwordRow = labeledRow(s("git.add.remote.password"), passwordField);
        tokenRow    = labeledRow(s("git.add.remote.token"),    tokenField);
        sshKeyRow   = buildSSHKeyRow(browseBtn);
        sshPassRow  = labeledRow(s("git.add.remote.ssh.passphrase"), sshPassphraseField);

        // Listen to combo changes to show/hide rows and resize dialog accordingly
        authCombo.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            updateAuthRows(n.intValue());
            dialog.sizeToScene();
        });
        updateAuthRows(0);  // initial: none

        // --- Test connection ---
        Button testBtn = new Button(s("git.add.remote.test"));
        testResultLabel = new Label();
        testResultLabel.setWrapText(true);
        testResultLabel.setMaxWidth(340);
        testBtn.setOnAction(e -> handleTest());

        HBox testRow = new HBox(8, testBtn, testResultLabel);
        testRow.setAlignment(Pos.CENTER_LEFT);

        // --- Save / Cancel ---
        saveBtn = new Button(s("git.add.remote.save"));
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> handleSave());

        Button cancelBtn = new Button(safeKey("cancel", "Annuler"));
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonBar = new HBox(8, new Spacer(), cancelBtn, saveBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        // --- Layout ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        ColumnConstraints col0 = new ColumnConstraints(120);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        int row = 0;
        grid.add(new Label(s("git.add.remote.url")), 0, row);
        
        // URL field with Browse and Create buttons
        Button browseReposBtn = new Button("Browse…");
        browseReposBtn.setOnAction(e -> handleBrowseRepositories());
        browseReposBtn.disableProperty().bind(tokenField.textProperty().isEmpty());
        
        Button createRepoBtn = new Button("Create New…");
        createRepoBtn.setOnAction(e -> handleCreateRepository());
        createRepoBtn.disableProperty().bind(tokenField.textProperty().isEmpty());
        
        HBox urlBox = new HBox(6, urlField, browseReposBtn, createRepoBtn);
        urlBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        
        grid.add(urlBox, 1, row++);
        grid.add(new Label(), 0, row);
        grid.add(platformDetectionLabel, 1, row++);
        grid.add(new Label(s("git.add.remote.auth")), 0, row);
        grid.add(authCombo, 1, row++);

        VBox authBox = new VBox(6, usernameRow, passwordRow, tokenRow, sshKeyRow, sshPassRow);
        grid.add(authBox, 0, row++, 2, 1);
        grid.add(testRow, 0, row++, 2, 1);

        Separator sep = new Separator();
        grid.add(sep, 0, row++, 2, 1);
        grid.add(buttonBar, 0, row, 2, 1);

        VBox root = new VBox(grid);
        root.setPrefWidth(500);
        dialog.setScene(new Scene(root));
    }

    /**
     * Affiche le dialogue et attend la fermeture.
     *
     * @return {@code true} si l'utilisateur a sauvegardé le remote, {@code false} sinon.
     */
    public boolean showAndWait() {
        dialog.showAndWait();
        return saved;
    }

    /** @return l'URL saisie (utile après showAndWait() si saved=true). */
    public String getUrl() {
        return urlField.getText().strip();
    }

    // -------------------------------------------------------------------------
    // Implémentation privée
    // -------------------------------------------------------------------------

    private void handleTest() {
        String url = urlField.getText().strip();
        if (url.isBlank()) {
            testResultLabel.setText("URL required");
            return;
        }
        applyCredentialsToService();
        testResultLabel.setText("\u2026");
        log.info(LOG_SOURCE, "Testing remote connection: " + url);
        Thread t = new Thread(() -> {
            String result = gitService.testRemoteConnection(url);
            Platform.runLater(() -> {
                if (result.isEmpty()) {
                    log.info(LOG_SOURCE, "Test connection OK: " + url);
                    testResultLabel.setStyle("-fx-text-fill: green;");
                    testResultLabel.setText(s("git.add.remote.test.success"));
                } else {
                    log.warn(LOG_SOURCE, "Test connection FAILED: " + result);
                    testResultLabel.setStyle("-fx-text-fill: red;");
                    String pattern = s("git.add.remote.test.failure");
                    testResultLabel.setText(pattern.replace("{0}", result));
                }
            });
        }, "add-remote-test");
        t.setDaemon(true);
        t.start();
    }

    private void handleSave() {
        String url = urlField.getText().strip();
        if (url.isBlank()) {
            showError("URL required");
            return;
        }
        applyCredentialsToService();
        log.info(LOG_SOURCE, "Saving remote origin: " + url);
        try {
            gitService.addRemote("origin", url);
            log.info(LOG_SOURCE, "Remote 'origin' saved successfully.");
            saved = true;
            dialog.close();
        } catch (Exception e) {
            log.error(LOG_SOURCE, "Failed to save remote: " + e.getMessage());
            showError(e.getMessage());
        }
    }

    /**
     * Recopie les credentials saisis dans le service git ET dans la config
     * pour persistence.
     */
    private void applyCredentialsToService() {
        int idx = authCombo.getSelectionModel().getSelectedIndex();
        switch (idx) {
            case 1 -> { // basic
                config.setGitUsername(usernameField.getText().strip());
                // password stored in token field for simplicity
                config.setGitToken(passwordField.getText());
                gitService.setGitUsername(config.getGitUsername());
                gitService.setGitToken(config.getGitToken());
            }
            case 2 -> { // token
                config.setGitUsername(usernameField.getText().strip());
                config.setGitToken(tokenField.getText());
                gitService.setGitUsername(config.getGitUsername());
                gitService.setGitToken(config.getGitToken());
            }
            case 3 -> { // ssh
                config.setGitSshKeyPath(sshKeyPathField.getText().strip());
                gitService.setSshKeyPath(config.getGitSshKeyPath());
            }
            default -> { /* none — no credentials */ }
        }
        config.save();
    }

    private void updateAuthRows(int idx) {
        String mode = switch (idx) {
            case 1 -> AUTH_BASIC;
            case 2 -> AUTH_TOKEN;
            case 3 -> AUTH_SSH;
            default -> AUTH_NONE;
        };
        usernameRow.setVisible(!AUTH_NONE.equals(mode) && !AUTH_SSH.equals(mode));
        usernameRow.setManaged(!AUTH_NONE.equals(mode) && !AUTH_SSH.equals(mode));
        passwordRow.setVisible(AUTH_BASIC.equals(mode));
        passwordRow.setManaged(AUTH_BASIC.equals(mode));
        tokenRow.setVisible(AUTH_TOKEN.equals(mode));
        tokenRow.setManaged(AUTH_TOKEN.equals(mode));
        sshKeyRow.setVisible(AUTH_SSH.equals(mode));
        sshKeyRow.setManaged(AUTH_SSH.equals(mode));
        sshPassRow.setVisible(AUTH_SSH.equals(mode));
        sshPassRow.setManaged(AUTH_SSH.equals(mode));
    }

    private void browseSSHKey() {
        FileChooser fc = new FileChooser();
        fc.setTitle(safeKey("options.git.ssh.browse.title", "Sélectionner la clé SSH"));
        java.io.File f = fc.showOpenDialog(dialog);
        if (f != null) sshKeyPathField.setText(f.getAbsolutePath());
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialog);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private HBox labeledRow(String labelText, Control control) {
        Label lbl = new Label(labelText);
        lbl.setMinWidth(130);
        HBox row = new HBox(8, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private HBox buildSSHKeyRow(Button browseBtn) {
        Label lbl = new Label(s("git.add.remote.ssh.key"));
        lbl.setMinWidth(130);
        sshKeyPathField.setPrefWidth(200);
        HBox row = new HBox(6, lbl, sshKeyPathField, browseBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sshKeyPathField, Priority.ALWAYS);
        return row;
    }

    /** Shorthand for messages.getString */
    private String s(String key) {
        return safeKey(key, key);
    }

    private String safeKey(String key, String fallback) {
        try { return messages.getString(key); } catch (Exception e) { return fallback; }
    }

    /**
     * Ouvre le dialogue de navigation des dépôts distants.
     */
    private void handleBrowseRepositories() {
        String platform = RemoteConnectorFactory.detectPlatform(urlField.getText());
        if (platform == null) {
            platform = "github"; // default
        }
        String token = getTokenFromAuthFields();
        
        RemoteRepositoryBrowserDialog browserDialog = 
            new RemoteRepositoryBrowserDialog(dialog, platform, token);
        browserDialog.showAndWait();
        
        browserDialog.getSelectedRepository().ifPresent(repo -> {
            urlField.setText(repo.cloneUrl());
            testResultLabel.setText("");
        });
    }
    
    /**
     * Ouvre le dialogue de création de dépôt distant.
     */
    private void handleCreateRepository() {
        String platform = RemoteConnectorFactory.detectPlatform(urlField.getText());
        if (platform == null) {
            platform = "github"; // default
        }
        String token = getTokenFromAuthFields();
        
        CreateRemoteRepositoryDialog createDialog = 
            new CreateRemoteRepositoryDialog(dialog, platform, token);
        createDialog.showAndWait();
        
        createDialog.getCreatedRepository().ifPresent(repo -> {
            urlField.setText(repo.cloneUrl());
            testResultLabel.setStyle("-fx-text-fill: green;");
            testResultLabel.setText("✓ Repository created: " + repo.name());
        });
    }
    
    /**
     * Extrait le token des champs d'authentification selon le mode sélectionné.
     */
    private String getTokenFromAuthFields() {
        int idx = authCombo.getSelectionModel().getSelectedIndex();
        return switch (idx) {
            case 1 -> passwordField.getText(); // basic auth uses password
            case 2 -> tokenField.getText();    // token auth
            default -> config.getGitToken();   // fallback to config
        };
    }
    
    private static class Spacer extends Region {
        Spacer() { HBox.setHgrow(this, Priority.ALWAYS); }
    }
}
