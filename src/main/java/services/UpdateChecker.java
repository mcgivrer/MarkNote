package services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/mcgivrer/MarkNote/releases/latest";

    public record VersionInfo(String tagName, String downloadUrl, String assetName) {}

    public static VersionInfo checkForUpdate(String currentVersion) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            JSONObject json = (JSONObject) new JSONParser().parse(resp.body());
            String tagName = (String) json.get("tag_name");
            if (tagName == null || !isNewer(tagName, currentVersion)) return null;

            JSONArray assets = (JSONArray) json.get("assets");
            JSONObject asset = selectAsset(assets);
            if (asset == null) return null;

            return new VersionInfo(tagName,
                    (String) asset.get("browser_download_url"),
                    (String) asset.get("name"));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNewer(String remote, String current) {
        int[] r = parseParts(remote.startsWith("v") ? remote.substring(1) : remote);
        int[] c = parseParts(current.startsWith("v") ? current.substring(1) : current);
        int len = Math.max(r.length, c.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (rv > cv) return true;
            if (rv < cv) return false;
        }
        return false;
    }

    private static int[] parseParts(String version) {
        String[] parts = version.split("\\.");
        int[] ints = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                ints[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                ints[i] = 0;
            }
        }
        return ints;
    }

    private static JSONObject selectAsset(JSONArray assets) {
        if (assets == null) return null;
        String os = System.getProperty("os.name").toLowerCase();
        String ext = os.contains("win") ? ".exe" : os.contains("mac") ? ".dmg" : ".deb";
        for (Object o : assets) {
            JSONObject a = (JSONObject) o;
            String name = (String) a.get("name");
            String state = (String) a.get("state");
            if ("uploaded".equals(state) && name != null && name.endsWith(ext)) {
                return a;
            }
        }
        return null;
    }
}
