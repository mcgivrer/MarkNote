package services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import config.LLMConfig;
import utils.LogService;

/**
 * Service client pour les API LLM (Ollama, OpenAI compatible). Supporte le
 * streaming des réponses.
 */
public class LLMService {

    private static final String LOG_SOURCE = "LLMService";
    private final LogService log = LogService.getInstance();

    private final LLMConfig config;
    private final HttpClient httpClient;
    private CompletableFuture<Void> currentRequest;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Crée un nouveau service LLM.
     *
     * @param config La configuration LLM
     */
    public LLMService(LLMConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.getTimeout())).build();
    }

    /**
     * Envoie un prompt au LLM avec streaming de la réponse.
     *
     * @param messages   La liste des messages de conversation
     * @param onChunk    Callback appelé pour chaque chunk de réponse
     * @param onComplete Callback appelé à la fin de la réponse
     * @param onError    Callback appelé en cas d'erreur
     */
    public void sendPromptAsync(List<Message> messages, Consumer<String> onChunk, Runnable onComplete,
            Consumer<Exception> onError) {
        cancelled.set(false);

        String jsonBody = buildRequestBody(messages, true);

        log.debug(LOG_SOURCE, "Sending request to: " + config.getChatEndpoint());
        log.debug(LOG_SOURCE, "Request body: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(config.getChatEndpoint()))
                .timeout(Duration.ofSeconds(config.getTimeout())).header("Content-Type", "application/json")
                .header("Accept", "application/x-ndjson, text/event-stream, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

        // Ajouter header Authorization si API key présente
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            request = HttpRequest.newBuilder(request, (k, v) -> true)
                    .header("Authorization", "Bearer " + config.getApiKey()).build();
        }

        final HttpRequest finalRequest = request;
        currentRequest = CompletableFuture.runAsync(() -> {
            int retries = 0;
            while (retries <= config.getMaxRetries()) {
                try {
                    HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(finalRequest,
                            HttpResponse.BodyHandlers.ofLines());

                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + response.statusCode());
                    }

                    response.body().forEach(line -> {
                        if (cancelled.get()) {
                            return;
                        }
                        processStreamLine(line, onChunk);
                    });

                    if (!cancelled.get()) {
                        onComplete.run();
                    }
                    return;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (!cancelled.get()) {
                        onError.accept(e);
                    }
                    return;
                } catch (Exception e) {
                    retries++;
                    if (retries > config.getMaxRetries()) {
                        log.error(LOG_SOURCE, "Request failed after " + retries + " attempts: " + e);
                        onError.accept(e);
                        return;
                    }
                    log.warn(LOG_SOURCE, "Retry " + retries + "/" + config.getMaxRetries() + " after error: " + e);
                    try {
                        Thread.sleep(1000 * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    /**
     * Envoie un prompt simple (non streaming).
     *
     * @param messages Les messages de conversation
     * @return La réponse complète
     * @throws Exception En cas d'erreur
     */
    public String sendPromptSync(List<Message> messages) throws Exception {
        String jsonBody = buildRequestBody(messages, false);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(config.getChatEndpoint()))
                .timeout(Duration.ofSeconds(config.getTimeout())).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractResponse(response.body());
    }

    /**
     * Annule la requête en cours.
     */
    public void cancelRequest() {
        cancelled.set(true);
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
            log.info(LOG_SOURCE, "Request cancelled");
        }
    }

    /**
     * Teste la connexion au service LLM.
     *
     * @return true si la connexion est établie
     */
    public boolean testConnection() {
        try {
            List<Message> testMessages = new ArrayList<>();
            testMessages.add(new Message(MessageRole.USER, "Hi"));

            // Utiliser un timeout court pour le test
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(config.getChatEndpoint()))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(testMessages, false))).build();

            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                request = HttpRequest.newBuilder(request, (k, v) -> true)
                        .header("Authorization", "Bearer " + config.getApiKey()).build();

            }
            log.debug(LOG_SOURCE, "Test request body: " + buildRequestBody(testMessages, false));
            log.debug(LOG_SOURCE, "Authorization header set for test connection: "
                    + (config.getApiKey() != null && !config.getApiKey().isBlank()));
            log.debug(LOG_SOURCE, "Testing connection to: " + config.getChatEndpoint());
            log.debug(LOG_SOURCE, "Test request: " + request);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info(LOG_SOURCE, "Connection test: HTTP " + response.statusCode());
            return response.statusCode() == 200;

        } catch (Exception e) {
            log.error(LOG_SOURCE, "Connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Récupère la liste des modèles disponibles (Ollama).
     *
     * @return Liste des noms de modèles
     */
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        try {
            String baseUrl = config.getEndpointUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(10)).GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = (JSONObject) new JSONParser().parse(response.body());
                JSONArray modelsArray = (JSONArray) json.get("models");
                if (modelsArray != null) {
                    for (Object obj : modelsArray) {
                        JSONObject model = (JSONObject) obj;
                        String name = (String) model.get("name");
                        if (name != null) {
                            models.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn(LOG_SOURCE, "Failed to get models list: " + e.getMessage());
        }
        return models;
    }

    /**
     * Vérifie si une requête est en cours.
     *
     * @return true si une requête est en cours
     */
    public boolean isProcessing() {
        return currentRequest != null && !currentRequest.isDone();
    }

    // --- Private methods ---

    @SuppressWarnings("unchecked")
    private String buildRequestBody(List<Message> messages, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", config.getModel());

        // Ajouter le contexte système si présent
        List<Message> allMessages = new ArrayList<>();
        if (config.getSystemContext() != null && !config.getSystemContext().isBlank()) {
            allMessages.add(new Message(MessageRole.SYSTEM, config.getSystemContext()));
        }
        allMessages.addAll(messages);

        JSONArray messagesArray = new JSONArray();
        for (Message msg : allMessages) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", msg.getRole().name().toLowerCase());
            msgObj.put("content", msg.getContent());
            messagesArray.add(msgObj);
        }
        body.put("messages", messagesArray);
        body.put("stream", stream);

        return body.toJSONString();
    }

    private void processStreamLine(String line, Consumer<String> onChunk) {
        if (line == null || line.isBlank()) {
            return;
        }

        // Retirer le préfixe "data: " pour SSE
        if (line.startsWith("data: ")) {
            line = line.substring(6);
        }

        // Ignorer les lignes de fin
        if (line.equals("[DONE]")) {
            return;
        }

        try {
            JSONObject json = (JSONObject) new JSONParser().parse(line);

            // Format Ollama: {"response": "chunk"}
            String response = (String) json.get("response");
            if (response != null && !response.isEmpty()) {
                onChunk.accept(response);
                return;
            }

            // Format OpenAI: {"choices": [{"delta": {"content": "chunk"}}]}
            String content = extractChoicesContent(json, "delta");
            if (content != null && !content.isEmpty()) {
                onChunk.accept(content);
            }
        } catch (Exception e) {
            log.warn(LOG_SOURCE, "Failed to parse stream line: " + line);
        }
    }

    private String extractResponse(String jsonBody) {
        try {
            JSONObject json = (JSONObject) new JSONParser().parse(jsonBody);

            // Format Ollama: {"message": {"content": "..."}}
            JSONObject message = (JSONObject) json.get("message");
            if (message != null) {
                String content = (String) message.get("content");
                if (content != null) return content;
            }

            // Format OpenAI: {"choices": [{"message": {"content": "..."}}]}
            return extractChoicesContent(json, "message");
        } catch (Exception e) {
            log.warn(LOG_SOURCE, "Failed to parse response: " + jsonBody);
            return null;
        }
    }

    /**
     * Extrait le contenu depuis la structure OpenAI choices[0].{key}.content.
     */
    private String extractChoicesContent(JSONObject json, String innerKey) {
        JSONArray choices = (JSONArray) json.get("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject choice = (JSONObject) choices.get(0);
            JSONObject inner = (JSONObject) choice.get(innerKey);
            if (inner != null) {
                return (String) inner.get("content");
            }
        }
        return null;
    }
}
