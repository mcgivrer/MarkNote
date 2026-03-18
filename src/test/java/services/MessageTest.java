package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la classe Message.
 */
class MessageTest {

    @Test
    @DisplayName("Création d'un message USER avec timestamp automatique")
    void testCreateUserMessage() {
        Message message = new Message(MessageRole.USER, "Hello, world!");
        
        assertEquals(MessageRole.USER, message.getRole());
        assertEquals("Hello, world!", message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Création d'un message ASSISTANT")
    void testCreateAssistantMessage() {
        Message message = new Message(MessageRole.ASSISTANT, "How can I help?");
        
        assertEquals(MessageRole.ASSISTANT, message.getRole());
        assertEquals("How can I help?", message.getContent());
    }

    @Test
    @DisplayName("Création d'un message SYSTEM")
    void testCreateSystemMessage() {
        Message message = new Message(MessageRole.SYSTEM, "You are a helpful assistant.");
        
        assertEquals(MessageRole.SYSTEM, message.getRole());
        assertEquals("You are a helpful assistant.", message.getContent());
    }

    @Test
    @DisplayName("Création d'un message avec timestamp spécifique")
    void testCreateMessageWithTimestamp() {
        LocalDateTime specificTime = LocalDateTime.of(2026, 3, 15, 10, 30, 0);
        Message message = new Message(MessageRole.USER, "Test", specificTime);
        
        assertEquals(specificTime, message.getTimestamp());
    }

    @Test
    @DisplayName("Modification du contenu d'un message")
    void testSetContent() {
        Message message = new Message(MessageRole.ASSISTANT, "Initial content");
        message.setContent("Updated content");
        
        assertEquals("Updated content", message.getContent());
    }

    @Test
    @DisplayName("Ajout de contenu au message (streaming)")
    void testAppendContent() {
        Message message = new Message(MessageRole.ASSISTANT, "Hello");
        message.appendContent(" world");
        message.appendContent("!");
        
        assertEquals("Hello world!", message.getContent());
    }

    @Test
    @DisplayName("Conversion en JSON pour API")
    void testToApiJson() {
        Message message = new Message(MessageRole.USER, "Test message");
        String json = message.toApiJson();
        
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("\"content\":\"Test message\""));
    }

    @Test
    @DisplayName("Conversion en JSON pour API avec caractères spéciaux")
    void testToApiJsonWithSpecialChars() {
        Message message = new Message(MessageRole.USER, "Line1\nLine2\t\"quoted\"");
        String json = message.toApiJson();
        
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\t"));
        assertTrue(json.contains("\\\"quoted\\\""));
    }

    @Test
    @DisplayName("Conversion en JSON complet pour export")
    void testToJson() {
        LocalDateTime time = LocalDateTime.of(2026, 3, 15, 10, 30, 0);
        Message message = new Message(MessageRole.USER, "Test", time);
        String json = message.toJson();
        
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("\"content\":\"Test\""));
        assertTrue(json.contains("\"timestamp\":\"2026-03-15T10:30:00\""));
    }

    @Test
    @DisplayName("Parsing d'un message depuis JSON")
    void testFromJson() {
        String json = "{\"role\":\"user\",\"content\":\"Hello\",\"timestamp\":\"2026-03-15T10:30:00\"}";
        Message message = Message.fromJson(json);
        
        assertNotNull(message);
        assertEquals(MessageRole.USER, message.getRole());
        assertEquals("Hello", message.getContent());
        assertEquals(LocalDateTime.of(2026, 3, 15, 10, 30, 0), message.getTimestamp());
    }

    @Test
    @DisplayName("Parsing d'un message JSON avec caractères échappés")
    void testFromJsonWithEscapedChars() {
        String json = "{\"role\":\"assistant\",\"content\":\"Line1\\nLine2\",\"timestamp\":\"2026-03-15T10:30:00\"}";
        Message message = Message.fromJson(json);
        
        assertNotNull(message);
        assertEquals("Line1\nLine2", message.getContent());
    }

    @Test
    @DisplayName("Parsing d'un JSON invalide retourne null")
    void testFromJsonInvalid() {
        Message message = Message.fromJson("invalid json");
        assertNull(message);
    }

    @Test
    @DisplayName("Parsing d'un JSON avec rôle invalide retourne null")
    void testFromJsonInvalidRole() {
        String json = "{\"role\":\"invalid\",\"content\":\"Test\",\"timestamp\":\"2026-03-15T10:30:00\"}";
        Message message = Message.fromJson(json);
        assertNull(message);
    }

    @Test
    @DisplayName("toString retourne un format lisible")
    void testToString() {
        Message message = new Message(MessageRole.USER, "Hello world!");
        String str = message.toString();
        
        assertTrue(str.contains("USER"));
        assertTrue(str.contains("Hello world!"));
    }

    @Test
    @DisplayName("toString tronque les messages longs")
    void testToStringTruncatesLongMessages() {
        String longContent = "A".repeat(100);
        Message message = new Message(MessageRole.USER, longContent);
        String str = message.toString();
        
        assertTrue(str.contains("..."));
        assertTrue(str.length() < 150); // Format complet mais tronqué
    }

    @Test
    @DisplayName("Roundtrip JSON encode/decode")
    void testJsonRoundtrip() {
        Message original = new Message(MessageRole.ASSISTANT, "Complex\ncontent\twith \"quotes\"");
        String json = original.toJson();
        Message decoded = Message.fromJson(json);
        
        assertNotNull(decoded);
        assertEquals(original.getRole(), decoded.getRole());
        assertEquals(original.getContent(), decoded.getContent());
    }
}
