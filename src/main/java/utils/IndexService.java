package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.concurrent.Task;

/**
 * Service d'indexation des fichiers d'un projet.
 * <p>
 * Construit et maintient un index JSON stocké à la racine du projet
 * ({@code .marknote-index.json}). L'index référence chaque fichier Markdown
 * par ses métadonnées de front matter (titre, tags, auteur, résumé, uuid)
 * ainsi que par son nom de fichier.
 * </p>
 */
public class IndexService {

    /** Nom du fichier d'index stocké dans le répertoire racine du projet. */
    public static final String INDEX_FILENAME = ".marknote-index.json";

    // ── Modèle interne ─────────────────────────────────────────

    /**
     * Entrée d'index pour un fichier unique.
     */
    public static class IndexEntry {
        private final String relativePath;
        private final String filename;
        private String uuid = "";
        private String title = "";
        private List<String> authors = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
        private String summary = "";
        private String createdAt = "";
        private boolean draft = false;
        private List<String> links = new ArrayList<>();

        public IndexEntry(String relativePath, String filename) {
            this.relativePath = relativePath;
            this.filename = filename;
        }

        public String getRelativePath() { return relativePath; }
        public String getFilename() { return filename; }
        public String getUuid() { return uuid; }
        public String getTitle() { return title; }
        public List<String> getAuthors() { return Collections.unmodifiableList(authors); }
        public List<String> getTags() { return Collections.unmodifiableList(tags); }
        public String getSummary() { return summary; }
        public String getCreatedAt() { return createdAt; }
        public boolean isDraft() { return draft; }
        public List<String> getLinks() { return Collections.unmodifiableList(links); }

        /**
         * Retourne un titre d'affichage : le titre front matter s'il existe,
         * sinon le nom du fichier.
         */
        public String getDisplayTitle() {
            return (title != null && !title.isBlank()) ? title : filename;
        }

        /**
         * Vérifie si cette entrée correspond à la requête de recherche.
         * La recherche s'effectue sur le titre, le nom de fichier, les tags,
         * le résumé, les auteurs et l'UUID.
         *
         * @param query la requête en minuscules
         * @return le texte correspondant décrivant le match, ou {@code null} si aucun match
         */
        public String matches(String query) {
            if (query == null || query.isBlank()) return null;
            String q = query.toLowerCase();

            if (title.toLowerCase().contains(q)) return "Title: " + title;
            if (filename.toLowerCase().contains(q)) return "File: " + filename;
            for (String tag : tags) {
                if (tag.toLowerCase().contains(q)) return "Tag: " + tag;
            }
            if (summary.toLowerCase().contains(q)) return "Summary: " + summary;
            for (String author : authors) {
                if (author.toLowerCase().contains(q)) return "Author: " + author;
            }
            if (uuid.toLowerCase().contains(q)) return "UUID: " + uuid;
            return null;
        }
    }

    // ── Données ─────────────────────────────────────────────────

    private File projectDir;
    private final List<IndexEntry> entries = new ArrayList<>();
    private final Map<String, Integer> tagCounts = new LinkedHashMap<>();

    /** Callback de progression (0.0 – 1.0). Appelé sur le FX Application Thread. */
    private Consumer<Double> onProgress;

    /** Callback de fin d'indexation. Appelé sur le FX Application Thread. */
    private Runnable onFinished;

    /**
     * Définit le callback de progression.
     *
     * @param onProgress reçoit une valeur entre 0.0 et 1.0
     */
    public void setOnProgress(Consumer<Double> onProgress) {
        this.onProgress = onProgress;
    }

    /**
     * Définit le callback de fin d'indexation.
     */
    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    // ── API publique ────────────────────────────────────────────

    /**
     * Construit l'index complet pour le répertoire de projet donné.
     * Scanne récursivement tous les fichiers {@code .md} et indexe
     * leurs métadonnées de front matter.
     *
     * @param projectDir le répertoire racine du projet
     */
    public void buildIndex(File projectDir) {
        this.projectDir = projectDir;
        entries.clear();
        tagCounts.clear();
        if (projectDir == null || !projectDir.isDirectory()) return;

        try (Stream<Path> walk = Files.walk(projectDir.toPath())) {
            List<Path> mdFiles = walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .toList();

            int total = mdFiles.size();
            int done = 0;
            for (Path p : mdFiles) {
                indexFile(p);
                done++;
                reportProgress(done, total);
            }
        } catch (IOException e) {
            System.err.println("IndexService: error scanning project: " + e.getMessage());
        }

        sortTagCounts();
        saveIndex();
    }

    /**
     * Lance l'indexation complète dans un thread séparé pour ne pas bloquer
     * le fil d'exécution JavaFX.
     *
     * @param projectDir le répertoire racine du projet
     */
    public void buildIndexAsync(File projectDir) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                buildIndex(projectDir);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            if (onFinished != null) Platform.runLater(onFinished);
        });
        task.setOnFailed(e -> {
            System.err.println("IndexService: async indexing failed: " + task.getException());
            if (onFinished != null) Platform.runLater(onFinished);
        });

        Thread thread = new Thread(task, "IndexService-build");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Charge l'index depuis le fichier JSON du projet.
     *
     * @param projectDir le répertoire racine du projet
     * @return {@code true} si l'index a été chargé avec succès
     */
    public boolean loadIndex(File projectDir) {
        this.projectDir = projectDir;
        entries.clear();
        tagCounts.clear();
        if (projectDir == null) return false;

        File indexFile = new File(projectDir, INDEX_FILENAME);
        if (!indexFile.exists()) return false;

        try {
            String json = Files.readString(indexFile.toPath());
            parseJson(json);
            return true;
        } catch (IOException e) {
            System.err.println("IndexService: error loading index: " + e.getMessage());
            return false;
        }
    }

    /**
     * Supprime le fichier d'index du projet.
     *
     * @param projectDir le répertoire racine du projet
     */
    public void resetIndex(File projectDir) {
        if (projectDir == null) return;
        File indexFile = new File(projectDir, INDEX_FILENAME);
        if (indexFile.exists()) {
            indexFile.delete();
        }
        entries.clear();
        tagCounts.clear();
    }

    /**
     * Recherche les entrées correspondant à la requête.
     *
     * @param query la requête de recherche
     * @return les résultats de recherche (entrée + texte de correspondance)
     */
    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        List<SearchResult> results = new ArrayList<>();
        for (IndexEntry entry : entries) {
            String matchText = entry.matches(query);
            if (matchText != null) {
                results.add(new SearchResult(entry, matchText));
            }
        }
        return results;
    }

    /**
     * Retourne le nombre d'occurrences de chaque tag.
     */
    public Map<String, Integer> getTagCounts() {
        return Collections.unmodifiableMap(tagCounts);
    }

    /**
     * Retourne toutes les entrées de l'index.
     */
    public List<IndexEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Résout une entrée d'index vers un fichier absolu.
     */
    public File resolveFile(IndexEntry entry) {
        if (projectDir == null || entry == null) return null;
        return new File(projectDir, entry.getRelativePath());
    }

    // ── Mises à jour incrémentales ──────────────────────────────

    /**
     * Met à jour l'index pour un fichier donné. Si le fichier est déjà indexé,
     * son entrée est remplacée ; sinon une nouvelle entrée est ajoutée.
     * Applicable aux fichiers {@code .md} uniquement ; les autres sont ignorés.
     * Persiste l'index et recalcule les compteurs de tags.
     *
     * @param file le fichier à (ré-)indexer
     */
    public void updateFile(File file) {
        if (projectDir == null || file == null) return;
        if (!file.getName().toLowerCase().endsWith(".md")) return;
        if (file.getName().startsWith(".")) return;

        // Retirer l'ancienne entrée si elle existe
        String relativePath = projectDir.toPath().relativize(file.toPath()).toString();
        entries.removeIf(e -> e.relativePath.equals(relativePath));

        // Indexer le fichier s'il existe encore (pourrait avoir été supprimé)
        if (file.exists() && file.isFile()) {
            indexFile(file.toPath());
        }

        rebuildTagCounts();
        saveIndex();
    }

    /**
     * Retire du index le fichier correspondant au chemin relatif donné,
     * puis persiste et recalcule les tags.
     *
     * @param file le fichier supprimé (n'a plus besoin d'exister sur disque)
     */
    public void removeFile(File file) {
        if (projectDir == null || file == null) return;
        String relativePath = projectDir.toPath().relativize(file.toPath()).toString();
        entries.removeIf(e -> e.relativePath.equals(relativePath));
        rebuildTagCounts();
        saveIndex();
    }

    /**
     * Retire du index tous les fichiers situés sous le répertoire donné
     * (suppression récursive), puis persiste et recalcule les tags.
     *
     * @param dir le répertoire supprimé
     */
    public void removeFilesUnder(File dir) {
        if (projectDir == null || dir == null) return;
        String relativePrefix = projectDir.toPath().relativize(dir.toPath()).toString();
        // Retirer toutes les entrées dont le chemin relatif commence par ce préfixe
        entries.removeIf(e -> e.relativePath.startsWith(relativePrefix));
        rebuildTagCounts();
        saveIndex();
    }

    /**
     * Gère le renommage d'un fichier : retire l'ancienne entrée et indexe la nouvelle.
     *
     * @param oldFile l'ancien fichier (avant renommage)
     * @param newFile le nouveau fichier (après renommage)
     */
    public void handleRename(File oldFile, File newFile) {
        if (projectDir == null) return;

        if (oldFile.isDirectory() || (newFile != null && newFile.isDirectory())) {
            // Pour un répertoire renommé, reconstruire tout l'index
            // car les chemins relatifs de tous les fichiers enfants changent.
            buildIndex(projectDir);
            return;
        }

        // Retirer l'ancienne entrée
        String oldRelativePath = projectDir.toPath().relativize(oldFile.toPath()).toString();
        entries.removeIf(e -> e.relativePath.equals(oldRelativePath));

        // Indexer le nouveau fichier
        if (newFile != null && newFile.exists() && newFile.getName().toLowerCase().endsWith(".md")) {
            indexFile(newFile.toPath());
        }

        rebuildTagCounts();
        saveIndex();
    }

    /**
     * Gère le déplacement de fichiers : retire les anciennes entrées et indexe
     * les fichiers au nouvel emplacement.
     *
     * @param movedFiles  les fichiers tels qu'ils étaient avant le déplacement
     * @param targetDir   le répertoire de destination
     */
    public void handleMove(List<File> movedFiles, File targetDir) {
        if (projectDir == null || movedFiles == null || targetDir == null) return;

        for (File oldFile : movedFiles) {
            String oldRelativePath = projectDir.toPath().relativize(oldFile.toPath()).toString();

            if (oldFile.isDirectory()) {
                // Retirer tout ce qui était sous ce dossier
                entries.removeIf(e -> e.relativePath.startsWith(oldRelativePath));
            } else {
                entries.removeIf(e -> e.relativePath.equals(oldRelativePath));
            }

            // Indexer au nouvel emplacement
            File newFile = new File(targetDir, oldFile.getName());
            if (newFile.exists()) {
                if (newFile.isDirectory()) {
                    indexDirectory(newFile);
                } else if (newFile.getName().toLowerCase().endsWith(".md")
                        && !newFile.getName().startsWith(".")) {
                    indexFile(newFile.toPath());
                }
            }
        }

        rebuildTagCounts();
        saveIndex();
    }

    /**
     * Gère la copie de fichiers : indexe les nouveaux fichiers copiés.
     *
     * @param copiedFiles les fichiers source (pour déterminer les noms)
     * @param targetDir   le répertoire de destination
     */
    public void handleCopy(List<File> copiedFiles, File targetDir) {
        if (projectDir == null || copiedFiles == null || targetDir == null) return;

        for (File srcFile : copiedFiles) {
            File newFile = new File(targetDir, srcFile.getName());
            if (newFile.exists()) {
                if (newFile.isDirectory()) {
                    indexDirectory(newFile);
                } else if (newFile.getName().toLowerCase().endsWith(".md")
                        && !newFile.getName().startsWith(".")) {
                    indexFile(newFile.toPath());
                }
            }
        }

        rebuildTagCounts();
        saveIndex();
    }

    /**
     * Indexe récursivement tous les fichiers .md d'un répertoire.
     */
    private void indexDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(this::indexFile);
        } catch (IOException e) {
            System.err.println("IndexService: error indexing directory " + dir + ": " + e.getMessage());
        }
    }

    /**
     * Recalcule les compteurs de tags à partir des entrées actuelles
     * et les trie par ordre décroissant d'occurrences.
     */
    private void rebuildTagCounts() {
        tagCounts.clear();
        for (IndexEntry entry : entries) {
            for (String tag : entry.tags) {
                String normalized = tag.toLowerCase().trim();
                if (!normalized.isEmpty()) {
                    tagCounts.merge(normalized, 1, Integer::sum);
                }
            }
        }
        sortTagCounts();
    }

    /**
     * Trie les compteurs de tags par nombre d'occurrences décroissant.
     */
    private void sortTagCounts() {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(tagCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        LinkedHashMap<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        tagCounts.clear();
        tagCounts.putAll(sortedMap);
    }

    /**
     * Signale la progression de l'indexation au callback enregistré.
     * La notification est envoyée sur le FX Application Thread.
     */
    private void reportProgress(int done, int total) {
        if (onProgress != null && total > 0) {
            double progress = (double) done / total;
            Platform.runLater(() -> onProgress.accept(progress));
        }
    }

    // ── Résultat de recherche ───────────────────────────────────

    /**
     * Résultat d'une recherche dans l'index.
     */
    public static class SearchResult {
        private final IndexEntry entry;
        private final String matchText;

        public SearchResult(IndexEntry entry, String matchText) {
            this.entry = entry;
            this.matchText = matchText;
        }

        public IndexEntry getEntry() { return entry; }
        public String getMatchText() { return matchText; }
    }

    // ── Indexation d'un fichier ─────────────────────────────────

    private void indexFile(Path path) {
        try {
            String content = Files.readString(path);
            String relativePath = projectDir.toPath().relativize(path).toString();
            String filename = path.getFileName().toString();

            IndexEntry entry = new IndexEntry(relativePath, filename);
            FrontMatter fm = FrontMatter.parse(content);
            if (fm != null) {
                entry.uuid = fm.getUuid();
                entry.title = fm.getTitle();
                entry.authors = new ArrayList<>(fm.getAuthors());
                entry.tags = new ArrayList<>(fm.getTags());
                entry.summary = fm.getSummary();
                entry.createdAt = fm.getCreatedAt();
                entry.draft = fm.isDraft();
                entry.links = new ArrayList<>(fm.getLinks());

                // Compter les tags
                for (String tag : fm.getTags()) {
                    String normalizedTag = tag.toLowerCase().trim();
                    if (!normalizedTag.isEmpty()) {
                        tagCounts.merge(normalizedTag, 1, Integer::sum);
                    }
                }
            }
            entries.add(entry);
        } catch (IOException e) {
            System.err.println("IndexService: error reading " + path + ": " + e.getMessage());
        }
    }

    // ── Sérialisation JSON (manuelle, sans dépendances) ─────────

    private void saveIndex() {
        if (projectDir == null) return;
        File indexFile = new File(projectDir, INDEX_FILENAME);
        String json = toJson();
        try {
            Files.writeString(indexFile.toPath(), json);
        } catch (IOException e) {
            System.err.println("IndexService: error saving index: " + e.getMessage());
        }
    }

    /**
     * Sérialise l'index complet en JSON.
     */
    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"projectDir\": ").append(jsonString(projectDir.getAbsolutePath())).append(",\n");

        // Entries
        sb.append("  \"entries\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            IndexEntry e = entries.get(i);
            sb.append("    {\n");
            sb.append("      \"relativePath\": ").append(jsonString(e.relativePath)).append(",\n");
            sb.append("      \"filename\": ").append(jsonString(e.filename)).append(",\n");
            sb.append("      \"uuid\": ").append(jsonString(e.uuid)).append(",\n");
            sb.append("      \"title\": ").append(jsonString(e.title)).append(",\n");
            sb.append("      \"authors\": ").append(jsonStringList(e.authors)).append(",\n");
            sb.append("      \"tags\": ").append(jsonStringList(e.tags)).append(",\n");
            sb.append("      \"summary\": ").append(jsonString(e.summary)).append(",\n");
            sb.append("      \"createdAt\": ").append(jsonString(e.createdAt)).append(",\n");
            sb.append("      \"draft\": ").append(e.draft).append(",\n");
            sb.append("      \"links\": ").append(jsonStringList(e.links)).append("\n");
            sb.append("    }");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Tag counts
        sb.append("  \"tagCounts\": {\n");
        int tagIdx = 0;
        int tagTotal = tagCounts.size();
        for (Map.Entry<String, Integer> tc : tagCounts.entrySet()) {
            sb.append("    ").append(jsonString(tc.getKey())).append(": ").append(tc.getValue());
            if (tagIdx < tagTotal - 1) sb.append(",");
            sb.append("\n");
            tagIdx++;
        }
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Parse le JSON de l'index. Parser minimaliste adapté au format produit
     * par {@link #toJson()}.
     */
    private void parseJson(String json) {
        entries.clear();
        tagCounts.clear();

        // Parse entries
        int entriesStart = json.indexOf("\"entries\"");
        if (entriesStart < 0) return;

        int arrayStart = json.indexOf('[', entriesStart);
        int arrayEnd = findMatchingBracket(json, arrayStart, '[', ']');
        if (arrayStart < 0 || arrayEnd < 0) return;

        String entriesBlock = json.substring(arrayStart + 1, arrayEnd);
        // Split by entry objects
        int depth = 0;
        int entryStart = -1;
        for (int i = 0; i < entriesBlock.length(); i++) {
            char c = entriesBlock.charAt(i);
            if (c == '{') {
                if (depth == 0) entryStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && entryStart >= 0) {
                    String entryJson = entriesBlock.substring(entryStart, i + 1);
                    IndexEntry entry = parseEntry(entryJson);
                    if (entry != null) entries.add(entry);
                    entryStart = -1;
                }
            }
        }

        // Parse tag counts
        int tagCountsStart = json.indexOf("\"tagCounts\"");
        if (tagCountsStart >= 0) {
            int objStart = json.indexOf('{', tagCountsStart);
            int objEnd = findMatchingBracket(json, objStart, '{', '}');
            if (objStart >= 0 && objEnd >= 0) {
                String tagBlock = json.substring(objStart + 1, objEnd);
                parseTagCounts(tagBlock);
            }
        }
    }

    private IndexEntry parseEntry(String json) {
        String relativePath = extractJsonStringValue(json, "relativePath");
        String filename = extractJsonStringValue(json, "filename");
        if (relativePath == null || filename == null) return null;

        IndexEntry entry = new IndexEntry(relativePath, filename);
        entry.uuid = extractJsonStringValue(json, "uuid");
        if (entry.uuid == null) entry.uuid = "";
        entry.title = extractJsonStringValue(json, "title");
        if (entry.title == null) entry.title = "";
        entry.authors = extractJsonStringList(json, "authors");
        entry.tags = extractJsonStringList(json, "tags");
        entry.summary = extractJsonStringValue(json, "summary");
        if (entry.summary == null) entry.summary = "";
        entry.createdAt = extractJsonStringValue(json, "createdAt");
        if (entry.createdAt == null) entry.createdAt = "";

        String draftStr = extractJsonRawValue(json, "draft");
        entry.draft = "true".equals(draftStr);
        entry.links = extractJsonStringList(json, "links");

        // Rebuild tag counts
        for (String tag : entry.tags) {
            String normalized = tag.toLowerCase().trim();
            if (!normalized.isEmpty()) {
                tagCounts.merge(normalized, 1, Integer::sum);
            }
        }

        return entry;
    }

    private void parseTagCounts(String block) {
        // Already rebuilt from entries, but let's also read to preserve order
        // Tag counts are rebuilt from entries during parseEntry, so this is optional
    }

    // ── JSON utility methods ────────────────────────────────────

    private static String jsonString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\")
                          .replace("\"", "\\\"")
                          .replace("\n", "\\n")
                          .replace("\r", "\\r")
                          .replace("\t", "\\t") + "\"";
    }

    private static String jsonStringList(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsonString(items.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String extractJsonStringValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return null;

        // Find the opening quote
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;

        // Find the closing quote (handle escaped quotes)
        int endQuote = startQuote + 1;
        while (endQuote < json.length()) {
            char c = json.charAt(endQuote);
            if (c == '\\') {
                endQuote += 2; // skip escaped char
                continue;
            }
            if (c == '"') break;
            endQuote++;
        }
        if (endQuote >= json.length()) return null;

        String raw = json.substring(startQuote + 1, endQuote);
        return raw.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }

    private static String extractJsonRawValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return null;

        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;

        int end = start;
        while (end < json.length() && !",}\n".contains(String.valueOf(json.charAt(end)))) end++;

        return json.substring(start, end).trim();
    }

    private static List<String> extractJsonStringList(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return new ArrayList<>();
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return new ArrayList<>();

        int bracketStart = json.indexOf('[', colonIdx);
        if (bracketStart < 0) return new ArrayList<>();
        int bracketEnd = findMatchingBracket(json, bracketStart, '[', ']');
        if (bracketEnd < 0) return new ArrayList<>();

        String listContent = json.substring(bracketStart + 1, bracketEnd).trim();
        if (listContent.isEmpty()) return new ArrayList<>();

        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < listContent.length()) {
            int quote1 = listContent.indexOf('"', i);
            if (quote1 < 0) break;
            int quote2 = quote1 + 1;
            while (quote2 < listContent.length()) {
                char c = listContent.charAt(quote2);
                if (c == '\\') {
                    quote2 += 2;
                    continue;
                }
                if (c == '"') break;
                quote2++;
            }
            if (quote2 >= listContent.length()) break;
            String val = listContent.substring(quote1 + 1, quote2)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            result.add(val);
            i = quote2 + 1;
        }
        return result;
    }

    private static int findMatchingBracket(String str, int openPos, char open, char close) {
        if (openPos < 0 || openPos >= str.length() || str.charAt(openPos) != open) return -1;
        int depth = 1;
        boolean inString = false;
        for (int i = openPos + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && inString) {
                i++; // skip escaped char
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }
}
