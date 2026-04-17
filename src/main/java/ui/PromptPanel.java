package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import config.LLMConfig;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import services.LLMService;
import services.Message;
import services.MessageRole;
import utils.LogService;

/**
 * Panel principal pour le chat LLM. Étend BasePanel pour s'intégrer au système
 * de docking.
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
    private Button insertAllButton;

    /** Fournit le DocumentTab actif pour l'insertion de réponses. */
    private Supplier<DocumentTab> activeDocumentSupplier;

    /** Fournit la liste de tous les DocumentTab ouverts pour le contexte. */
    private Supplier<List<DocumentTab>> openTabsSupplier;

    /**
     * Callback pour naviguer vers un DocumentTab (depuis les liens de contexte).
     */
    private Consumer<DocumentTab> onOpenTab;

    /**
     * Crée un nouveau panel de chat LLM.
     *
     * @param llmConfig La configuration LLM
     */
    public PromptPanel(LLMConfig llmConfig) {
        super("llm.panel.title", "llm.panel.close.tooltip");

        var chatCss = getClass().getResource("/css/markdown-editor.css");
        if (chatCss != null) {
            getStylesheets().add(chatCss.toExternalForm());
        }

        this.llmConfig = llmConfig;
        this.llmService = new LLMService(llmConfig);
        this.currentSession = new ArrayList<>();

        // Créer les composants
        conversationView = new ConversationView();
        promptInput = new PromptInputArea();

        // Configurer les callbacks
        setupCallbacks();

        // Câblage de l'insertion dans le document actif
        conversationView.setOnInsertToDocument(content -> {
            if (activeDocumentSupplier != null) {
                DocumentTab tab = activeDocumentSupplier.get();
                if (tab != null) {
                    tab.insertAtNextLine(content);
                }
            }
        });

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

        // Afficher le message de bienvenue lors de la première ouverture
        showWelcomeMessage();
    }

    /**
     * Soumet un prompt au LLM.
     */
    public void submitPrompt() {
        String promptText = promptInput.getText();
        if (promptText == null || promptText.isBlank()) {
            return;
        }

        List<String> selectedNames = promptInput.getSelectedDocuments();

        // Message envoyé à l'API : contenu complet des fichiers + question
        String apiText = buildEnrichedPrompt(promptText, selectedNames);

        // Message affiché dans le chat : texte brut uniquement
        Message displayMessage = new Message(MessageRole.USER, promptText);
        // Message pour l'API
        Message apiMessage = new Message(MessageRole.USER, apiText);

        currentSession.add(apiMessage);

        // Construire les liens de contexte (icone + nom du fichier, avec navigation au
        // clic)
        List<ConversationView.ContextLink> contextLinks = buildContextLinks(selectedNames);
        conversationView.addMessage(displayMessage, contextLinks);

        promptInput.clear();
        promptInput.setProcessing(true);

        // Snapshot de la session AVANT d'ajouter le message assistant vide
        // (l'API ne doit pas recevoir de message assistant vide en dernier)
        List<Message> messagesForApi = new ArrayList<>(currentSession);

        // Créer le message assistant pour le streaming (affiché localement)
        Message assistantMessage = conversationView.createAssistantMessage();
        currentSession.add(assistantMessage);

        // Envoyer au LLM seulement les messages jusqu'au user message inclus
        llmService.sendPromptAsync(messagesForApi,
                chunk -> Platform.runLater(() -> conversationView.appendToLastMessage(chunk)),
                () -> Platform.runLater(() -> {
                    promptInput.setProcessing(false);
                    conversationView.scrollToBottom();
                    log.info(LOG_SOURCE, "Response completed");
                }), error -> Platform.runLater(() -> {
                    promptInput.setProcessing(false);
                    // Ajouter un message d'erreur
                    String detail = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                    String errorMsg = getMessages().getString("llm.error.connection") + ": " + detail;
                    conversationView.appendToLastMessage("\n\n**Erreur:** " + errorMsg);
                    log.error(LOG_SOURCE, "LLM error: " + error);
                }));
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
     * Définit le fournisseur du DocumentTab actif pour l'insertion de texte.
     *
     * @param supplier Le supplier retournant le tab actif (ou null)
     */
    public void setActiveDocumentSupplier(Supplier<DocumentTab> supplier) {
        this.activeDocumentSupplier = supplier;
    }

    /**
     * Définit le fournisseur de la liste de tous les onglets ouverts. Utilisé pour
     * alimenter la barre de sélection des documents-contexte.
     *
     * @param supplier Le supplier retournant la liste des DocumentTab ouverts
     */
    public void setOpenTabsSupplier(Supplier<List<DocumentTab>> supplier) {
        this.openTabsSupplier = supplier;
    }

    /**
     * Définit le callback appelé lorsque l'utilisateur clique sur un lien de
     * contexte. Typiquement : sélectionner l'onglet correspondant dans mainTabPane.
     *
     * @param onOpenTab Consumer recevant le DocumentTab à activer
     */
    public void setOnOpenTab(Consumer<DocumentTab> onOpenTab) {
        this.onOpenTab = onOpenTab;
    }

    /**
     * Rafraîchit la barre de sélection des documents dans {@link PromptInputArea}.
     * À appeler depuis {@code MarkNote} lors de tout changement de la liste
     * d'onglets.
     */
    public void refreshDocumentContext() {
        List<String> names = List.of();
        if (openTabsSupplier != null) {
            names = openTabsSupplier.get().stream().map(t -> t.getFile() != null ? t.getFile().getName() : t.getText())
                    .toList();
        }
        promptInput.updateDocumentTabs(names);
    }

    /**
     * Insère l'intégralité de la session dans le document actif.
     */
    public void insertAllToDocument() {
        if (activeDocumentSupplier == null)
            return;
        DocumentTab tab = activeDocumentSupplier.get();
        if (tab == null)
            return;
        StringBuilder sb = new StringBuilder();
        for (Message msg : currentSession) {
            sb.append(msg.getRole() == MessageRole.USER ? "> " : "");
            sb.append(msg.getContent());
            sb.append("\n\n");
        }
        if (!sb.isEmpty()) {
            tab.insertAtNextLine(sb.toString().stripTrailing());
        }
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

    private void showWelcomeMessage() {
        var msgs = getMessages();
        String apiType = llmConfig.isOpenAIFormat() ? "OpenAI" : "Ollama";
        String contextStatus = llmConfig.getSystemContext().isBlank()
                ? msgs.getString("llm.welcome.context.none")
                : msgs.getString("llm.welcome.context.defined");

        String content = "## " + msgs.getString("llm.welcome.title") + "\n\n"
                + "**" + msgs.getString("llm.welcome.config") + "**\n\n"
                + "- **" + msgs.getString("llm.welcome.endpoint") + "** : `" + llmConfig.getEndpointUrl() + "`\n"
                + "- **" + msgs.getString("llm.welcome.model") + "** : `" + llmConfig.getModel() + "`\n"
                + "- **" + msgs.getString("llm.welcome.timeout") + "** : " + llmConfig.getTimeout() + " " + msgs.getString("llm.welcome.timeout.unit") + "\n"
                + "- **" + msgs.getString("llm.welcome.type") + "** : " + apiType + "\n"
                + "- **" + msgs.getString("llm.welcome.context.label") + "** : " + contextStatus + "\n";

        conversationView.addMessage(new Message(MessageRole.ASSISTANT, content));
    }

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

    /**
     * Construit la liste des liens de contexte à afficher dans le message chat.
     * Chaque lien identifie un document et ouvre l'onglet correspondant au clic.
     */
    private List<ConversationView.ContextLink> buildContextLinks(List<String> selectedNames) {
        if (selectedNames.isEmpty() || openTabsSupplier == null)
            return List.of();
        List<DocumentTab> openTabs = openTabsSupplier.get();
        List<ConversationView.ContextLink> links = new ArrayList<>();
        for (String name : selectedNames) {
            openTabs.stream().filter(t -> name.equals(t.getFile() != null ? t.getFile().getName() : t.getText()))
                    .findFirst().ifPresent(tab -> links.add(new ConversationView.ContextLink(name, () -> {
                        if (onOpenTab != null)
                            onOpenTab.accept(tab);
                    })));
        }
        return links;
    }

    /**
     * Construit le texte envoy\u00e9 \u00e0 l'API (contenu complet des fichiers +
     * question). Si aucun document n'est s\u00e9lectionn\u00e9, retourne le prompt
     * brut inchang\u00e9.
     */
    private String buildEnrichedPrompt(String userPrompt, List<String> selectedNames) {
        if (selectedNames.isEmpty() || openTabsSupplier == null) {
            return userPrompt;
        }

        List<DocumentTab> openTabs = openTabsSupplier.get();
        StringBuilder sb = new StringBuilder();
        for (String name : selectedNames) {
            openTabs.stream().filter(t -> name.equals(t.getFile() != null ? t.getFile().getName() : t.getText()))
                    .findFirst().ifPresent(tab -> {
                        sb.append("### Document : ").append(name).append("\n\n");
                        sb.append(tab.getFullContent().strip()).append("\n\n");
                    });
        }
        if (!sb.isEmpty()) {
            sb.append("---\n\n").append(userPrompt);
            return sb.toString();
        }
        return userPrompt;
    }

    private void setupHeaderButtons() {
        HBox header = getHeader();
        if (header == null)
            return;

        // Bouton export
        exportButton = new Button("\u2913"); // ⤓
        exportButton.getStyleClass().add("panel-header-button");
        exportButton.setTooltip(new Tooltip(getMessages().getString("llm.export.session")));
        exportButton.setOnAction(e -> exportSession());

        // Bouton insérer dans le document
        insertAllButton = new Button("\u2937"); // ↷
        insertAllButton.getStyleClass().add("panel-header-button");
        insertAllButton.setTooltip(new Tooltip(getMessages().getString("llm.insert.all.document")));
        insertAllButton.setOnAction(e -> insertAllToDocument());

        // Bouton clear
        clearButton = new Button("\u2717"); // ✗
        clearButton.getStyleClass().add("panel-header-button");
        clearButton.setTooltip(new Tooltip(getMessages().getString("llm.clear.session")));
        clearButton.setOnAction(e -> clearSession());

        // Insérer avant le spacer (index 1)
        int insertIndex = Math.min(1, header.getChildren().size());
        header.getChildren().add(insertIndex, exportButton);
        header.getChildren().add(insertIndex, insertAllButton);
        header.getChildren().add(insertIndex, clearButton);
    }
}
