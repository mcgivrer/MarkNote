package utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Service de logging centralisé pour MarkNote.
 * <p>
 * Les sorties console ne sont actives que si l'argument {@code --console-debug}
 * est passé au démarrage de l'application. Cette classe est un singleton.
 * </p>
 */
public class LogService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static LogService instance;

    private boolean consoleDebugEnabled = false;

    private LogService() {
        // Singleton
    }

    /**
     * Retourne l'instance unique du service de logging.
     */
    public static synchronized LogService getInstance() {
        if (instance == null) {
            instance = new LogService();
        }
        return instance;
    }

    /**
     * Active ou désactive le mode debug console.
     * Doit être appelé au démarrage avec la valeur de l'argument --console-debug.
     *
     * @param enabled true si --console-debug est présent
     */
    public void setConsoleDebugEnabled(boolean enabled) {
        this.consoleDebugEnabled = enabled;
        if (enabled) {
            System.out.println("[LOG] Console debug mode enabled");
        }
    }

    /**
     * @return true si le mode debug console est activé
     */
    public boolean isConsoleDebugEnabled() {
        return consoleDebugEnabled;
    }

    /**
     * Log un message d'information.
     *
     * @param source Classe ou composant source
     * @param message Message à logger
     */
    public void info(String source, String message) {
        log("INFO", source, message);
    }

    /**
     * Log un message d'avertissement.
     *
     * @param source Classe ou composant source
     * @param message Message à logger
     */
    public void warn(String source, String message) {
        log("WARN", source, message);
    }

    /**
     * Log un message d'erreur.
     *
     * @param source Classe ou composant source
     * @param message Message à logger
     */
    public void error(String source, String message) {
        logError("ERROR", source, message);
    }

    /**
     * Log un message d'erreur avec exception.
     *
     * @param source Classe ou composant source
     * @param message Message à logger
     * @param throwable Exception associée
     */
    public void error(String source, String message, Throwable throwable) {
        if (!consoleDebugEnabled) return;

        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        System.err.printf("[%s] [%s] [%s] %s%n", timestamp, "ERROR", source, message);
        throwable.printStackTrace(System.err);
    }

    /**
     * Log un message de debug (plus verbeux).
     *
     * @param source Classe ou composant source
     * @param message Message à logger
     */
    public void debug(String source, String message) {
        log("DEBUG", source, message);
    }

    /**
     * Log le début d'une opération.
     *
     * @param source Classe ou composant source
     * @param operation Nom de l'opération
     */
    public void startOperation(String source, String operation) {
        log("START", source, ">>> " + operation);
    }

    /**
     * Log la fin d'une opération.
     *
     * @param source Classe ou composant source
     * @param operation Nom de l'opération
     */
    public void endOperation(String source, String operation) {
        log("END", source, "<<< " + operation);
    }

    /**
     * Log la fin d'une opération avec un résultat.
     *
     * @param source Classe ou composant source
     * @param operation Nom de l'opération
     * @param result Résultat de l'opération
     */
    public void endOperation(String source, String operation, String result) {
        log("END", source, "<<< " + operation + " : " + result);
    }

    private void log(String level, String source, String message) {
        if (!consoleDebugEnabled) return;

        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        System.out.printf("[%s] [%s] [%s] %s%n", timestamp, level, source, message);
    }

    private void logError(String level, String source, String message) {
        if (!consoleDebugEnabled) return;

        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        System.err.printf("[%s] [%s] [%s] %s%n", timestamp, level, source, message);
    }
}
