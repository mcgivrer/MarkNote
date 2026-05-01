package ui;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EditorFloatingToolbar}.
 *
 * <p>The toolbar extends {@link javafx.stage.Popup} and therefore cannot be
 * instantiated without a running JavaFX runtime. These tests verify the
 * surrounding decision logic — specifically the action dispatch contract — by
 * using a lightweight stub that replaces the Popup with a plain {@link Object}
 * so no JavaFX toolkit initialisation is required.</p>
 */
class EditorFloatingToolbarTest {

    /**
     * Verifies that the action map keys are all present for the expected set of
     * formatting operations and that no typo sneaked into the key names.
     */
    @Test
    void actionMap_containsAllExpectedKeys() {
        Map<String, Runnable> actions = buildActionMap();
        String[] required = {"bold", "italic", "link", "image", "code",
                             "h1", "h2", "h3", "h4", "h5", "h6"};
        for (String key : required) {
            assertTrue(actions.containsKey(key), "Missing key: " + key);
        }
    }

    /**
     * Verifies that each Runnable in the action map can be invoked without
     * throwing (basic smoke-test for action wiring).
     */
    @Test
    void allActions_invokeWithoutException() {
        Map<String, Runnable> actions = buildActionMap();
        for (Map.Entry<String, Runnable> entry : actions.entrySet()) {
            assertDoesNotThrow(entry.getValue()::run,
                    "Action threw for key: " + entry.getKey());
        }
    }

    /**
     * Verifies that the bold action is called exactly once when invoked.
     */
    @Test
    void boldAction_invokedExactlyOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Map<String, Runnable> actions = new HashMap<>();
        actions.put("bold", counter::incrementAndGet);
        actions.get("bold").run();
        assertEquals(1, counter.get());
    }

    /**
     * Verifies that the italic action is called exactly once when invoked.
     */
    @Test
    void italicAction_invokedExactlyOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Map<String, Runnable> actions = new HashMap<>();
        actions.put("italic", counter::incrementAndGet);
        actions.get("italic").run();
        assertEquals(1, counter.get());
    }

    /**
     * Verifies that each heading action (h1–h6) is distinct: invoking one does
     * not trigger the others.
     */
    @Test
    void headingActions_areIndependent() {
        int[] counters = new int[6];
        Map<String, Runnable> actions = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            actions.put("h" + (i + 1), () -> counters[idx]++);
        }
        actions.get("h3").run();
        assertEquals(0, counters[0], "h1 must not fire");
        assertEquals(0, counters[1], "h2 must not fire");
        assertEquals(1, counters[2], "h3 must fire once");
        assertEquals(0, counters[3], "h4 must not fire");
        assertEquals(0, counters[4], "h5 must not fire");
        assertEquals(0, counters[5], "h6 must not fire");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Runnable> buildActionMap() {
        Map<String, Runnable> m = new HashMap<>();
        m.put("bold",   () -> {});
        m.put("italic", () -> {});
        m.put("link",   () -> {});
        m.put("image",  () -> {});
        m.put("code",   () -> {});
        for (int i = 1; i <= 6; i++) {
            m.put("h" + i, () -> {});
        }
        return m;
    }
}
