package utils;

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
 * Connecteur pour l'API GitLab (gitlab.com ou instance self-hosted).
 * 
 * <p>Ce connecteur utilise l'API REST de GitLab v4 pour lister et créer des dépôts (projects).
 * Il nécessite un Personal Access Token avec les permissions {@code api} ou {@code write_repository}.</p>
 * 
 * <h2>Endpoints utilisés :</h2>
 * <ul>
 *   <li>GET /api/v4/projects?owned=true&per_page=100 - Liste les projets de l'utilisateur</li>
 *   <li>POST /api/v4/projects - Crée un nouveau projet</li>
 * </ul>
 * 
 * @see <a href="https://docs.gitlab.com/ee/api/">GitLab API Documentation</a>
 */
public class GitLabConnector implements RemoteConnector {

    private static final String DEFAULT_GITLAB_URL = "https://gitlab.com";
    private static final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "GitLabConnector";

    private final String instanceUrl;
    private final String token;
    private final HttpClient httpClient;
    private final JSONParser jsonParser;

    /**
     * Construit un connecteur pour GitLab.com (instance publique).
     *
     * @param token Le Personal Access Token GitLab avec permissions {@code api}
     */
    public GitLabConnector(String token) {
        this(DEFAULT_GITLAB_URL, token);
    }

    /**
     * Construit un connecteur pour une instance GitLab custom.
     *
     * @param instanceUrl L'URL de base de l'instance GitLab (ex: https://gitlab.example.com)
     * @param token       Le Personal Access Token GitLab avec permissions {@code api}
     */
    public GitLabConnector(String instanceUrl, String token) {
        this.instanceUrl = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.jsonParser = new JSONParser();
    }

    @Override
    public String platform() {
        return "gitlab";
    }

    @Override
    public List<RemoteRepo> listRepositories() throws RemoteConnectorException {
        log.debug(LOG_SOURCE, "Récupération de la liste des projets GitLab depuis " + instanceUrl);

        String url = instanceUrl + "/api/v4/projects?owned=true&per_page=100&order_by=updated_at&sort=desc";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                handleErrorResponse(response);
            }

            return parseRepositories(response.body());

        } catch (IOException e) {
            log.error(LOG_SOURCE, "Erreur réseau lors de la récupération des projets: " + e.getMessage());
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
        log.debug(LOG_SOURCE, "Création du projet GitLab: " + name + " (privé=" + isPrivate + ")");

        String url = instanceUrl + "/api/v4/projects";

        // Construction du body JSON
        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("visibility", isPrivate ? "private" : "public");
        body.put("initialize_with_readme", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("PRIVATE-TOKEN", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                handleErrorResponse(response);
            }

            log.info(LOG_SOURCE, "Projet créé avec succès: " + name);

        } catch (IOException e) {
            log.error(LOG_SOURCE, "Erreur réseau lors de la création du projet: " + e.getMessage());
            throw new RemoteConnectorException("Erreur réseau: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(LOG_SOURCE, "Requête interrompue: " + e.getMessage());
            throw new RemoteConnectorException("Requête interrompue", e);
        }
    }

    /**
     * Parse la réponse JSON pour extraire la liste des projets.
     */
    private List<RemoteRepo> parseRepositories(String jsonResponse) throws RemoteConnectorException {
        List<RemoteRepo> repos = new ArrayList<>();

        try {
            JSONArray jsonArray = (JSONArray) jsonParser.parse(jsonResponse);

            for (Object obj : jsonArray) {
                JSONObject project = (JSONObject) obj;

                String name = (String) project.get("name");
                String cloneUrl = (String) project.get("http_url_to_repo");
                String description = (String) project.get("description");
                String visibility = (String) project.get("visibility");
                String defaultBranch = (String) project.get("default_branch");

                boolean isPrivate = "private".equals(visibility);

                repos.add(new RemoteRepo(
                        name,
                        cloneUrl,
                        description != null ? description : "",
                        isPrivate,
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

        log.error(LOG_SOURCE, "Erreur API GitLab " + statusCode + ": " + errorMessage);
        throw new RemoteConnectorException(statusCode, errorMessage);
    }

    /**
     * Extrait le message d'erreur du JSON de réponse GitLab.
     */
    private String extractErrorMessage(String jsonResponse) {
        try {
            JSONObject json = (JSONObject) jsonParser.parse(jsonResponse);
            
            // GitLab peut retourner "message" ou "error"
            String message = (String) json.get("message");
            if (message != null) {
                return message;
            }
            
            String error = (String) json.get("error");
            if (error != null) {
                return error;
            }
            
            return "Erreur inconnue";
        } catch (Exception e) {
            return "Erreur inconnue";
        }
    }
}
