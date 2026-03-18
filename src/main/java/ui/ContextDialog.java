package ui;

import java.util.Locale;
import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Dialogue pour définir le contexte système LLM.
 */
public class ContextDialog extends Dialog<String> {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final TextArea contextTextArea;

    /**
     * Crée un nouveau dialogue de contexte.
     *
     * @param owner Le propriétaire de la fenêtre
     */
    public ContextDialog(Window owner) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle(getMessages().getString("llm.context.title"));
        setHeaderText(getMessages().getString("llm.context.header"));
        setResizable(true);

        // Zone de texte pour le contexte
        Label label = new Label(getMessages().getString("llm.context.label"));
        
        contextTextArea = new TextArea();
        contextTextArea.setPromptText(getMessages().getString("llm.context.placeholder"));
        contextTextArea.setWrapText(true);
        contextTextArea.setPrefRowCount(10);
        contextTextArea.setPrefColumnCount(50);
        VBox.setVgrow(contextTextArea, Priority.ALWAYS);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(label, contextTextArea);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(500);
        getDialogPane().setPrefHeight(350);

        // Convertir le résultat
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return contextTextArea.getText();
            }
            return null;
        });
    }

    /**
     * Récupère le contexte actuel.
     *
     * @return Le texte du contexte
     */
    public String getContext() {
        return contextTextArea.getText();
    }

    /**
     * Définit le contexte initial.
     *
     * @param context Le contexte à afficher
     */
    public void setContext(String context) {
        contextTextArea.setText(context != null ? context : "");
    }
}
