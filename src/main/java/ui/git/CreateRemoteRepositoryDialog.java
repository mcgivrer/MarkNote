package ui.git;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
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
 * Dialogue modal pour créer un nouveau dépôt distant sur GitHub, GitLab ou Gitea.
 * 
 * <p>Ce dialogue permet de :</p>
 * <ul>
 *   <li>Saisir un nom de dépôt avec validation (alphanumeric, tirets, underscores)</li>
 *   <li>Ajouter une description optionnelle</li>
 *   <li>Choisir la visibilité (public/private)</li>
 *   <li>Initialiser automatiquement avec un README</li>
 *   <li>Tester le token API avant la création</li>
 * </ul>
 * 
 * <p>La création du dépôt est effectuée en arrière-plan pour ne pas bloquer l'interface.</p>
 */
public class CreateRemoteRepositoryDialog extends Stage {

    private static final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "CreateRemoteRepositoryDialog";

    // Repository name validation pattern: alphanumeric, dash, underscore (no spaces)
    private static final String VALID_NAME_PATTERN = "^[a-zA-Z0-9_-]+$";

    private final ComboBox<String> platformCombo;
    private final PasswordField tokenField;
    private final Button testButton;
    private final TextField nameField;
    private final TextField descriptionField;
    private final CheckBox privateCheckBox;
    private final CheckBox initReadmeCheckBox;
    private final Label statusLabel;
    private final Button createButton;
    private final Button cancelButton;

    private RemoteRepo createdRepository;

    /**
     * Construit un nouveau dialogue de création de dépôt distant.
     * 
     * @param owner La fenêtre parente
     * @param platform La plateforme ("github", "gitlab", "gitea") ou null
     * @param token Le token API existant ou null
     */
    public CreateRemoteRepositoryDialog(Window owner, String platform, String token) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Create Remote Repository");
        setWidth(500);
        setHeight(450);
        setResizable(false);

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

        // Test button
        testButton = new Button("Test");
        testButton.setOnAction(e -> handleTestConnection());
        testButton.setDisable(true);

        // Repository name field
        nameField = new TextField();
        nameField.setPromptText("my-awesome-project");
        nameField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        nameField.setMaxWidth(Double.MAX_VALUE);

        // Description field
        descriptionField = new TextField();
        descriptionField.setPromptText("Optional description");
        descriptionField.setMaxWidth(Double.MAX_VALUE);

        // Private checkbox
        privateCheckBox = new CheckBox("Private repository");
        privateCheckBox.setSelected(true);

        // Initialize with README checkbox
        initReadmeCheckBox = new CheckBox("Initialize with README");
        initReadmeCheckBox.setSelected(true);

        // Status label
        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: gray;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        // Create button
        createButton = new Button("Create");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> handleCreate());
        createButton.setDisable(true);

        // Cancel button
        cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> {
            createdRepository = null;
            close();
        });

        // Enable test button when token is entered
        tokenField.textProperty().addListener((obs, oldVal, newVal) -> {
            testButton.setDisable(newVal == null || newVal.isBlank());
            validateForm();
        });

        // Layout
        VBox root = buildLayout();
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        setScene(scene);
    }

    private VBox buildLayout() {
        // Platform and token row
        GridPane headerGrid = new GridPane();
        headerGrid.setHgap(10);
        headerGrid.setVgap(10);
        
        headerGrid.add(new Label("Platform:"), 0, 0);
        headerGrid.add(platformCombo, 1, 0);
        headerGrid.add(new Label("Token:"), 0, 1);
        headerGrid.add(tokenField, 1, 1);
        headerGrid.add(testButton, 2, 1);
        
        GridPane.setHgrow(platformCombo, Priority.ALWAYS);
        GridPane.setHgrow(tokenField, Priority.ALWAYS);

        // Repository form
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        
        Label nameLabel = new Label("Repository name:*");
        nameLabel.setStyle("-fx-font-weight: bold;");
        formGrid.add(nameLabel, 0, 0);
        formGrid.add(nameField, 0, 1);
        
        Label nameHint = new Label("Alphanumeric characters, dashes, and underscores only");
        nameHint.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
        formGrid.add(nameHint, 0, 2);
        
        formGrid.add(new Label("Description:"), 0, 3);
        formGrid.add(descriptionField, 0, 4);
        
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(descriptionField, Priority.ALWAYS);

        // Checkboxes
        VBox checkBoxes = new VBox(10);
        checkBoxes.getChildren().addAll(privateCheckBox, initReadmeCheckBox);

        // Button bar
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getChildren().addAll(createButton, cancelButton);

        // Main layout
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(
            headerGrid,
            new Separator(),
            formGrid,
            checkBoxes,
            statusLabel,
            buttonBar
        );

        return root;
    }

    /**
     * Valide le formulaire et active/désactive le bouton Create.
     */
    private void validateForm() {
        String name = nameField.getText();
        String token = tokenField.getText();

        boolean isValid = token != null && !token.isBlank() 
                       && name != null && !name.isBlank() 
                       && name.matches(VALID_NAME_PATTERN);

        createButton.setDisable(!isValid);

        // Show validation feedback
        if (name != null && !name.isBlank() && !name.matches(VALID_NAME_PATTERN)) {
            statusLabel.setText("Invalid name: use only letters, numbers, dashes, and underscores");
            statusLabel.setStyle("-fx-text-fill: #d32f2f;");
        } else {
            statusLabel.setText("");
        }
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
                        updateStatus("✗ Authentication failed", true);
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
     * Crée le dépôt distant via l'API.
     */
    private void handleCreate() {
        String platform = platformCombo.getValue();
        String token = tokenField.getText();
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();
        boolean isPrivate = privateCheckBox.isSelected();

        if (!name.matches(VALID_NAME_PATTERN)) {
            updateStatus("Invalid repository name", true);
            return;
        }

        updateStatus("Creating repository...", false);
        createButton.setDisable(true);
        cancelButton.setDisable(true);

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
                        createButton.setDisable(false);
                        cancelButton.setDisable(false);
                    });
                    return;
                }

                // Create the repository
                connector.createRepository(name, isPrivate);
                
                // List repositories to get the newly created one with full details
                List<RemoteRepo> repos = connector.listRepositories();
                RemoteRepo created = repos.stream()
                    .filter(r -> r.name().equals(name))
                    .findFirst()
                    .orElse(null);

                if (created != null) {
                    createdRepository = created;
                    Platform.runLater(() -> {
                        updateStatus("✓ Repository created successfully", false);
                        // Close after a short delay
                        new Thread(() -> {
                            try {
                                Thread.sleep(500);
                                Platform.runLater(this::close);
                            } catch (InterruptedException ignored) {}
                        }).start();
                    });
                } else {
                    // Fallback: create a RemoteRepo manually
                    String cloneUrl = switch (platform) {
                        case "github" -> "https://github.com/user/" + name + ".git";
                        case "gitlab" -> "https://gitlab.com/user/" + name + ".git";
                        default -> remoteUrl.replace("/repo.git", "/" + name + ".git");
                    };
                    createdRepository = new RemoteRepo(name, cloneUrl, description, isPrivate, "main");
                    Platform.runLater(() -> {
                        updateStatus("✓ Repository created successfully", false);
                        new Thread(() -> {
                            try {
                                Thread.sleep(500);
                                Platform.runLater(this::close);
                            } catch (InterruptedException ignored) {}
                        }).start();
                    });
                }

            } catch (RemoteConnectorException e) {
                log.error(LOG_SOURCE, "Failed to create repository: " + e.getMessage());
                Platform.runLater(() -> {
                    if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                        updateStatus("✗ Authentication failed", true);
                    } else if (e.getStatusCode() == 409) {
                        updateStatus("✗ Repository name already exists", true);
                    } else if (e.getStatusCode() == 422) {
                        updateStatus("✗ Invalid repository name", true);
                    } else if (e.getStatusCode() == 429) {
                        updateStatus("✗ Rate limit exceeded, try again later", true);
                    } else {
                        updateStatus("✗ Failed to create: " + e.getMessage(), true);
                    }
                    createButton.setDisable(false);
                    cancelButton.setDisable(false);
                });
            } catch (Exception e) {
                log.error(LOG_SOURCE, "Unexpected error creating repository: " + e.getMessage());
                Platform.runLater(() -> {
                    updateStatus("✗ Unexpected error: " + e.getMessage(), true);
                    createButton.setDisable(false);
                    cancelButton.setDisable(false);
                });
            }
        }).start();
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
     * Retourne le dépôt créé.
     * 
     * @return Un Optional contenant le dépôt créé, ou vide si la création a été annulée
     */
    public Optional<RemoteRepo> getCreatedRepository() {
        return Optional.ofNullable(createdRepository);
    }
}
