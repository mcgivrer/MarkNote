package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour RemoteConnectorFactory.
 */
class RemoteConnectorFactoryTest {

    @Test
    void testDetectGitHub() {
        assertEquals("github", RemoteConnectorFactory.detectPlatform("https://github.com/user/repo.git"));
        assertEquals("github", RemoteConnectorFactory.detectPlatform("git@github.com:user/repo.git"));
        assertEquals("github", RemoteConnectorFactory.detectPlatform("ssh://git@github.com/user/repo.git"));
    }

    @Test
    void testDetectGitLabPublic() {
        assertEquals("gitlab", RemoteConnectorFactory.detectPlatform("https://gitlab.com/user/repo.git"));
        assertEquals("gitlab", RemoteConnectorFactory.detectPlatform("git@gitlab.com:user/repo.git"));
    }

    @Test
    void testDetectGitLabSelfHosted() {
        assertEquals("gitlab", RemoteConnectorFactory.detectPlatform("https://gitlab.example.com/user/repo.git"));
        assertEquals("gitlab", RemoteConnectorFactory.detectPlatform("https://my-gitlab.internal/user/repo.git"));
    }

    @Test
    void testDetectGitea() {
        assertEquals("gitea", RemoteConnectorFactory.detectPlatform("https://gitea.example.com/user/repo.git"));
        assertEquals("gitea", RemoteConnectorFactory.detectPlatform("https://code.company.com/user/repo.git"));
    }

    @Test
    void testDetectPlatformWithInvalidUrl() {
        assertNull(RemoteConnectorFactory.detectPlatform(""));
        assertNull(RemoteConnectorFactory.detectPlatform(null));
        assertNull(RemoteConnectorFactory.detectPlatform("not-a-url"));
    }

    @Test
    void testCreateGitHubConnector() {
        RemoteConnector connector = RemoteConnectorFactory.create("https://github.com/user/repo.git", "token123");
        assertNotNull(connector);
        assertTrue(connector instanceof GitHubConnector);
        assertEquals("github", connector.platform());
    }

    @Test
    void testCreateGitLabConnector() {
        RemoteConnector connector = RemoteConnectorFactory.create("https://gitlab.com/user/repo.git", "token123");
        assertNotNull(connector);
        assertTrue(connector instanceof GitLabConnector);
        assertEquals("gitlab", connector.platform());
    }

    @Test
    void testCreateGitLabSelfHostedConnector() {
        RemoteConnector connector = RemoteConnectorFactory.create("https://gitlab.example.com/user/repo.git", "token123");
        assertNotNull(connector);
        assertTrue(connector instanceof GitLabConnector);
        assertEquals("gitlab", connector.platform());
    }

    @Test
    void testCreateGiteaConnector() {
        RemoteConnector connector = RemoteConnectorFactory.create("https://gitea.example.com/user/repo.git", "token123");
        assertNotNull(connector);
        assertTrue(connector instanceof GiteaConnector);
        assertEquals("gitea", connector.platform());
    }

    @Test
    void testCreateWithEmptyUrl() {
        assertNull(RemoteConnectorFactory.create("", "token123"));
        assertNull(RemoteConnectorFactory.create(null, "token123"));
    }

    @Test
    void testCreateWithEmptyToken() {
        assertNull(RemoteConnectorFactory.create("https://github.com/user/repo.git", ""));
        assertNull(RemoteConnectorFactory.create("https://github.com/user/repo.git", null));
    }

    @Test
    void testCreateWithInvalidUrl() {
        assertNull(RemoteConnectorFactory.create("not-a-valid-url", "token123"));
    }

    @Test
    void testGitHubSSHUrl() {
        RemoteConnector connector = RemoteConnectorFactory.create("git@github.com:user/repo.git", "token123");
        assertNotNull(connector);
        assertEquals("github", connector.platform());
    }

    @Test
    void testGitLabSSHUrl() {
        RemoteConnector connector = RemoteConnectorFactory.create("git@gitlab.com:user/repo.git", "token123");
        assertNotNull(connector);
        assertEquals("gitlab", connector.platform());
    }
}
