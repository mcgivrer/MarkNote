package ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java utility for saving and restoring the expansion state of a tree
 * whose nodes are provided through the {@link Node} abstraction.
 *
 * <p>The class is intentionally decoupled from JavaFX {@code TreeItem} so that
 * the core state-preservation logic can be unit-tested without a running JavaFX
 * runtime.  A thin {@link Node} adapter over {@code TreeItem<T>} is provided in
 * {@link ProjectExplorerPanel} to bridge the two.</p>
 */
class TreeExpansionState {

    private TreeExpansionState() {}

    /**
     * Minimal abstraction of a tree node used by the state-preservation logic.
     *
     * @param <T> the type of value held by each node.
     */
    interface Node<T> {
        /** Returns the value associated with this node, or {@code null}. */
        T getValue();

        /** Returns {@code true} if this node is currently expanded. */
        boolean isExpanded();

        /** Expands or collapses this node. */
        void setExpanded(boolean expanded);

        /** Returns the direct children of this node (never {@code null}). */
        List<? extends Node<T>> getChildren();

        /**
         * Returns {@code true} when the value of this node should be included
         * in the collected set.  The default implementation always returns
         * {@code true}; override to restrict collection to directories, etc.
         */
        default boolean isCollectable() {
            return true;
        }
    }

    /**
     * Walks the tree rooted at {@code root} and returns the set of values for
     * all nodes that are currently expanded <em>and</em> whose
     * {@link Node#isCollectable()} returns {@code true}.
     *
     * @param <T>  type of node values.
     * @param root root of the subtree to inspect (may be {@code null}).
     * @return a mutable {@code Set} of collected values; never {@code null}.
     */
    static <T> Set<T> collectExpanded(Node<T> root) {
        Set<T> expanded = new HashSet<>();
        if (root == null) return expanded;
        if (root.isExpanded() && root.getValue() != null && root.isCollectable()) {
            expanded.add(root.getValue());
        }
        for (Node<T> child : root.getChildren()) {
            expanded.addAll(collectExpanded(child));
        }
        return expanded;
    }

    /**
     * Walks the tree rooted at {@code root} and expands every node whose value
     * is contained in {@code expandedValues}.  Nodes whose values are not in
     * the set are left unchanged (they are <em>not</em> collapsed).
     *
     * @param <T>           type of node values.
     * @param root          root of the subtree to update (may be {@code null}).
     * @param expandedValues set of values that should be marked expanded.
     */
    static <T> void restoreExpanded(Node<T> root, Set<T> expandedValues) {
        if (root == null) return;
        if (root.getValue() != null && expandedValues.contains(root.getValue())) {
            root.setExpanded(true);
        }
        for (Node<T> child : root.getChildren()) {
            restoreExpanded(child, expandedValues);
        }
    }
}
