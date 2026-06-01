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
 * Connecteur pour l'API GitHub (github.com).
 * 
 * <p>Ce connecteur utilise l'API REST de GitHub v3 pour lister et créer des dépôts.
 * Il nécessite un Personal Access Token (PAT) avec les permissions {@code repo}.</p>
 * 
 * <h2>Endpoints utilisés :</h2>
 * <ul>
 *   <li>GET /user/repos?type=owner&per_page=100 - Liste les dépôts de l'utilisateur</li>
 *   <li>POST /user/repos - Crée un nouveau dépôt</li>
 * </ul>
 * 
 * @see <a href="https://docs.github.com/en/rest">GitHub REST API</a>
 */
public class GitHubConnector implements RemoteConnector {

    private static final String API_BASE_URL = "https://api.github.com";
    private static final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "GitHubConnector";

    private final String token;
    private final HttpClient httpClient;
    private final JSONParser jsonParser;

    /**
     * Construit un connecteur GitHub avec le token fourni.
     *
     * @param token Le Personal Access Token GitHub avec permissions {@code repo}
     */
    public GitHubConnector(String token) {
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.jsonParser = new JSONParser();
    }

    @Override
    public String platform() {
        return "github";
    }

    @Override
    public List<RemoteRepo> listRepositories() throws RemoteConnectorException {
        log.debug(LOG_SOURCE, "Récupération de la liste des dépôts GitHub");

        String url = API_BASE_URL + "/user/repos?type=owner&per_page=100&sort=updated";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "token " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
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
        log.debug(LOG_SOURCE, "Création du dépôt GitHub: " + name + " (privé=" + isPrivate + ")");

        String url = API_BASE_URL + "/user/repos";

        // Construction du body JSON
        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("private", isPrivate);
        body.put("auto_init", true); // Créer avec README initial

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "token " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
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

        log.error(LOG_SOURCE, "Erreur API GitHub " + statusCode + ": " + errorMessage);
        throw new RemoteConnectorException(statusCode, errorMessage);
    }

    /**
     * Extrait le message d'erreur du JSON de réponse GitHub.
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
