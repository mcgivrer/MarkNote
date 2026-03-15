package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import config.LLMConfig;
import services.LLMService;
import services.Message;
import services.MessageRole;
import utils.LogService;

/**
 * Panel principal pour le chat LLM.
 * Étend BasePanel pour s'intégrer au système de docking.
 */
public class PromptPanel extends BasePanel {

    private static final String LOG_SOURCE = "PromptPanel";
    private final LogService log = LogService.getInstance();

    // getMessages() is inherited from BasePanel

    private final ConversationView conversationView;
    private final PromptInputArea promptInput;
    private final LLMService llmService;
    private final LLMConfig llmConfig;
    private final List<Message> currentSession;

    private Button clearButton;
    private Button exportButton;

    /**
     * Crée un nouveau panel de chat LLM.
     *
     * @param llmConfig La configuration LLM
     */
    public PromptPanel(LLMConfig llmConfig) {
        super("llm.panel.title", "llm.panel.close.tooltip");

        this.llmConfig = llmConfig;
        this.llmService = new LLMService(llmConfig);
        this.currentSession = new ArrayList<>();

        // Créer les composants
        conversationView = new ConversationView();
        promptInput = new PromptInputArea();

        // Configurer les callbacks
        setupCallbacks();

        // Layout en SplitPane vertical (2/3 conversation, 1/3 input)
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(conversationView, promptInput);
        splitPane.setDividerPositions(0.7);
        SplitPane.setResizableWithParent(promptInput, false);

        // Ajouter les boutons supplémentaires au header
        setupHeaderButtons();

        setContent(splitPane);
        getStyleClass().add("prompt-panel");
    }

    /**
     * Soumet un prompt au LLM.
     */
    public void submitPrompt() {
        String promptText = promptInput.getText();
        if (promptText == null || promptText.isBlank()) {
            return;
        }

        // Ajouter le message user
        Message userMessage = new Message(MessageRole.USER, promptText);
        currentSession.add(userMessage);
        conversationView.addMessage(userMessage);
        
        promptInput.clear();
        promptInput.setProcessing(true);

        // Créer le message assistant pour le streaming
        Message assistantMessage = conversationView.createAssistantMessage();
        currentSession.add(assistantMessage);

        // Envoyer au LLM
        llmService.sendPromptAsync(
                currentSession,
                chunk -> Platform.runLater(() -> conversationView.appendToLastMessage(chunk)),
                () -> Platform.runLater(() -> {
                    promptInput.setProcessing(false);
                    conversationView.scrollToBottom();
                    log.info(LOG_SOURCE, "Response completed");
                }),
                error -> Platform.runLater(() -> {
                    promptInput.setProcessing(false);
                    // Ajouter un message d'erreur
                    String errorMsg = getMessages().getString("llm.error.connection") + ": " + error.getMessage();
                    conversationView.appendToLastMessage("\n\n**Error:** " + errorMsg);
                    log.error(LOG_SOURCE, "LLM error: " + error.getMessage());
                })
        );
    }

    /**
     * Annule la requête en cours.
     */
    public void cancelRequest() {
        llmService.cancelRequest();
        promptInput.setProcessing(false);
        log.info(LOG_SOURCE, "Request cancelled by user");
    }

    /**
     * Exporte la session en cours.
     */
    public void exportSession() {
        if (getScene() != null && getScene().getWindow() != null) {
            conversationView.exportAll(getScene().getWindow());
        }
    }

    /**
     * Ouvre le dialogue de contexte système.
     */
    public void openContextDialog() {
        if (getScene() == null || getScene().getWindow() == null) {
            return;
        }

        ContextDialog dialog = new ContextDialog(getScene().getWindow());
        dialog.setContext(llmConfig.getSystemContext());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(context -> {
            llmConfig.setSystemContext(context);
            llmConfig.save();
            log.info(LOG_SOURCE, "System context updated");
        });
    }

    /**
     * Efface la session en cours.
     */
    public void clearSession() {
        currentSession.clear();
        conversationView.clear();
        log.info(LOG_SOURCE, "Session cleared");
    }

    /**
     * Retourne le service LLM.
     *
     * @return Le service LLM
     */
    public LLMService getLLMService() {
        return llmService;
    }

    /**
     * Retourne la configuration LLM.
     *
     * @return La configuration
     */
    public LLMConfig getLLMConfig() {
        return llmConfig;
    }

    /**
     * Retourne la liste des messages de la session.
     *
     * @return La liste des messages
     */
    public List<Message> getCurrentSession() {
        return new ArrayList<>(currentSession);
    }

    @Override
    public String getDetachTabTitle() {
        return getMessages().getString("llm.panel.title");
    }

    // --- Private methods ---

    private void setupCallbacks() {
        promptInput.setOnSubmit(text -> submitPrompt());
        promptInput.setOnCancel(this::cancelRequest);
        promptInput.setOnContextClick(this::openContextDialog);

        conversationView.setOnEditMessage(() -> {
            int index = conversationView.getEditingIndex();
            if (index >= 0 && index < currentSession.size()) {
                Message msg = currentSession.get(index);
                if (msg.getRole() == MessageRole.USER) {
                    promptInput.setText(msg.getContent());
                    // Supprimer les messages à partir de cet index
                    while (currentSession.size() > index) {
                        currentSession.remove(currentSession.size() - 1);
                    }
                    conversationView.clear();
                    // Réafficher les messages restants
                    for (Message m : currentSession) {
                        conversationView.addMessage(m);
                    }
                    promptInput.focusInput();
                }
            }
        });
    }

    private void setupHeaderButtons() {
        HBox header = getHeader();
        if (header == null) return;

        // Bouton export
        exportButton = new Button("\u2913"); // ⤓
        exportButton.getStyleClass().add("panel-header-button");
        exportButton.setTooltip(new Tooltip(getMessages().getString("llm.export.session")));
        exportButton.setOnAction(e -> exportSession());

        // Bouton clear
        clearButton = new Button("\u2717"); // ✗
        clearButton.getStyleClass().add("panel-header-button");
        clearButton.setTooltip(new Tooltip(getMessages().getString("llm.clear.session")));
        clearButton.setOnAction(e -> clearSession());

        // Insérer avant le spacer (index 1)
        int insertIndex = Math.min(1, header.getChildren().size());
        header.getChildren().add(insertIndex, exportButton);
        header.getChildren().add(insertIndex, clearButton);
    }
}
