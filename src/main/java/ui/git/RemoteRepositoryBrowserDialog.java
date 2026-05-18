package ui.git;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import services.git.RemoteConnector;
import services.git.RemoteConnector.RemoteRepo;
import services.git.RemoteConnectorException;
import services.git.RemoteConnectorFactory;
import utils.LogService;

import java.util.List;
import java.util.Optional;

/**
 * Dialogue modal pour parcourir les dépôts distants d'une plateforme Git (GitHub, GitLab, Gitea).
 * 
 * <p>Ce dialogue permet de :</p>
 * <ul>
 *   <li>Lister tous les dépôts de l'utilisateur sur la plateforme sélectionnée</li>
 *   <li>Tester la validité du token API avant de charger</li>
 *   <li>Sélectionner un dépôt pour obtenir son URL de clone</li>
 *   <li>Rafraîchir la liste des dépôts</li>
 * </ul>
 * 
 * <p>Tous les appels API sont effectués en arrière-plan pour ne pas bloquer l'interface.</p>
 */
public class RemoteRepositoryBrowserDialog extends Stage {

    private static final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "RemoteRepositoryBrowserDialog";

    private final ComboBox<String> platformCombo;
    private final PasswordField tokenField;
    private final Button testButton;
    private final ListView<RemoteRepo> repoListView;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button selectButton;
    private final Button cancelButton;

    private RemoteRepo selectedRepository;
    private String detectedPlatform;

    /**
     * Construit un nouveau dialogue de navigation des dépôts distants.
     * 
     * @param owner La fenêtre parente
     * @param platform La plateforme détectée ("github", "gitlab", "gitea") ou null
     * @param token Le token API existant ou null
     */
    public RemoteRepositoryBrowserDialog(Window owner, String platform, String token) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Browse Remote Repositories");
        setWidth(650);
        setHeight(500);

        this.detectedPlatform = platform;

        // Platform selector
        platformCombo = new ComboBox<>();
        platformCombo.getItems().addAll("github", "gitlab", "gitea");
        platformCombo.setValue(platform != null ? platform : "github");
        platformCombo.setMaxWidth(Double.MAX_VALUE);

        // Token field
        tokenField = new PasswordField();
        tokenField.setPromptText("Enter your API token");
        if (token != null && !token.isBlank()) {
            tokenField.setText(token);
        }
        tokenField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tokenField, Priority.ALWAYS);

        // Test button
        testButton = new Button("Test");
        testButton.setOnAction(e -> handleTestConnection());
        testButton.setDisable(true);

        // Enable test button when token is entered
        tokenField.textProperty().addListener((obs, oldVal, newVal) -> {
            testButton.setDisable(newVal == null || newVal.isBlank());
        });

        // Status label
        statusLabel = new Label("Enter your token and click Refresh to load repositories");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: gray;");

        // Buttons
        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> handleRefresh());
        refreshButton.setDisable(true);

        selectButton = new Button("Select");
        selectButton.setDefaultButton(true);
        selectButton.setOnAction(e -> handleSelect());
        selectButton.setDisable(true);

        cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> {
            selectedRepository = null;
            close();
        });

        // Repository list
        repoListView = new ListView<>();
        repoListView.setCellFactory(lv -> new RepositoryCell());
        repoListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectButton.setDisable(newVal == null);
        });
        // Double-click to select
        repoListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && repoListView.getSelectionModel().getSelectedItem() != null) {
                handleSelect();
            }
        });
        VBox.setVgrow(repoListView, Priority.ALWAYS);

        // Enable refresh when token is entered
        tokenField.textProperty().addListener((obs, oldVal, newVal) -> {
            refreshButton.setDisable(newVal == null || newVal.isBlank());
        });

        // Layout
        VBox root = buildLayout();
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        setScene(scene);

        // Auto-load if token provided
        if (token != null && !token.isBlank()) {
            Platform.runLater(this::loadRepositories);
        }
    }

    private VBox buildLayout() {
        // Platform and token row
        HBox platformRow = new HBox(10);
        platformRow.setAlignment(Pos.CENTER_LEFT);
        platformRow.getChildren().addAll(
            new Label("Platform:"), platformCombo,
            new Label("Token:"), tokenField, testButton
        );

        // Button bar
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getChildren().addAll(refreshButton, selectButton, cancelButton);

        // Main layout
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(
            platformRow,
            new Separator(),
            repoListView,
            statusLabel,
            buttonBar
        );

        return root;
    }

    /**
     * Teste la connexion à l'API avec le token fourni.
     */
    private void handleTestConnection() {
        String platform = platformCombo.getValue();
        String token = tokenField.getText();

        if (token == null || token.isBlank()) {
            updateStatus("Please enter a token", true);
            return;
        }

        updateStatus("Testing connection...", false);
        testButton.setDisable(true);

        // Run in background
        new Thread(() -> {
            try {
                // Create a dummy remote URL to test the connector
                String testUrl = switch (platform) {
                    case "github" -> "https://github.com/test/repo.git";
                    case "gitlab" -> "https://gitlab.com/test/repo.git";
                    default -> "https://gitea.example.com/test/repo.git";
                };

                RemoteConnector connector = RemoteConnectorFactory.create(testUrl, token);
                if (connector != null) {
                    // Try to list repositories to validate token
                    connector.listRepositories();
                    Platform.runLater(() -> {
                        updateStatus("✓ Token is valid", false);
                        testButton.setDisable(false);
                    });
                } else {
                    Platform.runLater(() -> {
                        updateStatus("Failed to create connector", true);
                        testButton.setDisable(false);
                    });
                }
            } catch (RemoteConnectorException e) {
                log.error(LOG_SOURCE, "Token test failed: " + e.getMessage());
                Platform.runLater(() -> {
                    if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                        updateStatus("✗ Invalid token", true);
                    } else if (e.getStatusCode() == 429) {
                        updateStatus("✗ Rate limit exceeded, try again later", true);
                    } else {
                        updateStatus("✗ Connection failed: " + e.getMessage(), true);
                    }
                    testButton.setDisable(false);
                });
            } catch (Exception e) {
                log.error(LOG_SOURCE, "Unexpected error during token test: " + e.getMessage());
                Platform.runLater(() -> {
                    updateStatus("✗ Unexpected error: " + e.getMessage(), true);
                    testButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * Charge la liste des dépôts depuis l'API.
     */
    private void loadRepositories() {
        handleRefresh();
    }

    /**
     * Rafraîchit la liste des dépôts.
     */
    private void handleRefresh() {
        String platform = platformCombo.getValue();
        String token = tokenField.getText();

        if (token == null || token.isBlank()) {
            updateStatus("Please enter a token", true);
            return;
        }

        updateStatus("Loading repositories...", false);
        repoListView.getItems().clear();
        refreshButton.setDisable(true);
        selectButton.setDisable(true);

        // Run in background
        new Thread(() -> {
            try {
                // Create remote URL based on platform
                String remoteUrl = switch (platform) {
                    case "github" -> "https://github.com/user/repo.git";
                    case "gitlab" -> "https://gitlab.com/user/repo.git";
                    default -> "https://gitea.example.com/user/repo.git";
                };

                RemoteConnector connector = RemoteConnectorFactory.create(remoteUrl, token);
                if (connector == null) {
                    Platform.runLater(() -> {
                        updateStatus("Failed to create connector for " + platform, true);
                        refreshButton.setDisable(false);
                    });
                    return;
                }

                List<RemoteRepo> repos = connector.listRepositories();

                Platform.runLater(() -> {
                    repoListView.getItems().addAll(repos);
                    if (repos.isEmpty()) {
                        updateStatus("No repositories found", false);
                    } else {
                        updateStatus(repos.size() + " repositories found", false);
                    }
                    refreshButton.setDisable(false);
                });

            } catch (RemoteConnectorException e) {
                log.error(LOG_SOURCE, "Failed to list repositories: " + e.getMessage());
                Platform.runLater(() -> {
                    if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                        updateStatus("✗ Invalid token", true);
                    } else if (e.getStatusCode() == 429) {
                        updateStatus("✗ Rate limit exceeded, try again later", true);
                    } else {
                        updateStatus("✗ Failed to load: " + e.getMessage(), true);
                    }
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                log.error(LOG_SOURCE, "Unexpected error loading repositories: " + e.getMessage());
                Platform.runLater(() -> {
                    updateStatus("✗ Unexpected error: " + e.getMessage(), true);
                    refreshButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * Sélectionne le dépôt actuellement choisi et ferme le dialogue.
     */
    private void handleSelect() {
        selectedRepository = repoListView.getSelectionModel().getSelectedItem();
        if (selectedRepository != null) {
            close();
        }
    }

    /**
     * Met à jour le label de statut.
     * 
     * @param message Le message à afficher
     * @param isError True si c'est un message d'erreur (affiché en rouge)
     */
    private void updateStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #d32f2f;" : "-fx-text-fill: gray;");
    }

    /**
     * Retourne le dépôt sélectionné.
     * 
     * @return Un Optional contenant le dépôt sélectionné, ou vide si aucun n'a été sélectionné
     */
    public Optional<RemoteRepo> getSelectedRepository() {
        return Optional.ofNullable(selectedRepository);
    }

    /**
     * Cell renderer personnalisé pour afficher les informations d'un dépôt.
     */
    private static class RepositoryCell extends ListCell<RemoteRepo> {
        @Override
        protected void updateItem(RemoteRepo repo, boolean empty) {
            super.updateItem(repo, empty);

            if (empty || repo == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(5);
                
                // Title row: name + visibility badge
                HBox titleRow = new HBox(10);
                titleRow.setAlignment(Pos.CENTER_LEFT);
                
                Label nameLabel = new Label(repo.name());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                
                Label visibilityLabel = new Label(repo.isPrivate() ? "Private" : "Public");
                visibilityLabel.setStyle(repo.isPrivate() 
                    ? "-fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-padding: 2 6 2 6; -fx-background-radius: 3;"
                    : "-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
                
                titleRow.getChildren().addAll(nameLabel, visibilityLabel);
                
                // Clone URL
                Label urlLabel = new Label(repo.cloneUrl());
                urlLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                
                content.getChildren().addAll(titleRow, urlLabel);
                
                // Description if present
                if (repo.description() != null && !repo.description().isBlank()) {
                    Label descLabel = new Label(repo.description());
                    descLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
                    descLabel.setWrapText(true);
                    content.getChildren().add(descLabel);
                }
                
                setGraphic(content);
            }
        }
    }
}
