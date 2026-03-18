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

import config.LLMConfig;
import utils.LogService;

/**
 * Service client pour les API LLM (Ollama, OpenAI compatible).
 * Supporte le streaming des réponses.
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
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeout()))
                .build();
    }

    /**
     * Envoie un prompt au LLM avec streaming de la réponse.
     *
     * @param messages     La liste des messages de conversation
     * @param onChunk      Callback appelé pour chaque chunk de réponse
     * @param onComplete   Callback appelé à la fin de la réponse
     * @param onError      Callback appelé en cas d'erreur
     */
    public void sendPromptAsync(List<Message> messages, Consumer<String> onChunk,
                                 Runnable onComplete, Consumer<Exception> onError) {
        cancelled.set(false);
        
        String jsonBody = buildRequestBody(messages, true);
        
        log.debug(LOG_SOURCE, "Sending request to: " + config.getChatEndpoint());
        log.debug(LOG_SOURCE, "Request body: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getChatEndpoint()))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/x-ndjson, text/event-stream, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Ajouter header Authorization si API key présente
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            request = HttpRequest.newBuilder(request, (k, v) -> true)
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .build();
        }

        final HttpRequest finalRequest = request;
        currentRequest = CompletableFuture.runAsync(() -> {
            int retries = 0;
            while (retries <= config.getMaxRetries()) {
                try {
                    HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                            finalRequest,
                            HttpResponse.BodyHandlers.ofLines()
                    );

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

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.getChatEndpoint()))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );

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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getChatEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(testMessages, false)))
                    .build();

            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                request = HttpRequest.newBuilder(request, (k, v) -> true)
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .build();
            }

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
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Parse simple de la réponse JSON
                String body = response.body();
                int modelsStart = body.indexOf("\"models\":");
                if (modelsStart != -1) {
                    int arrayStart = body.indexOf("[", modelsStart);
                    int arrayEnd = body.indexOf("]", arrayStart);
                    if (arrayStart != -1 && arrayEnd != -1) {
                        String modelsArray = body.substring(arrayStart, arrayEnd + 1);
                        // Extraire les noms de modèles
                        int namePos = 0;
                        while ((namePos = modelsArray.indexOf("\"name\":", namePos)) != -1) {
                            int valueStart = modelsArray.indexOf("\"", namePos + 7) + 1;
                            int valueEnd = modelsArray.indexOf("\"", valueStart);
                            if (valueStart > 0 && valueEnd > valueStart) {
                                models.add(modelsArray.substring(valueStart, valueEnd));
                            }
                            namePos = valueEnd;
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

    private String buildRequestBody(List<Message> messages, boolean stream) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(escapeJson(config.getModel())).append("\",");
        
        // Ajouter le contexte système si présent
        List<Message> allMessages = new ArrayList<>();
        if (config.getSystemContext() != null && !config.getSystemContext().isBlank()) {
            allMessages.add(new Message(MessageRole.SYSTEM, config.getSystemContext()));
        }
        allMessages.addAll(messages);
        
        sb.append("\"messages\":[");
        for (int i = 0; i < allMessages.size(); i++) {
            sb.append(allMessages.get(i).toApiJson());
            if (i < allMessages.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("],");
        sb.append("\"stream\":").append(stream);
        sb.append("}");
        
        return sb.toString();
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
            // Format Ollama: {"response": "chunk"}
            String response = extractJsonField(line, "response");
            if (response != null && !response.isEmpty()) {
                onChunk.accept(response);
                return;
            }
            
            // Format OpenAI: {"choices": [{"delta": {"content": "chunk"}}]}
            String content = extractNestedContent(line);
            if (content != null && !content.isEmpty()) {
                onChunk.accept(content);
            }
        } catch (Exception e) {
            log.warn(LOG_SOURCE, "Failed to parse stream line: " + line);
        }
    }

    private String extractResponse(String jsonBody) {
        // Format Ollama: {"message": {"content": "..."}}
        String messageContent = extractNestedMessageContent(jsonBody);
        if (messageContent != null) {
            return messageContent;
        }
        
        // Format OpenAI: {"choices": [{"message": {"content": "..."}}]}
        return extractNestedContent(jsonBody);
    }

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String extractNestedContent(String json) {
        // Chercher content dans la structure OpenAI
        int contentPos = json.indexOf("\"content\":");
        if (contentPos == -1) return null;
        
        int valueStart = json.indexOf("\"", contentPos + 10);
        if (valueStart == -1) return null;
        valueStart++;
        
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String extractNestedMessageContent(String json) {
        // Format Ollama: {"message": {"role": "...", "content": "..."}}
        int messagePos = json.indexOf("\"message\":");
        if (messagePos == -1) return null;
        
        int contentPos = json.indexOf("\"content\":", messagePos);
        if (contentPos == -1) return null;
        
        return extractJsonField(json.substring(contentPos), "content");
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
