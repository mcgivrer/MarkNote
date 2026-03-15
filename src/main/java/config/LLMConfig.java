package config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration pour le service LLM.
 */
public class LLMConfig {

    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".marknote";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "llm-config";

    private String endpointUrl = "http://localhost:11434";
    private String apiKey = "";
    private String model = "llama3.2";
    private int timeout = 60;
    private String systemContext = "";
    private boolean enabled = true;
    private int maxRetries = 3;

    /**
     * Charge la configuration depuis le fichier.
     */
    public void load() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(configFile.toPath());
            for (String line : lines) {
                if (line.startsWith("endpointUrl=")) {
                    endpointUrl = line.substring("endpointUrl=".length()).trim();
                } else if (line.startsWith("apiKey=")) {
                    apiKey = line.substring("apiKey=".length()).trim();
                } else if (line.startsWith("model=")) {
                    model = line.substring("model=".length()).trim();
                } else if (line.startsWith("timeout=")) {
                    try {
                        timeout = Integer.parseInt(line.substring("timeout=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("systemContext=")) {
                    systemContext = unescapeNewlines(line.substring("systemContext=".length()).trim());
                } else if (line.startsWith("enabled=")) {
                    enabled = Boolean.parseBoolean(line.substring("enabled=".length()).trim());
                } else if (line.startsWith("maxRetries=")) {
                    try {
                        maxRetries = Integer.parseInt(line.substring("maxRetries=".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Sauvegarde la configuration dans le fichier.
     */
    public void save() {
        try {
            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            List<String> lines = new ArrayList<>();
            lines.add("endpointUrl=" + endpointUrl);
            lines.add("apiKey=" + apiKey);
            lines.add("model=" + model);
            lines.add("timeout=" + timeout);
            lines.add("systemContext=" + escapeNewlines(systemContext));
            lines.add("enabled=" + enabled);
            lines.add("maxRetries=" + maxRetries);

            Files.writeString(Path.of(CONFIG_FILE), String.join("\n", lines));
        } catch (IOException ignored) {
        }
    }

    /**
     * Valide la configuration.
     *
     * @return true si la configuration est valide
     */
    public boolean validate() {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            return false;
        }
        if (model == null || model.isBlank()) {
            return false;
        }
        if (timeout <= 0) {
            return false;
        }
        return true;
    }

    /**
     * Retourne l'URL complète pour l'API chat.
     *
     * @return L'URL de l'endpoint chat
     */
    public String getChatEndpoint() {
        String base = endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl;
        // Détection automatique Ollama vs OpenAI
        if (base.contains("openai.com") || base.contains("/v1")) {
            return base + "/v1/chat/completions";
        }
        return base + "/api/chat";
    }

    /**
     * Détermine si l'API est de type OpenAI.
     *
     * @return true si format OpenAI
     */
    public boolean isOpenAIFormat() {
        return endpointUrl.contains("openai.com") || endpointUrl.contains("/v1");
    }

    private String escapeNewlines(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private String unescapeNewlines(String text) {
        if (text == null) return "";
        return text.replace("\\n", "\n").replace("\\\\", "\\");
    }

    // --- Getters and Setters ---

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl != null ? endpointUrl : "";
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model != null ? model : "";
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = Math.max(1, timeout);
    }

    public String getSystemContext() {
        return systemContext;
    }

    public void setSystemContext(String systemContext) {
        this.systemContext = systemContext != null ? systemContext : "";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }
}
