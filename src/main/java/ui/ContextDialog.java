package ui;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import config.LLMConfig;
import services.LLMService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Dialogue pour configurer le modèle LLM actif et le contexte système.
 */
public class ContextDialog extends Dialog<String> {

    private static ResourceBundle getMessages() {
        return ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
    }

    private final TextArea contextTextArea;
    private final ComboBox<String> modelCombo;

    /**
     * Crée un nouveau dialogue de contexte et de sélection de modèle.
     *
     * @param owner     Le propriétaire de la fenêtre
     * @param llmConfig La configuration LLM courante
     */
    public ContextDialog(Window owner, LLMConfig llmConfig) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        var msgs = getMessages();
        setTitle(msgs.getString("llm.context.title"));
        setHeaderText(msgs.getString("llm.context.header"));
        setResizable(true);

        // --- Section modèle ---
        Label modelLabel = new Label(msgs.getString("llm.config.model"));

        modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.setValue(llmConfig.getModel());
        modelCombo.setPrefWidth(220);
        GridPane.setHgrow(modelCombo, Priority.ALWAYS);

        Button refreshBtn = new Button(msgs.getString("llm.config.refresh"));
        refreshBtn.setOnAction(e -> {
            LLMConfig tempConfig = new LLMConfig();
            tempConfig.setEndpointUrl(llmConfig.getEndpointUrl());
            tempConfig.setApiKey(llmConfig.getApiKey());
            LLMService tempService = new LLMService(tempConfig);
            List<String> models = tempService.getAvailableModels();
            if (!models.isEmpty()) {
                modelCombo.getItems().setAll(models);
                if (!models.contains(modelCombo.getValue())) {
                    modelCombo.setValue(models.get(0));
                }
            }
        });

        Label endpointInfo = new Label(msgs.getString("llm.welcome.endpoint") + " : " + llmConfig.getEndpointUrl());
        endpointInfo.setStyle("-fx-text-fill: #6a7a90; -fx-font-size: 11px;");

        GridPane modelGrid = new GridPane();
        modelGrid.setHgap(8);
        modelGrid.setVgap(6);
        modelGrid.add(modelLabel, 0, 0);
        modelGrid.add(modelCombo, 1, 0);
        modelGrid.add(refreshBtn, 2, 0);
        modelGrid.add(endpointInfo, 0, 1, 3, 1);

        // --- Section contexte ---
        Label contextLabel = new Label(msgs.getString("llm.context.label"));

        contextTextArea = new TextArea();
        contextTextArea.setPromptText(msgs.getString("llm.context.placeholder"));
        contextTextArea.setWrapText(true);
        contextTextArea.setPrefRowCount(8);
        contextTextArea.setPrefColumnCount(50);
        VBox.setVgrow(contextTextArea, Priority.ALWAYS);

        VBox content = new VBox(10, modelGrid, new Separator(), contextLabel, contextTextArea);
        content.setPadding(new Insets(10));

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(520);
        getDialogPane().setPrefHeight(430);

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return contextTextArea.getText();
            }
            return null;
        });
    }

    /**
     * Récupère le contexte système saisi.
     */
    public String getContext() {
        return contextTextArea.getText();
    }

    /**
     * Définit le contexte système initial.
     */
    public void setContext(String context) {
        contextTextArea.setText(context != null ? context : "");
    }

    /**
     * Retourne le modèle sélectionné dans la ComboBox.
     */
    public String getSelectedModel() {
        String val = modelCombo.getValue();
        return val != null ? val.trim() : "";
    }
}
