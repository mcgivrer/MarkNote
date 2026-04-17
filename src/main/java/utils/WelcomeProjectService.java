package utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Installs a welcome project in the user's Documents folder.
 */
public class WelcomeProjectService {

    private static final String LOG_SOURCE = "WelcomeProjectService";
    private static final String WELCOME_DIR_NAME = "MarkNote Welcome";
    private static final String ILLUSTRATIONS_DIR_NAME = "illustrations";

    private static final String USER_GUIDE_RESOURCE = "user-guide-en.md";
    private static final String TIPS_RESOURCE = "tips-and-tricks-en.md";

    private static final List<String> ILLUSTRATIONS = List.of(
            "welcome-page.svg",
            "main-interface.svg",
            "front-matter-panel.svg",
            "view-menu.svg",
            "panel-detach.svg",
            "file-menu.svg",
            "drag-drop-editor.svg",
            "front-matter-links.svg",
            "project-explorer.svg",
            "image-preview.svg",
            "search-box.svg",
            "tag-cloud.svg",
            "network-diagram.svg",
            "status-bar.svg",
            "preview-front-matter.svg",
            "llm-chat-panel.svg",
            "themes.svg",
            "keyboard-shortcuts.svg");

    private final LogService log = LogService.getInstance();
    private final Path userHome;

    public WelcomeProjectService() {
        this(Path.of(System.getProperty("user.home")));
    }

    WelcomeProjectService(Path userHome) {
        this.userHome = userHome;
    }

    public void installIfMissing(String appVersion) {
        try {
            Path documentsDir = resolveDocumentsDirectory();
            Path welcomeDir = documentsDir.resolve(WELCOME_DIR_NAME);
            if (Files.exists(welcomeDir)) {
                return;
            }

            Files.createDirectories(welcomeDir.resolve(ILLUSTRATIONS_DIR_NAME));

            copyResource(USER_GUIDE_RESOURCE, welcomeDir.resolve("MarkNote " + appVersion + " - User Guide.md"));
            copyResource(TIPS_RESOURCE, welcomeDir.resolve("MarkNote " + appVersion + " - Tips & Tricks.md"));
            for (String illustration : ILLUSTRATIONS) {
                copyResource("illustrations/" + illustration,
                        welcomeDir.resolve(ILLUSTRATIONS_DIR_NAME).resolve(illustration));
            }
            log.info(LOG_SOURCE, "Welcome project installed in " + welcomeDir);
        } catch (IOException e) {
            log.warn(LOG_SOURCE, "Unable to install welcome project: " + e.getMessage());
        }
    }

    private Path resolveDocumentsDirectory() {
        Path documentsDir = userHome.resolve("Documents");
        try {
            Files.createDirectories(documentsDir);
            return documentsDir;
        } catch (IOException e) {
            return userHome;
        }
    }

    private void copyResource(String resourcePath, Path destination) throws IOException {
        try (InputStream stream = WelcomeProjectService.class.getResourceAsStream("/welcome-project/" + resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing embedded resource: /welcome-project/" + resourcePath);
            }
            Files.copy(stream, destination);
        }
    }
}
