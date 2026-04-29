package utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour la classe Debouncer.
 */
class DebouncerTest {
    
    private Debouncer debouncer;
    
    @BeforeEach
    void setUp() {
        debouncer = new Debouncer(100); // 100ms pour les tests
    }
    
    @AfterEach
    void tearDown() {
        debouncer.shutdown();
    }
    
    @Test
    void testDebounceExecutesAfterDelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] executed = {false};
        
        debouncer.debounce(() -> {
            executed[0] = true;
            latch.countDown();
        });
        
        // L'action ne doit pas s'être exécutée immédiatement
        assertFalse(executed[0]);
        
        // Attendre le délai
        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
        assertTrue(executed[0]);
    }
    
    @Test
    void testDebounceCancelsPreviousCall() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> executionOrder = new ArrayList<>();
        
        // Premier appel
        debouncer.debounce(() -> {
            executionOrder.add(1);
        });
        
        // Deuxième appel avant le délai
        Thread.sleep(50);
        debouncer.debounce(() -> {
            executionOrder.add(2);
            latch.countDown();
        });
        
        // Attendre
        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
        
        // Seule la deuxième action doit s'être exécutée
        assertEquals(List.of(2), executionOrder);
    }
    
    @Test
    void testMultipleConsecutiveCallsOnlyLastExecutes() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> executions = new ArrayList<>();
        
        // Plusieurs appels rapides
        for (int i = 0; i < 10; i++) {
            final int value = i;
            debouncer.debounce(() -> {
                executions.add(value);
                if (value == 9) {
                    latch.countDown();
                }
            });
            Thread.sleep(20);
        }
        
        // Attendre
        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
        
        // Seule la dernière action doit s'être exécutée
        assertEquals(List.of(9), executions);
    }
    
    @Test
    void testCancelPreventsExecution() throws InterruptedException {
        boolean[] executed = {false};
        
        debouncer.debounce(() -> {
            executed[0] = true;
        });
        
        // Annuler avant le délai
        debouncer.cancel();
        
        // Attendre plus longtemps que le délai
        Thread.sleep(150);
        
        assertFalse(executed[0]);
    }
    
    @Test
    void testShutdownPreventsFurtherExecution() throws InterruptedException {
        boolean[] executed = {false};
        
        debouncer.debounce(() -> {
            executed[0] = true;
        });
        
        debouncer.shutdown();
        
        Thread.sleep(150);
        
        assertFalse(executed[0]);
    }
}
