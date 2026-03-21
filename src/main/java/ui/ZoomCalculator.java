package ui;

/**
 * Stateless helper that computes scroll-wheel zoom levels for the
 * {@link VisualLinkPanel} canvas.
 *
 * <p>The class is intentionally decoupled from JavaFX so that the core
 * calculation can be unit-tested without a running JavaFX runtime (following
 * the same pattern as {@link TreeExpansionState}).</p>
 *
 * <h2>Linux scroll-wheel fix</h2>
 * <p>On Linux (GTK/X11/Wayland), {@code ScrollEvent.getDeltaY()} can return
 * {@code 0.0} for ordinary mouse-wheel rotations while the actual scroll
 * direction is carried by {@code ScrollEvent.getTextDeltaY()}.  The previous
 * code evaluated {@code getDeltaY() > 0 ? zoomIn : zoomOut}; when
 * {@code getDeltaY()} was {@code 0} the condition was always {@code false},
 * so <em>every</em> zero-delta event triggered a zoom-out step — effectively
 * blocking zoom-in on affected Linux configurations.</p>
 * <p>This class fixes the issue by falling back to {@code textDeltaY} when
 * {@code deltaY} is zero, and ignoring the event entirely when both values
 * are zero.</p>
 */
final class ZoomCalculator {

    /** Multiplicative factor applied per scroll-wheel step (zoom in). */
    static final double ZOOM_FACTOR = 1.15;
    /** Minimum allowed zoom level. */
    static final double ZOOM_MIN    = 0.1;
    /** Maximum allowed zoom level. */
    static final double ZOOM_MAX    = 8.0;

    private ZoomCalculator() {
        // utility class — do not instantiate
    }

    /**
     * Computes the new zoom level after a scroll-wheel event.
     *
     * <p>The method uses {@code textDeltaY} only when {@code deltaY} is
     * exactly {@code 0.0}, providing cross-platform correctness without
     * altering the behaviour on Windows and macOS where {@code deltaY} is
     * always non-zero for mouse-wheel events.</p>
     *
     * @param deltaY      vertical scroll delta from
     *                    {@code ScrollEvent.getDeltaY()}; positive means
     *                    scroll up (zoom in), negative means scroll down
     *                    (zoom out)
     * @param textDeltaY  text-unit vertical scroll delta from
     *                    {@code ScrollEvent.getTextDeltaY()}; used as a
     *                    fallback only when {@code deltaY} is zero (Linux)
     * @param currentZoom the current zoom level before the event
     * @param zoomFactor  the multiplicative factor applied per scroll step
     *                    (e.g. {@code 1.15}); must be &gt; 1
     * @param minZoom     the minimum allowed zoom level (e.g. {@code 0.1});
     *                    must be positive
     * @param maxZoom     the maximum allowed zoom level (e.g. {@code 8.0});
     *                    must be &gt; {@code minZoom}
     * @return the clamped new zoom level; equal to {@code currentZoom} when
     *         both deltas are zero (no-op event)
     */
    static double computeNewZoom(double deltaY, double textDeltaY,
                                 double currentZoom, double zoomFactor,
                                 double minZoom, double maxZoom) {
        // On Linux, getDeltaY() can be 0; fall back to getTextDeltaY()
        double effectiveDelta = deltaY != 0.0 ? deltaY : textDeltaY;
        if (effectiveDelta == 0.0) {
            // Both deltas are zero — nothing to do
            return currentZoom;
        }
        double factor = effectiveDelta > 0 ? zoomFactor : 1.0 / zoomFactor;
        double newZoom = currentZoom * factor;
        return Math.max(minZoom, Math.min(maxZoom, newZoom));
    }
}
