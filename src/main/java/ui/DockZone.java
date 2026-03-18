package ui;

/**
 * Enum représentant les zones de docking disponibles dans l'interface.
 */
public enum DockZone {
    /** Zone supérieure (nord) */
    TOP,
    /** Zone inférieure (sud) */
    BOTTOM,
    /** Zone gauche (ouest) */
    LEFT,
    /** Zone droite (est) */
    RIGHT,
    /** Zone centrale (contenu principal) */
    CENTER;

    /**
     * Alias pour compatibilité avec les noms cardinaux.
     */
    public static DockZone NORTH = TOP;
    public static DockZone SOUTH = BOTTOM;
    public static DockZone WEST = LEFT;
    public static DockZone EAST = RIGHT;
}
