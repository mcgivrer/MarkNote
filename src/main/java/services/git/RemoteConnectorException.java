package services.git;

/**
 * Exception levée lors d'une erreur avec un connecteur distant (GitHub, GitLab, Gitea).
 * 
 * <p>Cette exception encapsule les erreurs HTTP et les erreurs API spécifiques,
 * fournissant un code de statut HTTP et un message d'erreur descriptif que l'UI
 * peut afficher à l'utilisateur.</p>
 */
public class RemoteConnectorException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String apiMessage;

    /**
     * Construit une exception avec un message et une cause.
     *
     * @param message    Le message d'erreur
     * @param cause      La cause originale (peut être null)
     */
    public RemoteConnectorException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.apiMessage = null;
    }

    /**
     * Construit une exception avec un code HTTP, un message d'erreur de l'API et une cause.
     *
     * @param statusCode Le code de statut HTTP (401, 403, 404, 429, etc.)
     * @param apiMessage Le message d'erreur retourné par l'API
     * @param cause      La cause originale (peut être null)
     */
    public RemoteConnectorException(int statusCode, String apiMessage, Throwable cause) {
        super(buildMessage(statusCode, apiMessage), cause);
        this.statusCode = statusCode;
        this.apiMessage = apiMessage;
    }

    /**
     * Construit une exception avec un code HTTP et un message d'erreur de l'API.
     *
     * @param statusCode Le code de statut HTTP
     * @param apiMessage Le message d'erreur retourné par l'API
     */
    public RemoteConnectorException(int statusCode, String apiMessage) {
        this(statusCode, apiMessage, null);
    }

    /**
     * Obtient le code de statut HTTP associé à l'erreur.
     *
     * @return Le code HTTP (0 si non applicable)
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Obtient le message d'erreur retourné par l'API.
     *
     * @return Le message de l'API (null si non disponible)
     */
    public String getApiMessage() {
        return apiMessage;
    }

    /**
     * Construit un message d'erreur formaté à partir du code HTTP et du message API.
     */
    private static String buildMessage(int statusCode, String apiMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Erreur API ");
        
        switch (statusCode) {
            case 401:
                sb.append("(401 Unauthorized): Authentification invalide");
                break;
            case 403:
                sb.append("(403 Forbidden): Accès refusé");
                break;
            case 404:
                sb.append("(404 Not Found): Ressource non trouvée");
                break;
            case 429:
                sb.append("(429 Too Many Requests): Limite de taux dépassée");
                break;
            case 500:
            case 502:
            case 503:
                sb.append("(").append(statusCode).append("): Erreur serveur");
                break;
            default:
                sb.append("(").append(statusCode).append(")");
        }
        
        if (apiMessage != null && !apiMessage.isBlank()) {
            sb.append(" - ").append(apiMessage);
        }
        
        return sb.toString();
    }
}
