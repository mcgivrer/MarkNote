package utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modèle représentant les métadonnées "Front Matter" d'un document Markdown.
 * <p>
 * Format attendu en tête de fichier :
 * <pre>
 * ---
 * title: Mon article
 * author: Jean Dupont
 * created_at: 2026-02-21
 * tags: [java, markdown, editor]
 * summary: Un résumé de l'article
 * draft: true
 * ---
 * </pre>
 */
public class FrontMatter {

    // ── Format de date ──────────────────────────────────────────
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Pattern pour détecter un bloc front matter au début du fichier. */
    private static final Pattern FM_BLOCK =
            Pattern.compile("\\A---\\s*\\n(.*?)\\n---\\s*\\n?", Pattern.DOTALL);

    /** Pattern pour parser une liste entre crochets. */
    private static final Pattern LIST_PATTERN =
            Pattern.compile("\\[(.*)\\]");

    // ── Champs ──────────────────────────────────────────────────
    private String title = "";
    private List<String> authors = new ArrayList<>();
    private String createdAt = "";
    private List<String> tags = new ArrayList<>();
    private String summary = "";
    private boolean draft = false;

    // ── Constructeurs ───────────────────────────────────────────

    public FrontMatter() {
    }

    // ── Parsing ─────────────────────────────────────────────────

    /**
     * Analyse le contenu d'un fichier Markdown et en extrait le front matter.
     *
     * @param content le contenu complet du fichier
     * @return le FrontMatter parsé, ou {@code null} si aucun bloc n'est détecté
     */
    public static FrontMatter parse(String content) {
        if (content == null) return null;
        Matcher m = FM_BLOCK.matcher(content);
        if (!m.find()) return null;

        FrontMatter fm = new FrontMatter();
        String block = m.group(1);

        for (String line : block.split("\\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "title" -> fm.title = value;
                case "author" -> fm.authors = parseList(value);
                case "created_at" -> fm.createdAt = value;
                case "tags" -> fm.tags = parseList(value);
                case "summary" -> fm.summary = value;
                case "draft" -> fm.draft = Boolean.parseBoolean(value);
            }
        }
        return fm;
    }

    /**
     * Retourne le contenu du fichier sans le bloc front matter.
     *
     * @param content le contenu complet
     * @return le contenu Markdown pur (après le bloc ---)
     */
    public static String stripFrontMatter(String content) {
        if (content == null) return "";
        Matcher m = FM_BLOCK.matcher(content);
        if (m.find()) {
            return content.substring(m.end());
        }
        return content;
    }

    /**
     * Sérialise le front matter en bloc YAML.
     *
     * @return le bloc front matter (avec les délimiteurs ---)
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        if (!title.isBlank()) {
            sb.append("title: ").append(title).append('\n');
        }
        if (!authors.isEmpty()) {
            sb.append("author: ").append(formatList(authors)).append('\n');
        }
        if (!createdAt.isBlank()) {
            sb.append("created_at: ").append(createdAt).append('\n');
        }
        if (!tags.isEmpty()) {
            sb.append("tags: ").append(formatList(tags)).append('\n');
        }
        if (!summary.isBlank()) {
            sb.append("summary: ").append(summary).append('\n');
        }
        if (draft) {
            sb.append("draft: true\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    /**
     * Indique si le front matter est entièrement vide (aucun champ renseigné).
     */
    public boolean isEmpty() {
        return title.isBlank()
                && authors.isEmpty()
                && createdAt.isBlank()
                && tags.isEmpty()
                && summary.isBlank()
                && !draft;
    }

    // ── Helpers privés ──────────────────────────────────────────

    /**
     * Parse une valeur qui peut être :
     * <ul>
     *   <li>un scalaire simple : {@code "Jean Dupont"}</li>
     *   <li>une liste entre crochets : {@code "[a, b, c]"}</li>
     * </ul>
     */
    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        Matcher m = LIST_PATTERN.matcher(value);
        if (m.find()) {
            String inner = m.group(1);
            List<String> items = new ArrayList<>();
            for (String item : inner.split(",")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) items.add(trimmed);
            }
            return items;
        }
        // Valeur scalaire → liste à un élément
        return new ArrayList<>(List.of(value.trim()));
    }

    /**
     * Formate une liste en représentation YAML inline.
     */
    private static String formatList(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return "[" + String.join(", ", items) + "]";
    }

    // ── Getters / Setters ───────────────────────────────────────

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public List<String> getAuthors() {
        return Collections.unmodifiableList(authors);
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors != null ? new ArrayList<>(authors) : new ArrayList<>();
    }

    /**
     * Définit les auteurs à partir d'une chaîne séparée par des virgules.
     */
    public void setAuthorsFromString(String authorsStr) {
        this.authors = parseList(authorsStr);
    }

    /**
     * Retourne les auteurs sous forme de chaîne séparée par des virgules.
     */
    public String getAuthorsAsString() {
        return String.join(", ", authors);
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt != null ? createdAt : "";
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    /**
     * Définit les tags à partir d'une chaîne séparée par des virgules.
     */
    public void setTagsFromString(String tagsStr) {
        this.tags = parseList("[" + tagsStr + "]");
    }

    /**
     * Retourne les tags sous forme de chaîne séparée par des virgules.
     */
    public String getTagsAsString() {
        return String.join(", ", tags);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary != null ? summary : "";
    }

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    /**
     * Valide le format de la date created_at.
     *
     * @return true si la date est vide ou au format valide (YYYY-MM-DD ou YYYY-MM-DD HH:mm)
     */
    public boolean isCreatedAtValid() {
        if (createdAt.isBlank()) return true;
        try {
            DATETIME_FMT.parse(createdAt);
            return true;
        } catch (DateTimeParseException e1) {
            try {
                DATE_FMT.parse(createdAt);
                return true;
            } catch (DateTimeParseException e2) {
                return false;
            }
        }
    }
}
