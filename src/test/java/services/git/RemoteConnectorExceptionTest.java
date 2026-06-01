package services.git;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour RemoteConnectorException.
 */
class RemoteConnectorExceptionTest {

    @Test
    void testConstructorWithMessageAndCause() {
        Exception cause = new RuntimeException("root cause");
        RemoteConnectorException ex = new RemoteConnectorException("Test error", cause);
        
        assertEquals("Test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals(0, ex.getStatusCode());
        assertNull(ex.getApiMessage());
    }

    @Test
    void testConstructorWithStatusCodeAndMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(404, "Resource not found");
        
        assertTrue(ex.getMessage().contains("404"));
        assertTrue(ex.getMessage().contains("Resource not found"));
        assertEquals(404, ex.getStatusCode());
        assertEquals("Resource not found", ex.getApiMessage());
    }

    @Test
    void testConstructorWithStatusCodeMessageAndCause() {
        Exception cause = new RuntimeException("network error");
        RemoteConnectorException ex = new RemoteConnectorException(500, "Server error", cause);
        
        assertTrue(ex.getMessage().contains("500"));
        assertTrue(ex.getMessage().contains("Server error"));
        assertEquals(500, ex.getStatusCode());
        assertEquals("Server error", ex.getApiMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void testUnauthorizedMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(401, "Invalid token");
        assertTrue(ex.getMessage().contains("401 Unauthorized"));
        assertTrue(ex.getMessage().contains("Invalid token"));
    }

    @Test
    void testForbiddenMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(403, "Access denied");
        assertTrue(ex.getMessage().contains("403 Forbidden"));
        assertTrue(ex.getMessage().contains("Access denied"));
    }

    @Test
    void testNotFoundMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(404, "Repository not found");
        assertTrue(ex.getMessage().contains("404 Not Found"));
        assertTrue(ex.getMessage().contains("Repository not found"));
    }

    @Test
    void testRateLimitMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(429, "Rate limit exceeded");
        assertTrue(ex.getMessage().contains("429 Too Many Requests"));
        assertTrue(ex.getMessage().contains("Rate limit exceeded"));
    }

    @Test
    void testServerErrorMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(500, "Internal error");
        assertTrue(ex.getMessage().contains("500"));
        assertTrue(ex.getMessage().contains("Erreur serveur"));
        assertTrue(ex.getMessage().contains("Internal error"));
    }

    @Test
    void testUnknownStatusCode() {
        RemoteConnectorException ex = new RemoteConnectorException(418, "I'm a teapot");
        assertTrue(ex.getMessage().contains("418"));
        assertTrue(ex.getMessage().contains("I'm a teapot"));
    }

    @Test
    void testNullApiMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(404, null);
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("404"));
        assertNull(ex.getApiMessage());
    }

    @Test
    void testBlankApiMessage() {
        RemoteConnectorException ex = new RemoteConnectorException(404, "   ");
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("404"));
    }
}
