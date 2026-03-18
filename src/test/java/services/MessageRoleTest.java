package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour l'énumération MessageRole.
 */
class MessageRoleTest {

    @Test
    @DisplayName("Enum contient les trois rôles attendus")
    void testEnumValues() {
        MessageRole[] values = MessageRole.values();
        
        assertEquals(3, values.length);
        assertEquals(MessageRole.USER, MessageRole.valueOf("USER"));
        assertEquals(MessageRole.ASSISTANT, MessageRole.valueOf("ASSISTANT"));
        assertEquals(MessageRole.SYSTEM, MessageRole.valueOf("SYSTEM"));
    }

    @Test
    @DisplayName("Conversion en minuscules pour API")
    void testLowerCaseName() {
        assertEquals("user", MessageRole.USER.name().toLowerCase());
        assertEquals("assistant", MessageRole.ASSISTANT.name().toLowerCase());
        assertEquals("system", MessageRole.SYSTEM.name().toLowerCase());
    }

    @Test
    @DisplayName("valueOf avec valeur invalide lève une exception")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            MessageRole.valueOf("INVALID");
        });
    }
}
