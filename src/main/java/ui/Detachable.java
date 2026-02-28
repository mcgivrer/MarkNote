package ui;

import javafx.scene.Node;

/**
 * Interface définissant le comportement de détachement d'un panel vers un onglet.
 * <p>
 * Les panels implémentant cette interface peuvent être détachés de leur position
 * dans l'interface pour s'afficher dans un onglet séparé du TabPane principal.
 * </p>
 */
public interface Detachable {

    /**
     * Retourne le contenu qui sera affiché dans l'onglet détaché.
     *
     * @return le Node contenant le contenu à détacher
     */
    Node getDetachableContent();

    /**
     * Retourne le titre à afficher dans l'onglet détaché.
     *
     * @return le titre de l'onglet
     */
    String getDetachTabTitle();

    /**
     * Appelé lorsque le contenu est détaché vers un onglet.
     * Permet au panel de réagir au détachement (ex: masquer certains éléments).
     */
    default void onDetached() {
        // Implémentation par défaut vide
    }

    /**
     * Appelé lorsque le contenu est rattaché au panel (fermeture de l'onglet).
     * Permet au panel de restaurer son état initial.
     *
     * @param content le contenu qui était dans l'onglet
     */
    default void onReattached(Node content) {
        // Implémentation par défaut vide
    }

    /**
     * Appelé après que le contenu a été rattaché et que le layout est stabilisé.
     * Utile pour les opérations de rafraîchissement (ex: zoom to fit).
     */
    default void afterReattach() {
        // Implémentation par défaut vide
    }

    /**
     * Indique si ce panel supporte le détachement.
     * Par défaut, tous les panels qui implémentent cette interface le supportent.
     *
     * @return true si le détachement est supporté
     */
    default boolean isDetachable() {
        return true;
    }
}
