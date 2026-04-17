package services;

import config.LLMConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la classe LLMService.
 * Note: Ces tests ne nécessitent pas de serveur LLM réel.
 */
class LLMServiceTest {

    private LLMConfig config;
    private LLMService service;

    @BeforeEach
    void setUp() {
        config = new LLMConfig();
        config.setEndpointUrl("http://localhost:19999"); // Port volontairement indisponible
        config.setModel("llama3.2");
        config.setTimeout(5);
        config.setMaxRetries(0); // Pas de retry pour les tests
        service = new LLMService(config);
    }

    @Test
    @DisplayName("Création du service avec configuration valide")
    void testServiceCreation() {
        assertNotNull(service);
        assertFalse(service.isProcessing());
    }

    @Test
    @DisplayName("isProcessing retourne false initialement")
    void testIsProcessingInitially() {
        assertFalse(service.isProcessing());
    }

    @Test
    @DisplayName("cancelRequest ne lève pas d'exception sans requête active")
    void testCancelRequestWithoutActiveRequest() {
        assertDoesNotThrow(() -> service.cancelRequest());
    }

    @Test
    @DisplayName("testConnection échoue gracieusement si serveur indisponible")
    void testConnectionFailsGracefully() {
        // Le serveur est probablement indisponible en environnement de test
        boolean result = service.testConnection();
        // On ne vérifie pas le résultat car ça dépend de l'environnement
        // L'important est que ça ne lève pas d'exception
        assertFalse(result); // Généralement false sans serveur
    }

    @Test
    @DisplayName("getAvailableModels retourne liste vide si serveur indisponible")
    void testGetAvailableModelsEmpty() {
        List<String> models = service.getAvailableModels();
        assertNotNull(models);
        // Liste vide car serveur indisponible
        assertTrue(models.isEmpty());
    }

    @Test
    @DisplayName("sendPromptAsync appelle le callback d'erreur si connexion échoue")
    void testSendPromptAsyncConnectionError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        List<Message> messages = new ArrayList<>();
        messages.add(new Message(MessageRole.USER, "Hello"));

        service.sendPromptAsync(
                messages,
                chunk -> {}, // onChunk
                () -> latch.countDown(), // onComplete
                error -> {
                    errorCalled.set(true);
                    errorRef.set(error);
                    latch.countDown();
                }
        );

        // Attendre max 15 secondes
        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Timeout waiting for response");
        assertTrue(errorCalled.get(), "Error callback should have been called");
        assertNotNull(errorRef.get());
    }

    @Test
    @DisplayName("sendPromptSync lève exception si connexion échoue")
    void testSendPromptSyncConnectionError() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(MessageRole.USER, "Hello"));

        assertThrows(Exception.class, () -> {
            service.sendPromptSync(messages);
        });
    }

    @Test
    @DisplayName("Configuration avec contexte système")
    void testConfigWithSystemContext() {
        config.setSystemContext("You are a helpful assistant.");
        LLMService serviceWithContext = new LLMService(config);
        
        assertNotNull(serviceWithContext);
    }

    @Test
    @DisplayName("Configuration avec clé API")
    void testConfigWithApiKey() {
        config.setApiKey("test-api-key");
        LLMService serviceWithKey = new LLMService(config);
        
        assertNotNull(serviceWithKey);
    }

    @Test
    @DisplayName("Multiple messages dans la conversation")
    void testMultipleMessages() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(MessageRole.SYSTEM, "You are helpful."));
        messages.add(new Message(MessageRole.USER, "Hello"));
        messages.add(new Message(MessageRole.ASSISTANT, "Hi! How can I help?"));
        messages.add(new Message(MessageRole.USER, "Tell me a joke"));

        service.sendPromptAsync(
                messages,
                chunk -> {},
                () -> latch.countDown(),
                error -> latch.countDown()
        );

        latch.await(15, TimeUnit.SECONDS);
        // Test passe si pas d'exception
    }

    @Test
    @DisplayName("Annulation de requête en cours")
    void testCancelDuringRequest() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        List<Message> messages = new ArrayList<>();
        messages.add(new Message(MessageRole.USER, "Tell me a very long story"));

        service.sendPromptAsync(
                messages,
                chunk -> startLatch.countDown(),
                () -> completed.set(true),
                error -> errorOccurred.set(true)
        );

        // Annuler immédiatement
        service.cancelRequest();

        // Attendre un peu
        Thread.sleep(500);

        // La requête devrait être annulée ou avoir échoué
        // On ne peut pas garantir l'état exact car cela dépend du timing
        assertFalse(service.isProcessing());
    }
}
