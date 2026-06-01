package services.git;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour le record RemoteRepo.
 */
class RemoteRepoTest {

    @Test
    void testValidRemoteRepo() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "my-repo",
                "https://github.com/user/my-repo.git",
                "A test repository",
                true,
                "main"
        );

        assertEquals("my-repo", repo.name());
        assertEquals("https://github.com/user/my-repo.git", repo.cloneUrl());
        assertEquals("A test repository", repo.description());
        assertTrue(repo.isPrivate());
        assertEquals("main", repo.defaultBranch());
    }

    @Test
    void testNullDescription() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "my-repo",
                "https://github.com/user/my-repo.git",
                null,
                false,
                "main"
        );

        assertEquals("", repo.description());
    }

    @Test
    void testNullDefaultBranch() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "my-repo",
                "https://github.com/user/my-repo.git",
                "Description",
                false,
                null
        );

        assertEquals("main", repo.defaultBranch());
    }

    @Test
    void testBlankDefaultBranch() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "my-repo",
                "https://github.com/user/my-repo.git",
                "Description",
                false,
                "   "
        );

        assertEquals("main", repo.defaultBranch());
    }

    @Test
    void testNullName() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RemoteConnector.RemoteRepo(
                    null,
                    "https://github.com/user/repo.git",
                    "Description",
                    false,
                    "main"
            );
        });
    }

    @Test
    void testBlankName() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RemoteConnector.RemoteRepo(
                    "   ",
                    "https://github.com/user/repo.git",
                    "Description",
                    false,
                    "main"
            );
        });
    }

    @Test
    void testNullCloneUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RemoteConnector.RemoteRepo(
                    "my-repo",
                    null,
                    "Description",
                    false,
                    "main"
            );
        });
    }

    @Test
    void testBlankCloneUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RemoteConnector.RemoteRepo(
                    "my-repo",
                    "   ",
                    "Description",
                    false,
                    "main"
            );
        });
    }

    @Test
    void testPublicRepository() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "public-repo",
                "https://github.com/user/public-repo.git",
                "Public repository",
                false,
                "main"
        );

        assertFalse(repo.isPrivate());
    }

    @Test
    void testPrivateRepository() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "private-repo",
                "https://github.com/user/private-repo.git",
                "Private repository",
                true,
                "main"
        );

        assertTrue(repo.isPrivate());
    }

    @Test
    void testCustomDefaultBranch() {
        RemoteConnector.RemoteRepo repo = new RemoteConnector.RemoteRepo(
                "my-repo",
                "https://github.com/user/my-repo.git",
                "Description",
                false,
                "develop"
        );

        assertEquals("develop", repo.defaultBranch());
    }
}
