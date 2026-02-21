package ui;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import utils.IndexService;
import utils.IndexService.SearchResult;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

/**
 * Composant de recherche intégré dans la barre de menu.
 * <p>
 * Contient un champ de texte de recherche et un popup affichant
 * les résultats correspondants dans l'index du projet.
 * </p>
 */
public class SearchBox extends HBox {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");
    private static final int MAX_RESULTS = 20;

    private final TextField searchField;
    private final Popup resultsPopup;
    private final ListView<SearchResult> resultsList;
    private IndexService indexService;
    private Consumer<File> onFileSelected;

    public SearchBox() {
        setAlignment(Pos.CENTER_RIGHT);
        setPadding(new Insets(2, 8, 2, 8));
        setSpacing(4);

        // Search icon label
        Label searchIcon = new Label("\uD83D\uDD0D"); // 🔍
        searchIcon.setStyle("-fx-font-size: 14px;");

        // Search text field
        searchField = new TextField();
        searchField.setPromptText(bundle.getString("search.prompt"));
        searchField.setPrefWidth(220);
        searchField.setMaxWidth(280);
        searchField.getStyleClass().add("search-field");

        getChildren().addAll(searchIcon, searchField);

        // Results popup
        resultsPopup = new Popup();
        resultsPopup.setAutoHide(true);
        resultsPopup.setHideOnEscape(true);

        resultsList = new ListView<>();
        resultsList.setPrefWidth(400);
        resultsList.setPrefHeight(300);
        resultsList.setMaxHeight(400);
        resultsList.getStyleClass().add("search-results-list");
        resultsList.setPlaceholder(new Label(bundle.getString("search.noResults")));

        // Custom cell factory for rich display
        resultsList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    VBox cellBox = new VBox(2);
                    cellBox.setPadding(new Insets(4, 8, 4, 8));

                    Label titleLabel = new Label(item.getEntry().getDisplayTitle());
                    titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                    Label matchLabel = new Label(item.getMatchText());
                    matchLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

                    Label pathLabel = new Label(item.getEntry().getRelativePath());
                    pathLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 10px; -fx-font-style: italic;");

                    cellBox.getChildren().addAll(titleLabel, matchLabel, pathLabel);
                    setGraphic(cellBox);
                    setText(null);
                }
            }
        });

        // Select result on click or Enter
        resultsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                selectResult();
            }
        });

        resultsList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                selectResult();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                resultsPopup.hide();
                searchField.requestFocus();
            }
        });

        VBox popupContent = new VBox(resultsList);
        popupContent.setStyle(
            "-fx-background-color: -fx-background; " +
            "-fx-border-color: -fx-box-border; " +
            "-fx-border-width: 1; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 4);"
        );
        resultsPopup.getContent().add(popupContent);

        // Live search on text change
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                resultsPopup.hide();
            } else {
                performSearch(newVal.trim());
            }
        });

        // Navigate to results with Down arrow
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && resultsPopup.isShowing()) {
                resultsList.requestFocus();
                if (!resultsList.getItems().isEmpty()) {
                    resultsList.getSelectionModel().selectFirst();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                resultsPopup.hide();
                searchField.clear();
            } else if (e.getCode() == KeyCode.ENTER && resultsPopup.isShowing()) {
                if (!resultsList.getItems().isEmpty()) {
                    resultsList.getSelectionModel().selectFirst();
                    selectResult();
                }
            }
        });
    }

    /**
     * Définit le service d'index à utiliser pour les recherches.
     */
    public void setIndexService(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * Définit l'action à exécuter quand un fichier est sélectionné dans les résultats.
     */
    public void setOnFileSelected(Consumer<File> action) {
        this.onFileSelected = action;
    }

    /**
     * Met le texte du champ de recherche (utile pour rechercher un tag depuis le tag cloud).
     */
    public void setSearchText(String text) {
        searchField.setText(text);
        searchField.requestFocus();
        searchField.positionCaret(text.length());
    }

    /**
     * Effectue la recherche et affiche les résultats dans le popup.
     */
    private void performSearch(String query) {
        if (indexService == null) return;

        List<SearchResult> results = indexService.search(query);
        resultsList.getItems().clear();

        int limit = Math.min(results.size(), MAX_RESULTS);
        for (int i = 0; i < limit; i++) {
            resultsList.getItems().add(results.get(i));
        }

        if (!results.isEmpty()) {
            showPopup();
        } else {
            // Still show with "no results" placeholder
            showPopup();
        }
    }

    /**
     * Affiche le popup de résultats sous le champ de recherche.
     */
    private void showPopup() {
        Window window = getScene() != null ? getScene().getWindow() : null;
        if (window == null) return;

        Bounds bounds = searchField.localToScreen(searchField.getBoundsInLocal());
        if (bounds == null) return;

        resultsPopup.show(window,
            bounds.getMinX(),
            bounds.getMaxY() + 2
        );
    }

    /**
     * Sélectionne le résultat courant et ouvre le fichier correspondant.
     */
    private void selectResult() {
        SearchResult selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected != null && indexService != null && onFileSelected != null) {
            File file = indexService.resolveFile(selected.getEntry());
            if (file != null && file.exists()) {
                resultsPopup.hide();
                searchField.clear();
                onFileSelected.accept(file);
            }
        }
    }
}
