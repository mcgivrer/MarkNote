package utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WelcomeProjectServiceTest {

    @TempDir
    Path tempHome;

    @Test
    @DisplayName("Install welcome project in Documents when missing")
    void installWelcomeProjectWhenMissing() {
        WelcomeProjectService service = new WelcomeProjectService(tempHome);
        service.installIfMissing("0.1.4");

        Path welcomeDir = tempHome.resolve("Documents").resolve("MarkNote Welcome");
        assertTrue(Files.exists(welcomeDir));
        assertTrue(Files.exists(welcomeDir.resolve("MarkNote 0.1.4 - User Guide.md")));
        assertTrue(Files.exists(welcomeDir.resolve("MarkNote 0.1.4 - Tips & Tricks.md")));
        assertTrue(Files.exists(welcomeDir.resolve("illustrations").resolve("welcome-page.svg")));
    }

    @Test
    @DisplayName("Do not override existing welcome project")
    void doNotOverrideExistingWelcomeProject() throws IOException {
        Path welcomeDir = tempHome.resolve("Documents").resolve("MarkNote Welcome");
        Files.createDirectories(welcomeDir);
        Path marker = welcomeDir.resolve("existing.md");
        Files.writeString(marker, "keep me");

        WelcomeProjectService service = new WelcomeProjectService(tempHome);
        service.installIfMissing("0.1.4");

        assertTrue(Files.exists(marker));
        assertFalse(Files.exists(welcomeDir.resolve("MarkNote 0.1.4 - User Guide.md")));
    }
}
