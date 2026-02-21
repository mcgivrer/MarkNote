package utils;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modèle représentant les métadonnées "Front Matter" d'un document Markdown.
 * <p>
 * Utilise un {@link LinkedHashMap} interne pour stocker tous les attributs,
 * y compris les attributs inconnus. Les clés connues sont accessibles via
 * des constantes et des getters/setters typés.
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
 * custom_attr: some value
 * ---
 * </pre>
 */
public class FrontMatter {

    // ── Clés connues ────────────────────────────────────────────
    public static final String KEY_UUID       = "uuid";
    public static final String KEY_TITLE      = "title";
    public static final String KEY_AUTHOR     = "author";
    public static final String KEY_CREATED_AT = "created_at";
    public static final String KEY_TAGS       = "tags";
    public static final String KEY_SUMMARY    = "summary";
    public static final String KEY_DRAFT      = "draft";
    public static final String KEY_LINKS      = "links";

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

    /** Ensemble ordonné des clés connues (pour la sérialisation). */
    private static final List<String> KNOWN_KEYS = List.of(
            KEY_UUID, KEY_TITLE, KEY_AUTHOR, KEY_CREATED_AT,
            KEY_TAGS, KEY_SUMMARY, KEY_DRAFT, KEY_LINKS
    );

    // ── Stockage ────────────────────────────────────────────────
    /** Map ordonnée contenant tous les attributs (connus et inconnus). */
    private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();

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
            if (!key.isEmpty()) {
                fm.attributes.put(key, value);
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
     * Les clés connues sont sérialisées en premier (dans l'ordre défini),
     * suivies des attributs inconnus dans l'ordre d'insertion.
     *
     * @return le bloc front matter (avec les délimiteurs ---)
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");

        // Écrire les clés connues en premier (dans l'ordre défini)
        for (String key : KNOWN_KEYS) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) {
                // Cas spécial pour draft : ne pas écrire "draft: false"
                if (KEY_DRAFT.equals(key) && "false".equalsIgnoreCase(value)) {
                    continue;
                }
                sb.append(key).append(": ").append(value).append('\n');
            }
        }

        // Écrire les attributs inconnus
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (!KNOWN_KEYS.contains(entry.getKey()) && !entry.getValue().isBlank()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
            }
        }

        sb.append("---\n");
        return sb.toString();
    }

    /**
     * Indique si le front matter est entièrement vide (aucun champ renseigné).
     */
    public boolean isEmpty() {
        return attributes.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    // ── Accès générique à la Map ────────────────────────────────

    /**
     * Retourne la valeur brute d'un attribut.
     *
     * @param key la clé (en minuscules)
     * @return la valeur, ou une chaîne vide si absente
     */
    public String get(String key) {
        String v = attributes.get(key);
        return v != null ? v : "";
    }

    /**
     * Définit la valeur d'un attribut.
     *
     * @param key   la clé (en minuscules)
     * @param value la valeur
     */
    public void put(String key, String value) {
        if (key != null && !key.isBlank()) {
            attributes.put(key.toLowerCase(), value != null ? value : "");
        }
    }

    /**
     * Supprime un attribut.
     *
     * @param key la clé à supprimer
     */
    public void remove(String key) {
        attributes.remove(key);
    }

    /**
     * Retourne une vue non modifiable de tous les attributs, dans l'ordre d'insertion.
     */
    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Retourne la liste des clés d'attributs inconnus (non standard).
     */
    public List<String> getExtraKeys() {
        List<String> extra = new ArrayList<>();
        for (String key : attributes.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                extra.add(key);
            }
        }
        return extra;
    }

    // ── Helpers ─────────────────────────────────────────────────

    /**
     * Parse une valeur qui peut être :
     * <ul>
     *   <li>un scalaire simple : {@code "Jean Dupont"}</li>
     *   <li>une liste entre crochets : {@code "[a, b, c]"}</li>
     * </ul>
     */
    static List<String> parseList(String value) {
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
    static String formatList(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        return "[" + String.join(", ", items) + "]";
    }

    // ── Getters / Setters typés pour les clés connues ───────────

    public String getUuid() {
        return get(KEY_UUID);
    }

    public void setUuid(String uuid) {
        put(KEY_UUID, uuid);
    }

    /**
     * Génère un UUID aléatoire et le stocke.
     *
     * @return l'UUID généré
     */
    public String generateUuid() {
        String uuid = UUID.randomUUID().toString();
        setUuid(uuid);
        return uuid;
    }

    public String getTitle() {
        return get(KEY_TITLE);
    }

    public void setTitle(String title) {
        put(KEY_TITLE, title);
    }

    public List<String> getAuthors() {
        return Collections.unmodifiableList(parseList(get(KEY_AUTHOR)));
    }

    public void setAuthors(List<String> authors) {
        put(KEY_AUTHOR, formatList(authors));
    }

    /**
     * Définit les auteurs à partir d'une chaîne séparée par des virgules.
     */
    public void setAuthorsFromString(String authorsStr) {
        put(KEY_AUTHOR, authorsStr != null ? authorsStr.trim() : "");
    }

    /**
     * Retourne les auteurs sous forme de chaîne séparée par des virgules.
     */
    public String getAuthorsAsString() {
        return String.join(", ", getAuthors());
    }

    public String getCreatedAt() {
        return get(KEY_CREATED_AT);
    }

    public void setCreatedAt(String createdAt) {
        put(KEY_CREATED_AT, createdAt);
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(parseList(get(KEY_TAGS)));
    }

    public void setTags(List<String> tags) {
        put(KEY_TAGS, formatList(tags));
    }

    /**
     * Définit les tags à partir d'une chaîne séparée par des virgules.
     */
    public void setTagsFromString(String tagsStr) {
        if (tagsStr != null && !tagsStr.isBlank()) {
            put(KEY_TAGS, "[" + tagsStr.trim() + "]");
        } else {
            put(KEY_TAGS, "");
        }
    }

    /**
     * Retourne les tags sous forme de chaîne séparée par des virgules.
     */
    public String getTagsAsString() {
        return String.join(", ", getTags());
    }

    public String getSummary() {
        return get(KEY_SUMMARY);
    }

    public void setSummary(String summary) {
        put(KEY_SUMMARY, summary);
    }

    public boolean isDraft() {
        return Boolean.parseBoolean(get(KEY_DRAFT));
    }

    public void setDraft(boolean draft) {
        put(KEY_DRAFT, String.valueOf(draft));
    }

    /**
     * Valide le format de la date created_at.
     *
     * @return true si la date est vide ou au format valide (YYYY-MM-DD ou YYYY-MM-DD HH:mm)
     */
    public boolean isCreatedAtValid() {
        String createdAt = getCreatedAt();
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

    public List<String> getLinks() {
        return Collections.unmodifiableList(parseList(get(KEY_LINKS)));
    }

    public void setLinks(List<String> links) {
        put(KEY_LINKS, formatList(links));
    }

    /**
     * Définit les liens à partir d'une chaîne séparée par des virgules.
     */
    public void setLinksFromString(String linksStr) {
        if (linksStr != null && !linksStr.isBlank()) {
            put(KEY_LINKS, "[" + linksStr.trim() + "]");
        } else {
            put(KEY_LINKS, "");
        }
    }

    /**
     * Retourne les liens sous forme de chaîne séparée par des virgules.
     */
    public String getLinksAsString() {
        return String.join(", ", getLinks());
    }

    /**
     * Ajoute un lien (UUID) s'il n'est pas déjà présent.
     *
     * @param linkUuid l'UUID du document lié
     * @return true si le lien a été ajouté
     */
    public boolean addLink(String linkUuid) {
        if (linkUuid != null && !linkUuid.isBlank()) {
            List<String> currentLinks = new ArrayList<>(getLinks());
            if (!currentLinks.contains(linkUuid)) {
                currentLinks.add(linkUuid);
                setLinks(currentLinks);
                return true;
            }
        }
        return false;
    }
}
