/**
 * Classe de lancement pour MarkNote.
 * Nécessaire car JavaFX Application ne peut pas être lancée directement
 * dans certains environnements (VS Code, etc.).
 */
public class Main {
    public static void main(String[] args) {
        // Keep network preference consistent across all launch modes.
        System.setProperty("java.net.preferIPv6Addresses", "true");
        MarkNote.main(args);
    }
}
