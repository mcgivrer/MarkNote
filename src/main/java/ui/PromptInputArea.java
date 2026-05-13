package ui;

import java.util.LinkedHashMap;
 import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button; 
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Zone de saisie de prompt LLM avec boutons d'action.
 */
public class PromptInputArea extends VBox {

    private static final double ACTION_BUTTON_WIDTH = 38;
    private static final double ACTION_BUTTON_HEIGHT = 32;

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final TextArea textArea;
    private final Button submitButton;
    private final Button cancelButton;
    private final Button contextButton;
    private final ProgressIndicator spinner;
    private boolean isProcessing = false;

    /** Barre de sélection des documents ouverts (au-dessus du TextArea). */
    private final FlowPane docContextBar;
    /** filename → bouton Toggle (true = sélectionné). */
    private final Map<String, Button> docButtons = new LinkedHashMap<>();

    private Consumer<String> onSubmit;
    private Runnable onCancel;
    private Runnable onContextClick;

    /**
     * Crée une nouvelle zone de saisie de prompt.
     */
    public PromptInputArea() {
        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().add("prompt-input-area");

        // Barre de sélection des documents ouverts
        docContextBar = new FlowPane();
        docContextBar.setHgap(4);
        docContextBar.setVgap(4);
        docContextBar.setPadding(new Insets(0, 0, 2, 0));
        docContextBar.getStyleClass().add("doc-context-bar");
        docContextBar.setVisible(false);
        docContextBar.setManaged(false);

        // Zone de texte
        textArea = new TextArea();
        textArea.setPromptText(getMessages().getString("llm.prompt.placeholder"));
        textArea.setWrapText(true);
        textArea.setPrefRowCount(3);
        textArea.getStyleClass().add("prompt-textarea");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Raccourci clavier Ctrl+Enter
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
                if (!isProcessing) {
                    submit();
                }
                event.consume();
            }
        });

        // Boutons d'action
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.BOTTOM_RIGHT);
        buttonBar.getStyleClass().add("prompt-button-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionButtons = new HBox(8);
        actionButtons.setAlignment(Pos.BOTTOM_RIGHT);
        actionButtons.getStyleClass().add("prompt-action-buttons");

        contextButton = new Button("\u2699"); // ⚙ roue/contexte
        contextButton.getStyleClass().add("prompt-button");
        contextButton.setTooltip(new Tooltip(getMessages().getString("llm.context.button")));
        applyActionButtonSize(contextButton);
        contextButton.setOnAction(e -> {
            if (onContextClick != null) onContextClick.run();
        });

        spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);
        spinner.getStyleClass().add("prompt-spinner");

        submitButton = new Button("\u25B6"); // ▶ envoyer
        submitButton.getStyleClass().addAll("prompt-button", "submit-button");
        applyActionButtonSize(submitButton);
        submitButton.setDefaultButton(true);
        submitButton.setOnAction(e -> submit());

        cancelButton = new Button("\u2715"); // ✕ annuler
        cancelButton.getStyleClass().addAll("prompt-button", "cancel-button");
        applyActionButtonSize(cancelButton);
        cancelButton.setTooltip(new Tooltip(getMessages().getString("llm.cancel")));
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        cancelButton.setOnAction(e -> {
            if (onCancel != null) onCancel.run();
        });

        actionButtons.getChildren().addAll(contextButton, cancelButton, submitButton);
        buttonBar.getChildren().addAll(spacer, spinner, actionButtons);

        getChildren().addAll(docContextBar, textArea, buttonBar);
    }

    /**
     * Met à jour la barre de documents ouverts.
     * Crée un bouton compact par fichier.
     *
     * @param fileNames liste ordonnée des noms de fichiers ouverts
     */
    public void updateDocumentTabs(List<String> fileNames) {
        // Conserver l'état de sélection des boutons existants
        Map<String, Boolean> previousState = new LinkedHashMap<>();
        docButtons.forEach((name, btn) -> previousState.put(name, isDocSelected(btn)));

        docContextBar.getChildren().clear();
        docButtons.clear();

        for (String name : fileNames) {
            boolean wasSelected = previousState.getOrDefault(name, false);
            Button btn = buildDocButton(name, wasSelected);
            docButtons.put(name, btn);
            docContextBar.getChildren().add(btn);
        }

        boolean hasItems = !fileNames.isEmpty();
        docContextBar.setVisible(hasItems);
        docContextBar.setManaged(hasItems);
    }

    /**
     * Retourne la liste des noms de documents dont le bouton est activé.
     *
     * @return liste des fichiers sélectionnés comme contexte
     */
    public List<String> getSelectedDocuments() {
        return docButtons.entrySet().stream()
                .filter(e -> isDocSelected(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Retourne le texte du prompt.
     *
     * @return Le texte saisi
     */
    public String getText() {
        return textArea.getText();
    }

    /**
     * Définit le texte du prompt.
     *
     * @param text Le texte à afficher
     */
    public void setText(String text) {
        textArea.setText(text);
    }

    /**
     * Efface le texte.
     */
    public void clear() {
        textArea.clear();
    }

    /**
     * Définit l'état de traitement.
     *
     * @param processing true si en cours de traitement
     */
    public void setProcessing(boolean processing) {
        this.isProcessing = processing;
        spinner.setVisible(processing);
        spinner.setManaged(processing);
        submitButton.setVisible(!processing);
        submitButton.setManaged(!processing);
        cancelButton.setVisible(processing);
        cancelButton.setManaged(processing);
        textArea.setDisable(processing);
        contextButton.setDisable(processing);
    }

    /**
     * Vérifie si un traitement est en cours.
     *
     * @return true si en cours de traitement
     */
    public boolean isProcessing() {
        return isProcessing;
    }

    /**
     * Définit le callback de soumission.
     *
     * @param callback Le callback recevant le texte du prompt
     */
    public void setOnSubmit(Consumer<String> callback) {
        this.onSubmit = callback;
    }

    /**
     * Définit le callback d'annulation.
     *
     * @param callback Le callback
     */
    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    /**
     * Définit le callback du bouton contexte.
     *
     * @param callback Le callback
     */
    public void setOnContextClick(Runnable callback) {
        this.onContextClick = callback;
    }

    /**
     * Donne le focus à la zone de texte.
     */
    public void focusInput() {
        textArea.requestFocus();
    }

    // --- Private methods ---

    private void submit() {
        String text = textArea.getText();
        if (text != null && !text.isBlank() && onSubmit != null) {
            onSubmit.accept(text);
        }
    }

    private void applyActionButtonSize(Button button) {
        button.setMinWidth(ACTION_BUTTON_WIDTH);
        button.setPrefWidth(ACTION_BUTTON_WIDTH);
        button.setMaxWidth(ACTION_BUTTON_WIDTH);
        button.setMinHeight(ACTION_BUTTON_HEIGHT);
        button.setPrefHeight(ACTION_BUTTON_HEIGHT);
    }

    /**
     * Construit un bouton de sélection de document.
     * Style compact : 8pt, fond clair, tour pointillé (inactif) ou plein (actif).
     */
    private Button buildDocButton(String fileName, boolean selected) {
        Button btn = new Button();
        btn.getStyleClass().add("doc-context-btn");
        btn.setUserData(selected);
        updateDocButtonAppearance(btn, fileName, selected);
        btn.setTooltip(new Tooltip(fileName));
        btn.setOnAction(e -> {
            boolean nowSelected = !isDocSelected(btn);
            btn.setUserData(nowSelected);
            updateDocButtonAppearance(btn, fileName, nowSelected);
        });
        return btn;
    }

    private void updateDocButtonAppearance(Button btn, String fileName, boolean selected) {
        // Icône gauche + nom fichier
        String icon = selected ? "\u2713" : "+"; // ✓ ou +
        btn.setText("[" + icon + "] " + fileName);
        if (selected) {
            btn.getStyleClass().remove("doc-context-btn-off");
            btn.getStyleClass().add("doc-context-btn-on");
        } else {
            btn.getStyleClass().remove("doc-context-btn-on");
            btn.getStyleClass().add("doc-context-btn-off");
        }
    }

    private boolean isDocSelected(Button btn) {
        Object data = btn.getUserData();
        return Boolean.TRUE.equals(data);
    }
}
