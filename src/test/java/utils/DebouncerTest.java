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
        debouncer = new Debouncer(500); // 500ms — wide margin against CI scheduler jitter
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
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
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

        // Deuxième appel bien avant le délai (100ms << 500ms)
        Thread.sleep(100);
        debouncer.debounce(() -> {
            executionOrder.add(2);
            latch.countDown();
        });

        // Attendre
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

        // Seule la deuxième action doit s'être exécutée
        assertEquals(List.of(2), executionOrder);
    }

    @Test
    void testMultipleConsecutiveCallsOnlyLastExecutes() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> executions = new ArrayList<>();

        // Plusieurs appels rapides (30ms << 500ms debounce, ratio 16x)
        for (int i = 0; i < 10; i++) {
            final int value = i;
            debouncer.debounce(() -> {
                executions.add(value);
                if (value == 9) {
                    latch.countDown();
                }
            });
            Thread.sleep(30);
        }

        // Attendre (10*30ms calls + 500ms debounce + buffer)
        assertTrue(latch.await(1500, TimeUnit.MILLISECONDS));

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
        Thread.sleep(700);

        assertFalse(executed[0]);
    }

    @Test
    void testShutdownPreventsFurtherExecution() throws InterruptedException {
        boolean[] executed = {false};

        debouncer.debounce(() -> {
            executed[0] = true;
        });

        debouncer.shutdown();

        Thread.sleep(700);

        assertFalse(executed[0]);
    }
}
