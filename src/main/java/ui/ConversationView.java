package ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import services.Message;
import services.MessageRole;
import utils.LogService;

/**
 * Vue de la conversation LLM avec historique des messages.
 */
public class ConversationView extends VBox {

    /**
     * Lien vers un document de contexte affiché dans un message utilisateur.
     *
     * @param name   Nom du document (affiché dans le lien)
     * @param action Action exécutée au clic (ex: ouvrir l'onglet correspondant)
     */
    public record ContextLink(String name, Runnable action) {}

    private static final String LOG_SOURCE = "ConversationView";
    private final LogService log = LogService.getInstance();

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final VBox messagesContainer;
    private final ScrollPane scrollPane;
    private Runnable onEditMessage;
    private java.util.function.Consumer<String> onInsertToDocument;
    private int editingIndex = -1;

    /**
     * Crée une nouvelle vue de conversation.
     */
    public ConversationView() {
        messagesContainer = new VBox(8);
        messagesContainer.setPadding(new Insets(10));
        messagesContainer.getStyleClass().add("conversation-container");

        scrollPane = new ScrollPane(messagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("conversation-scroll");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);
        getStyleClass().add("conversation-view");
    }

    /**
     * Ajoute un message à la conversation.
     *
     * @param message Le message à ajouter
     */
    public void addMessage(Message message) {
        addMessage(message, List.of());
    }

    /**
     * Ajoute un message à la conversation avec des liens de contexte.
     * Les liens sont affichés sous forme de puces cliquables au-dessus du texte du message.
     *
     * @param message      Le message à ajouter
     * @param contextLinks Les liens vers les documents de contexte
     */
    public void addMessage(Message message, List<ContextLink> contextLinks) {
        Platform.runLater(() -> {
            HBox wrapper = createMessageWrapper(message, messagesContainer.getChildren().size(), contextLinks);
            messagesContainer.getChildren().add(wrapper);
            scrollToBottom();
        });
    }

    /**
     * Crée le dernier bloc de message assistant (pour streaming).
     *
     * @return Le message créé
     */
    public Message createAssistantMessage() {
        Message message = new Message(MessageRole.ASSISTANT, "");
        Platform.runLater(() -> {
            HBox wrapper = createMessageWrapper(message, messagesContainer.getChildren().size(), List.of());
            messagesContainer.getChildren().add(wrapper);
        });
        return message;
    }

    /**
     * Ajoute du contenu au dernier message (streaming).
     *
     * @param chunk Le contenu à ajouter
     */
    public void appendToLastMessage(String chunk) {
        Platform.runLater(() -> {
            if (!messagesContainer.getChildren().isEmpty()) {
                var lastNode = messagesContainer.getChildren().get(messagesContainer.getChildren().size() - 1);
                MessageBlock mb = findMessageBlock(lastNode);
                if (mb != null) {
                    Object userData = mb.getUserData();
                    if (userData instanceof Message message) {
                        message.appendContent(chunk);
                        mb.updateContent(message.getContent());
                    }
                }
            }
            scrollToBottom();
        });
    }

    /**
     * Efface toute la conversation.
     */
    public void clear() {
        Platform.runLater(() -> messagesContainer.getChildren().clear());
    }

    /**
     * Fait défiler jusqu'en bas, seulement si l'ascenseur est déjà en position basse.
     * Si l'utilisateur a fait défiler vers le haut, sa position est préservée.
     */
    public void scrollToBottom() {
        Platform.runLater(() -> {
            double vvalue = scrollPane.getVvalue();
            double max    = scrollPane.getVmax();
            // On auto-scroll uniquement si l'ascenseur est au bas (ou très proche)
            if (max <= 0 || vvalue >= max - 0.01) {
                scrollPane.setVvalue(max);
            }
        });
    }

    /**
     * Définit le callback pour l'édition de message.
     *
     * @param onEdit Le callback
     */
    public void setOnEditMessage(Runnable onEdit) {
        this.onEditMessage = onEdit;
    }

    /**
     * Définit le callback pour l'insertion d'un message dans le document actif.
     *
     * @param onInsert Le callback recevant le contenu du message
     */
    public void setOnInsertToDocument(java.util.function.Consumer<String> onInsert) {
        this.onInsertToDocument = onInsert;
    }

    /**
     * Retourne l'index du message en cours d'édition.
     *
     * @return L'index ou -1
     */
    public int getEditingIndex() {
        return editingIndex;
    }

    /**
     * Exporte tous les messages en Markdown.
     *
     * @param owner La fenêtre parente
     */
    public void exportAll(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(getMessages().getString("llm.export.session"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown", "*.md"));
        chooser.setInitialFileName("conversation.md");

        File file = chooser.showSaveDialog(owner);
        if (file != null) {
            try {
                StringBuilder md = new StringBuilder();
                md.append("# LLM Conversation\n\n");
                md.append("*Exported on ").append(java.time.LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("*\n\n");
                md.append("---\n\n");

                for (var node : messagesContainer.getChildren()) {
                    MessageBlock mb = findMessageBlock(node);
                    if (mb != null) {
                        Object userData = mb.getUserData();
                        if (userData instanceof Message message) {
                            String role = message.getRole() == MessageRole.USER ? "**User**" : "**Assistant**";
                            md.append(role).append(" (")
                              .append(message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                              .append("):\n\n");
                            md.append(message.getContent()).append("\n\n---\n\n");
                        }
                    }
                }

                Files.writeString(file.toPath(), md.toString());
                log.info(LOG_SOURCE, "Session exported to: " + file.getAbsolutePath());
            } catch (IOException e) {
                log.error(LOG_SOURCE, "Export failed: " + e.getMessage());
            }
        }
    }

    /**
     * Retourne le nombre de messages.
     *
     * @return Le nombre de messages
     */
    public int getMessageCount() {
        return messagesContainer.getChildren().size();
    }

    // --- Private methods ---

    private HBox createMessageWrapper(Message message, int index, List<ContextLink> contextLinks) {
        MessageBlock block = new MessageBlock(message, contextLinks);
        block.setOnCopy(() -> copyToClipboard(message.getContent()));
        block.setOnExport(() -> exportMessage(message, getScene().getWindow()));
        block.setOnInsert(() -> { if (onInsertToDocument != null) onInsertToDocument.accept(message.getContent()); });
        block.setOnEdit(() -> {
            editingIndex = index;
            if (onEditMessage != null) {
                onEditMessage.run();
            }
        });
        HBox wrapper = new HBox();
        wrapper.getStyleClass().add("message-wrapper");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        if (message.getRole() == MessageRole.USER) {
            // Questions de l'utilisateur : alignées à gauche
            wrapper.getChildren().addAll(block, spacer);
        } else {
            // Réponses de l'assistant : alignées à droite
            wrapper.getChildren().addAll(spacer, block);
        }
        return wrapper;
    }

    private MessageBlock findMessageBlock(Object node) {
        if (node instanceof MessageBlock mb) return mb;
        if (node instanceof HBox hbox) {
            for (var child : hbox.getChildren()) {
                if (child instanceof MessageBlock mb) return mb;
            }
        }
        return null;
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void exportMessage(Message message, Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(getMessages().getString("llm.export.message"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown", "*.md"));
        chooser.setInitialFileName("message.md");

        File file = chooser.showSaveDialog(owner);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), message.getContent());
            } catch (IOException e) {
                log.error(LOG_SOURCE, "Export failed: " + e.getMessage());
            }
        }
    }

    /**
     * Bloc représentant un message dans la conversation.
     */
    public static class MessageBlock extends VBox {

        private final Label contentLabel;
        private Runnable onCopy;
        private Runnable onExport;
        private Runnable onEdit;
        private Runnable onInsert;

        public MessageBlock(Message message) {
            this(message, List.of());
        }

        public MessageBlock(Message message, List<ContextLink> contextLinks) {
            setSpacing(4);
            setPadding(new Insets(8));
            getStyleClass().add("conversation-message");
            getStyleClass().add(message.getRole() == MessageRole.USER ? "user" : "assistant");

            // Header avec rôle et timestamp
            HBox header = new HBox(5);
            header.setAlignment(Pos.CENTER_LEFT);
            
            Label roleLabel = new Label(message.getRole() == MessageRole.USER ? "You" : "Assistant");
            roleLabel.getStyleClass().add("message-role");
            
            Label timeLabel = new Label(message.getTimestamp()
                    .format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLabel.getStyleClass().add("message-time");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Boutons d'action
            Button copyBtn = new Button("\u2398"); // ⎘
            copyBtn.getStyleClass().add("message-action-button");
            copyBtn.setTooltip(new Tooltip("Copy"));
            copyBtn.setOnAction(e -> { if (onCopy != null) onCopy.run(); });

            Button exportBtn = new Button("\u2913"); // ⤓
            exportBtn.getStyleClass().add("message-action-button");
            exportBtn.setTooltip(new Tooltip("Export"));
            exportBtn.setOnAction(e -> { if (onExport != null) onExport.run(); });

            Button insertBtn = new Button("\u2937"); // ↷ insérer dans le document
            insertBtn.getStyleClass().add("message-action-button");
            insertBtn.setTooltip(new Tooltip(getMessages().getString("llm.insert.document")));
            insertBtn.setOnAction(e -> { if (onInsert != null) onInsert.run(); });

            header.getChildren().addAll(roleLabel, timeLabel, spacer, copyBtn, exportBtn, insertBtn);

            // Ajouter bouton édition seulement pour les messages user
            if (message.getRole() == MessageRole.USER) {
                Button editBtn = new Button("\u270E"); // ✎
                editBtn.getStyleClass().add("message-action-button");
                editBtn.setTooltip(new Tooltip("Edit"));
                editBtn.setOnAction(e -> { if (onEdit != null) onEdit.run(); });
                header.getChildren().add(editBtn);
            }

            getChildren().add(header);

            // Barre de liens vers les documents de contexte (si présents)
            if (!contextLinks.isEmpty()) {
                FlowPane linksPane = new FlowPane(6, 4);
                linksPane.getStyleClass().add("context-docs-bar");
                for (ContextLink link : contextLinks) {
                    Hyperlink hl = new Hyperlink("\uD83D\uDCC4 " + link.name());
                    hl.getStyleClass().add("context-doc-link");
                    hl.setOnAction(e -> { if (link.action() != null) link.action().run(); });
                    linksPane.getChildren().add(hl);
                }
                getChildren().add(linksPane);
            }

            // Contenu du message
            contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add("message-content");

            getChildren().add(contentLabel);
            setUserData(message);
        }

        public void updateContent(String content) {
            contentLabel.setText(content);
        }

        public void setOnCopy(Runnable action) { this.onCopy = action; }
        public void setOnExport(Runnable action) { this.onExport = action; }
        public void setOnEdit(Runnable action) { this.onEdit = action; }
        public void setOnInsert(Runnable action) { this.onInsert = action; }
    }
}
