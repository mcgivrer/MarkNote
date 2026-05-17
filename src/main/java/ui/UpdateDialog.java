package ui;

import java.util.ResourceBundle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import services.UpdateChecker.VersionInfo;

public class UpdateDialog {

    public enum UpdateResult { UPDATE, DECLINE, SKIP }

    private final Stage dialog;
    private UpdateResult result = UpdateResult.DECLINE;

    public UpdateDialog(ResourceBundle messages, Window owner, VersionInfo info, String currentVersion) {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle(messages.getString("update.available.title"));
        dialog.setResizable(false);

        Label headerLabel = new Label(
                messages.getString("update.available.header").replace("{0}", info.tagName()));
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label bodyLabel = new Label(
                messages.getString("update.available.content").replace("{0}", currentVersion));

        CheckBox skipBox = new CheckBox(messages.getString("update.skip"));

        Button downloadBtn = new Button(messages.getString("update.download"));
        downloadBtn.setDefaultButton(true);
        downloadBtn.setOnAction(e -> {
            result = UpdateResult.UPDATE;
            dialog.close();
        });

        Button declineBtn = new Button(messages.getString("update.decline"));
        declineBtn.setOnAction(e -> {
            result = skipBox.isSelected() ? UpdateResult.SKIP : UpdateResult.DECLINE;
            dialog.close();
        });

        HBox buttons = new HBox(10, downloadBtn, declineBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, headerLabel, bodyLabel, skipBox, buttons);
        root.setPadding(new Insets(20));
        root.setPrefWidth(420);

        dialog.setScene(new Scene(root));
    }

    public UpdateResult showAndGet() {
        dialog.showAndWait();
        return result;
    }
}
