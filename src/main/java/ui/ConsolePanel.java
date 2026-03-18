package ui;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;

/**
 * Panel affichant la sortie console Java (stdout et stderr).
 * <p>
 * Ce panel capture System.out et System.err et affiche les messages
 * dans une zone de texte avec horodatage. Il n'est visible que si
 * l'argument "--console-debug" est passé au démarrage de l'application.
 * </p>
 */
public class ConsolePanel extends BasePanel {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_LINES = 10000;

    private final TextArea consoleArea;
    private final ScrollPane scrollPane;
    private final PrintStream originalOut;
    private final PrintStream originalErr;
    private boolean capturing = false;

    public ConsolePanel() {
        super("console.title", "console.close.tooltip");

        // Sauvegarder les streams originaux
        originalOut = System.out;
        originalErr = System.err;

        // Zone de texte pour la console
        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.getStyleClass().add("console-area");
        consoleArea.setStyle("-fx-font-family: 'Monospace', 'Courier New', monospace; -fx-font-size: 12px;");

        scrollPane = new ScrollPane(consoleArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        setContent(scrollPane);
        setPrefHeight(150);
        setMinHeight(80);

        // Ajouter un bouton "Clear" dans le header
        Button clearButton = new Button("\u239A"); // ⎚ symbole clear
        clearButton.getStyleClass().add("panel-close-button");
        clearButton.setTooltip(new Tooltip(getMessages().getString("console.clear.tooltip")));
        clearButton.setOnAction(e -> clear());

        // Insérer le bouton clear avant les boutons detach et close
        getHeader().getChildren().add(2, clearButton);
    }

    /**
     * Démarre la capture des sorties console.
     */
    public void startCapture() {
        if (capturing) return;
        capturing = true;

        // Rediriger stdout
        System.setOut(new PrintStream(new ConsoleOutputStream("[OUT] ", originalOut), true));

        // Rediriger stderr
        System.setErr(new PrintStream(new ConsoleOutputStream("[ERR] ", originalErr), true));

        appendLine("[SYS] Console debug capture started");
    }

    /**
     * Arrête la capture et restaure les streams originaux.
     */
    public void stopCapture() {
        if (!capturing) return;
        capturing = false;

        System.setOut(originalOut);
        System.setErr(originalErr);

        appendLine("[SYS] Console debug capture stopped");
    }

    /**
     * Efface le contenu de la console.
     */
    public void clear() {
        Platform.runLater(() -> consoleArea.clear());
    }

    /**
     * Ajoute une ligne horodatée à la console.
     */
    private void appendLine(String line) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String formattedLine = "[" + timestamp + "] " + line + "\n";

        Platform.runLater(() -> {
            consoleArea.appendText(formattedLine);

            // Limiter le nombre de lignes pour éviter les problèmes de mémoire
            String text = consoleArea.getText();
            int lineCount = text.split("\n", -1).length;
            if (lineCount > MAX_LINES) {
                int cutIndex = text.indexOf('\n', text.length() / 4);
                if (cutIndex > 0) {
                    consoleArea.setText(text.substring(cutIndex + 1));
                }
            }

            // Auto-scroll vers le bas
            consoleArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * OutputStream personnalisé qui redirige vers le panel console.
     */
    private class ConsoleOutputStream extends OutputStream {
        private final String prefix;
        private final PrintStream originalStream;
        private final StringBuilder buffer = new StringBuilder();

        ConsoleOutputStream(String prefix, PrintStream originalStream) {
            this.prefix = prefix;
            this.originalStream = originalStream;
        }

        @Override
        public void write(int b) {
            char c = (char) b;

            // Écrire aussi vers le stream original
            originalStream.write(b);

            if (c == '\n') {
                appendLine(prefix + buffer.toString());
                buffer.setLength(0);
            } else if (c != '\r') {
                buffer.append(c);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }

        @Override
        public void flush() {
            originalStream.flush();
        }
    }
}
