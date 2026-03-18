package services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Représente un message dans une conversation LLM.
 */
public class Message {

    private final MessageRole role;
    private String content;
    private final LocalDateTime timestamp;

    /**
     * Crée un nouveau message.
     *
     * @param role    Le rôle de l'émetteur (USER, ASSISTANT, SYSTEM)
     * @param content Le contenu du message
     */
    public Message(MessageRole role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Crée un message avec un timestamp spécifique.
     *
     * @param role      Le rôle de l'émetteur
     * @param content   Le contenu du message
     * @param timestamp Le timestamp du message
     */
    public Message(MessageRole role, String content, LocalDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Ajoute du contenu au message existant (streaming).
     *
     * @param chunk Le contenu à ajouter
     */
    public void appendContent(String chunk) {
        this.content += chunk;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Convertit le message en JSON pour l'API LLM.
     *
     * @return Le message au format JSON
     */
    public String toApiJson() {
        String escapedContent = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return String.format("{\"role\":\"%s\",\"content\":\"%s\"}",
                role.name().toLowerCase(), escapedContent);
    }

    /**
     * Convertit le message en JSON complet pour l'export.
     *
     * @return Le message au format JSON avec timestamp
     */
    public String toJson() {
        String escapedContent = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return String.format("{\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                role.name().toLowerCase(),
                escapedContent,
                timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * Parse un message depuis une ligne JSON.
     *
     * @param json La ligne JSON
     * @return Le message parsé ou null si invalide
     */
    public static Message fromJson(String json) {
        try {
            // Simple parsing sans bibliothèque JSON externe
            String roleStr = extractJsonValue(json, "role");
            String content = extractJsonValue(json, "content");
            String timestampStr = extractJsonValue(json, "timestamp");

            MessageRole role = MessageRole.valueOf(roleStr.toUpperCase());
            LocalDateTime timestamp = timestampStr != null
                    ? LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : LocalDateTime.now();

            // Dé-échapper le contenu
            content = content
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            return new Message(role, content, timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = start;
        boolean escaped = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            }
            end++;
        }
        return json.substring(start, end);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s",
                timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                role, content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }
}
