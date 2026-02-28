package ui;

/**
 * Interface définissant le comportement de docking d'un panel.
 * <p>
 * Les panels implémentant cette interface peuvent être déplacés 
 * vers différentes zones de la fenêtre principale (TOP, BOTTOM, LEFT, RIGHT, CENTER).
 * </p>
 */
public interface Dockable {

    /**
     * Retourne la position de docking actuelle du panel.
     *
     * @return la position de docking actuelle
     */
    DockPosition getDockPosition();

    /**
     * Définit la position de docking du panel.
     *
     * @param position la nouvelle position de docking
     */
    void setDockPosition(DockPosition position);

    /**
     * Indique si ce panel peut être docké.
     *
     * @return true si le docking est supporté
     */
    default boolean isDockable() {
        return true;
    }

    /**
     * Retourne le titre du panel pour l'affichage dans les zones de docking.
     *
     * @return le titre du panel
     */
    String getDockTitle();
}
