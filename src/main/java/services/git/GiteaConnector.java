package services.git;

import utils.LogService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Connecteur pour l'API Gitea (instance self-hosted uniquement).
 * 
 * <p>Ce connecteur utilise l'API REST de Gitea v1 pour lister et créer des dépôts.
 * Il nécessite un Access Token avec les permissions appropriées.</p>
 * 
 * <p><b>Note :</b> Gitea n'a pas d'instance publique par défaut, toutes les instances
 * sont self-hosted. L'URL de base doit être fournie lors de la construction.</p>
 * 
 * <h2>Endpoints utilisés :</h2>
 * <ul>
 *   <li>GET /api/v1/user/repos - Liste les dépôts de l'utilisateur</li>
 *   <li>POST /api/v1/user/repos - Crée un nouveau dépôt</li>
 * </ul>
 * 
 * @see <a href="https://docs.gitea.io/en-us/api-usage/">Gitea API Documentation</a>
 */
public class GiteaConnector implements RemoteConnector {

    private static final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "GiteaConnector";

    private final String instanceUrl;
    private final String token;
    private final HttpClient httpClient;
    private final JSONParser jsonParser;

    /**
     * Construit un connecteur pour une instance Gitea.
     *
     * @param instanceUrl L'URL de base de l'instance Gitea (ex: https://gitea.example.com)
     * @param token       L'Access Token Gitea
     */
    public GiteaConnector(String instanceUrl, String token) {
        this.instanceUrl = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.jsonParser = new JSONParser();
    }

    @Override
    public String platform() {
        return "gitea";
    }

    @Override
    public List<RemoteRepo> listRepositories() throws RemoteConnectorException {
        log.debug(LOG_SOURCE, "Récupération de la liste des dépôts Gitea depuis " + instanceUrl);

        String url = instanceUrl + "/api/v1/user/repos?limit=100";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "token " + token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                handleErrorResponse(response);
            }

            return parseRepositories(response.body());

        } catch (IOException e) {
            log.error(LOG_SOURCE, "Erreur réseau lors de la récupération des dépôts: " + e.getMessage());
            throw new RemoteConnectorException("Erreur réseau: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(LOG_SOURCE, "Requête interrompue: " + e.getMessage());
            throw new RemoteConnectorException("Requête interrompue", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void createRepository(String name, boolean isPrivate) throws RemoteConnectorException {
        log.debug(LOG_SOURCE, "Création du dépôt Gitea: " + name + " (privé=" + isPrivate + ")");

        String url = instanceUrl + "/api/v1/user/repos";

        // Construction du body JSON
        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("private", isPrivate);
        body.put("auto_init", true); // Créer avec README initial
        body.put("default_branch", "main");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "token " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                handleErrorResponse(response);
            }

            log.info(LOG_SOURCE, "Dépôt créé avec succès: " + name);

        } catch (IOException e) {
            log.error(LOG_SOURCE, "Erreur réseau lors de la création du dépôt: " + e.getMessage());
            throw new RemoteConnectorException("Erreur réseau: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(LOG_SOURCE, "Requête interrompue: " + e.getMessage());
            throw new RemoteConnectorException("Requête interrompue", e);
        }
    }

    /**
     * Parse la réponse JSON pour extraire la liste des dépôts.
     */
    private List<RemoteRepo> parseRepositories(String jsonResponse) throws RemoteConnectorException {
        List<RemoteRepo> repos = new ArrayList<>();

        try {
            JSONArray jsonArray = (JSONArray) jsonParser.parse(jsonResponse);

            for (Object obj : jsonArray) {
                JSONObject repo = (JSONObject) obj;

                String name = (String) repo.get("name");
                String cloneUrl = (String) repo.get("clone_url");
                String description = (String) repo.get("description");
                Boolean isPrivate = (Boolean) repo.get("private");
                String defaultBranch = (String) repo.get("default_branch");

                repos.add(new RemoteRepo(
                        name,
                        cloneUrl,
                        description != null ? description : "",
                        isPrivate != null && isPrivate,
                        defaultBranch != null ? defaultBranch : "main"
                ));
            }

            log.debug(LOG_SOURCE, "Parsed " + repos.size() + " repositories");
            return repos;

        } catch (ParseException e) {
            log.error(LOG_SOURCE, "Erreur de parsing JSON: " + e.getMessage());
            throw new RemoteConnectorException("Réponse JSON invalide", e);
        }
    }

    /**
     * Gère les réponses d'erreur HTTP et lève une exception appropriée.
     */
    private void handleErrorResponse(HttpResponse<String> response) throws RemoteConnectorException {
        int statusCode = response.statusCode();
        String body = response.body();

        String errorMessage = extractErrorMessage(body);

        log.error(LOG_SOURCE, "Erreur API Gitea " + statusCode + ": " + errorMessage);
        throw new RemoteConnectorException(statusCode, errorMessage);
    }

    /**
     * Extrait le message d'erreur du JSON de réponse Gitea.
     */
    private String extractErrorMessage(String jsonResponse) {
        try {
            JSONObject json = (JSONObject) jsonParser.parse(jsonResponse);
            String message = (String) json.get("message");
            return message != null ? message : "Erreur inconnue";
        } catch (Exception e) {
            return "Erreur inconnue";
        }
    }
}
