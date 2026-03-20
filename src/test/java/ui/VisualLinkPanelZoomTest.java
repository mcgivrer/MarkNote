package ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the zoom calculation logic in {@link ZoomCalculator}.
 *
 * <p>All tests exercise {@link ZoomCalculator#computeNewZoom} as a pure,
 * JavaFX-free function. This avoids the need for a running JavaFX runtime and
 * makes the suite fast and portable.</p>
 *
 * <p>The primary motivation for these tests is the Linux scroll-wheel bug:
 * on Linux, {@code ScrollEvent.getDeltaY()} can return {@code 0.0} for
 * physical mouse-wheel rotations. The previous code treated every zero-delta
 * event as a zoom-out step, making zoom-in impossible. The fixed
 * {@link ZoomCalculator#computeNewZoom} method falls back to
 * {@code textDeltaY} when {@code deltaY} is zero, and ignores the event
 * completely when both are zero.</p>
 */
public class VisualLinkPanelZoomTest {

    // ── constants that mirror VisualLinkPanel's zoom configuration ─────────
    private static final double FACTOR  = ZoomCalculator.ZOOM_FACTOR; // 1.15
    private static final double MIN     = ZoomCalculator.ZOOM_MIN;    // 0.1
    private static final double MAX     = ZoomCalculator.ZOOM_MAX;    // 8.0
    private static final double CURRENT = 1.0;
    private static final double DELTA   = 1e-9;

    /** Helper: call computeNewZoom with the standard constants. */
    private static double zoom(double deltaY, double textDeltaY, double current) {
        return ZoomCalculator.computeNewZoom(deltaY, textDeltaY, current, FACTOR, MIN, MAX);
    }

    // ── normal scroll (getDeltaY() != 0) ──────────────────────────────────

    @Test
    void scrollUp_positiveDelta_zoomsIn() {
        double result = zoom(40.0, 0.0, CURRENT);
        assertEquals(CURRENT * FACTOR, result, DELTA,
                "positive deltaY must produce a zoom-in (multiply by factor)");
    }

    @Test
    void scrollDown_negativeDelta_zoomsOut() {
        double result = zoom(-40.0, 0.0, CURRENT);
        assertEquals(CURRENT / FACTOR, result, DELTA,
                "negative deltaY must produce a zoom-out (divide by factor)");
    }

    // ── Linux fallback: getDeltaY() == 0, use textDeltaY ─────────────────

    @Test
    void linuxScrollUp_zeroDelta_positiveTextDelta_zoomsIn() {
        // Simulates a Linux mouse-wheel-up event where getDeltaY() == 0
        double result = zoom(0.0, 1.0, CURRENT);
        assertEquals(CURRENT * FACTOR, result, DELTA,
                "when deltaY == 0, positive textDeltaY must still produce a zoom-in");
    }

    @Test
    void linuxScrollDown_zeroDelta_negativeTextDelta_zoomsOut() {
        // Simulates a Linux mouse-wheel-down event where getDeltaY() == 0
        double result = zoom(0.0, -1.0, CURRENT);
        assertEquals(CURRENT / FACTOR, result, DELTA,
                "when deltaY == 0, negative textDeltaY must still produce a zoom-out");
    }

    @Test
    void bothDeltasZero_returnsCurrentZoom() {
        // Both deltas zero → no scroll occurred; zoom must not change
        double result = zoom(0.0, 0.0, CURRENT);
        assertEquals(CURRENT, result, DELTA,
                "when both deltas are zero, zoom must remain unchanged");
    }

    // ── clamping ──────────────────────────────────────────────────────────

    @Test
    void zoomIn_atMaximum_clampedToMax() {
        double result = zoom(40.0, 0.0, MAX);
        assertEquals(MAX, result, DELTA,
                "zoom-in at maximum must be clamped to ZOOM_MAX");
    }

    @Test
    void zoomOut_atMinimum_clampedToMin() {
        double result = zoom(-40.0, 0.0, MIN);
        assertEquals(MIN, result, DELTA,
                "zoom-out at minimum must be clamped to ZOOM_MIN");
    }

    @Test
    void zoomIn_nearMaximum_clampedToMax() {
        // A zoom level just below MAX × FACTOR would exceed the limit
        double nearMax = MAX / FACTOR + 0.001;
        double result = zoom(40.0, 0.0, nearMax);
        assertEquals(MAX, result, DELTA,
                "zoom-in result exceeding ZOOM_MAX must be clamped to ZOOM_MAX");
    }

    @Test
    void zoomOut_nearMinimum_clampedToMin() {
        double nearMin = MIN * FACTOR - 0.001;
        double result = zoom(-40.0, 0.0, nearMin);
        assertEquals(MIN, result, DELTA,
                "zoom-out result below ZOOM_MIN must be clamped to ZOOM_MIN");
    }

    // ── symmetry: zoom-in then zoom-out should round-trip ─────────────────

    @Test
    void zoomInThenOut_isApproximatelySymmetric() {
        double afterIn  = zoom(40.0,  0.0, CURRENT);
        double afterOut = zoom(-40.0, 0.0, afterIn);
        assertEquals(CURRENT, afterOut, 1e-6,
                "one zoom-in followed by one zoom-out must return to the original level");
    }

    @Test
    void linuxFallback_zoomInThenOut_isApproximatelySymmetric() {
        // Same round-trip test but using the Linux textDeltaY path
        double afterIn  = zoom(0.0,  1.0, CURRENT);
        double afterOut = zoom(0.0, -1.0, afterIn);
        assertEquals(CURRENT, afterOut, 1e-6,
                "Linux textDeltaY zoom-in then zoom-out must round-trip correctly");
    }

    // ── deltaY takes precedence over textDeltaY ───────────────────────────

    @Test
    void nonZeroDeltaY_overridesTextDeltaY() {
        // deltaY positive → zoom in, even though textDeltaY is negative
        double result = zoom(40.0, -999.0, CURRENT);
        assertEquals(CURRENT * FACTOR, result, DELTA,
                "non-zero deltaY must take precedence over textDeltaY");
    }
}

