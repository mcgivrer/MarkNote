package ui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import utils.GitService;
import utils.GitService.StagedFile;

/**
 * Dialogue modal pour créer un commit git.
 * <p>
 * Affiche :
 * <ul>
 *   <li>Une {@link TextArea} pour saisir le message de commit.</li>
 *   <li>Une {@link ListView} en lecture seule listant les fichiers stagés.</li>
 *   <li>Un bouton "Stager tout" pour appeler {@link GitService#addAll()} avant commit.</li>
 * </ul>
 * Retourne le message de commit via {@link #showAndWait()}, ou {@link Optional#empty()}
 * si l'utilisateur annule.
 */
public class CommitDialog {

    private final Stage dialog;
    private final GitService gitService;
    private final ResourceBundle messages;

    private final TextArea messageArea;
    private final ListView<String> stagedList;
    private final Button confirmBtn;

    private String result = null;

    /**
     * @param owner      fenêtre propriétaire
     * @param gitService service git courant
     */
    public CommitDialog(Window owner, GitService gitService) {
        this.gitService = gitService;
        this.messages   = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());

        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle(messages.getString("git.commit.dialog.title"));
        dialog.setResizable(true);

        // --- Message area ---
        messageArea = new TextArea();
        messageArea.setPromptText(messages.getString("git.commit.message.prompt"));
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(4);
        messageArea.setMinHeight(80);

        // --- Staged files list ---
        Label stagedLabel = new Label(messages.getString("git.commit.staged.files"));
        stagedList = new ListView<>(FXCollections.observableArrayList());
        stagedList.setEditable(false);
        stagedList.setPrefHeight(160);
        stagedList.setMinHeight(80);

        // --- Buttons ---
        Button stageAllBtn = new Button(messages.getString("git.commit.stage.all"));
        stageAllBtn.setOnAction(e -> handleStageAll());

        confirmBtn = new Button(messages.getString("git.commit.confirm"));
        confirmBtn.setDefaultButton(true);
        confirmBtn.setDisable(true);
        confirmBtn.setOnAction(e -> handleConfirm());

        Button cancelBtn = new Button(messages.getString("cancel") != null
                ? safeKey("cancel", "Annuler")
                : "Annuler");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> dialog.close());

        // Enable confirm only when message is non-blank AND staged list non-empty
        messageArea.textProperty().addListener((obs, oldVal, newVal) -> updateConfirmState());

        HBox buttonBar = new HBox(8, stageAllBtn, new Spacer(), cancelBtn, confirmBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        // --- Layout ---
        VBox root = new VBox(10,
                new Label(messages.getString("git.commit.message.prompt") != null
                        ? safeKey("git.commit.message.prompt", "Message :") : "Message :"),
                messageArea,
                stagedLabel,
                stagedList,
                buttonBar);
        root.setPadding(new Insets(16));
        root.setPrefWidth(480);

        dialog.setScene(new Scene(root));

        // Populate staged files on open
        refreshStagedList();
    }

    /**
     * Affiche le dialogue et attend la fermeture.
     *
     * @return le message de commit si confirmé, {@link Optional#empty()} si annulé.
     */
    public Optional<String> showAndWait() {
        dialog.showAndWait();
        return Optional.ofNullable(result);
    }

    // -------------------------------------------------------------------------
    // Implémentation privée
    // -------------------------------------------------------------------------

    private void handleStageAll() {
        Thread t = new Thread(() -> {
            try {
                gitService.addAll();
                Platform.runLater(this::refreshStagedList);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.initOwner(dialog);
                    alert.setHeaderText(null);
                    alert.setContentText("git add: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }, "commit-dialog-stage-all");
        t.setDaemon(true);
        t.start();
    }

    private void handleConfirm() {
        String msg = messageArea.getText().strip();
        if (msg.isBlank()) {
            showWarning(messages.getString("git.commit.empty.message"));
            return;
        }
        if (stagedList.getItems().isEmpty()) {
            showWarning(messages.getString("git.commit.nothing.staged"));
            return;
        }
        result = msg;
        dialog.close();
    }

    private void refreshStagedList() {
        List<StagedFile> staged = gitService.listStagedFiles();
        stagedList.setItems(FXCollections.observableArrayList(
                staged.stream().map(StagedFile::toString).toList()));
        updateConfirmState();
    }

    private void updateConfirmState() {
        boolean hasMessage = !messageArea.getText().strip().isBlank();
        boolean hasStaged  = !stagedList.getItems().isEmpty();
        confirmBtn.setDisable(!hasMessage || !hasStaged);
    }

    private void showWarning(String text) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(dialog);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }

    /** Returns the i18n value for key, or fallback if key is missing. */
    private String safeKey(String key, String fallback) {
        try { return messages.getString(key); } catch (Exception e) { return fallback; }
    }

    // Simple spacer for button bar alignment
    private static class Spacer extends Region {
        Spacer() { HBox.setHgrow(this, Priority.ALWAYS); }
    }
}
