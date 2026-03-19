package ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TreeExpansionState}.
 *
 * All tests are pure Java — no JavaFX runtime required — by using the
 * {@link TreeExpansionState.Node} interface with a simple in-memory
 * implementation.
 *
 * These tests verify the core mechanism that prevents the project-explorer
 * tree from collapsing when a document is saved: after the tree is rebuilt
 * from scratch, previously expanded directories are re-expanded and the
 * currently selected file is revealed.
 */
public class TreeExpansionStateTest {

    @TempDir
    File tempDir;

    // ── Simple in-memory tree node ──────────────────────────────────────────

    /**
     * Lightweight, pure-Java tree node for testing purposes.
     */
    static class SimpleNode<T> implements TreeExpansionState.Node<T> {
        private final T value;
        private boolean expanded;
        private final boolean collectable;
        private final List<SimpleNode<T>> children = new ArrayList<>();

        SimpleNode(T value, boolean collectable) {
            this.value = value;
            this.collectable = collectable;
        }

        SimpleNode<T> add(SimpleNode<T> child) {
            children.add(child);
            return this;
        }

        @Override public T getValue()               { return value;      }
        @Override public boolean isExpanded()       { return expanded;   }
        @Override public void setExpanded(boolean e){ this.expanded = e; }
        @Override public List<SimpleNode<T>> getChildren() { return children; }
        @Override public boolean isCollectable()    { return collectable; }
    }

    // ── collectExpanded ─────────────────────────────────────────────────────

    @Test
    void collectExpanded_nullRoot_returnsEmptySet() {
        Set<File> result = TreeExpansionState.collectExpanded(null);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "null root must yield an empty set");
    }

    @Test
    void collectExpanded_noExpandedNodes_returnsEmptySet() {
        File sub = new File(tempDir, "sub");
        SimpleNode<File> root = new SimpleNode<>(tempDir, true);
        SimpleNode<File> child = new SimpleNode<>(sub, true);
        root.add(child);
        // nothing expanded

        assertTrue(TreeExpansionState.collectExpanded(root).isEmpty(),
                "no expanded node → empty set expected");
    }

    @Test
    void collectExpanded_onlyCollectableExpandedNodesReturned() {
        File subA = new File(tempDir, "subA");
        File subB = new File(tempDir, "subB");
        File fileC = new File(tempDir, "note.md");

        // root and subA are collectable (directories), fileC is not (file)
        SimpleNode<File> root  = new SimpleNode<>(tempDir, true);
        SimpleNode<File> nodeA = new SimpleNode<>(subA, true);
        SimpleNode<File> nodeB = new SimpleNode<>(subB, true);
        SimpleNode<File> nodeC = new SimpleNode<>(fileC, false);
        root.add(nodeA).add(nodeB).add(nodeC);

        root.setExpanded(true);
        nodeA.setExpanded(true);
        nodeC.setExpanded(true); // expanded but NOT collectable

        Set<File> result = TreeExpansionState.collectExpanded(root);

        assertTrue(result.contains(tempDir), "root (expanded, collectable) must be collected");
        assertTrue(result.contains(subA),    "subA (expanded, collectable) must be collected");
        assertFalse(result.contains(subB),   "subB (not expanded) must NOT be collected");
        assertFalse(result.contains(fileC),  "fileC (not collectable) must NOT be collected");
    }

    @Test
    void collectExpanded_deeplyNestedNode_collected() {
        File sub = new File(tempDir, "sub");
        File deep = new File(sub, "deep");

        SimpleNode<File> root    = new SimpleNode<>(tempDir, true);
        SimpleNode<File> nodeSub = new SimpleNode<>(sub,  true);
        SimpleNode<File> nodeDeep = new SimpleNode<>(deep, true);
        nodeSub.add(nodeDeep);
        root.add(nodeSub);

        root.setExpanded(true);
        nodeSub.setExpanded(true);
        nodeDeep.setExpanded(true);

        Set<File> result = TreeExpansionState.collectExpanded(root);

        assertTrue(result.contains(tempDir), "root must be collected");
        assertTrue(result.contains(sub),     "sub must be collected");
        assertTrue(result.contains(deep),    "deep must be collected");
    }

    // ── restoreExpanded ─────────────────────────────────────────────────────

    @Test
    void restoreExpanded_nullRoot_doesNotThrow() {
        Set<File> set = new HashSet<>();
        set.add(tempDir);
        assertDoesNotThrow(() -> TreeExpansionState.restoreExpanded(null, set));
    }

    @Test
    void restoreExpanded_expandsMatchingNodes() {
        File subA = new File(tempDir, "subA");
        File subB = new File(tempDir, "subB");

        SimpleNode<File> root  = new SimpleNode<>(tempDir, true);
        SimpleNode<File> nodeA = new SimpleNode<>(subA, true);
        SimpleNode<File> nodeB = new SimpleNode<>(subB, true);
        root.add(nodeA).add(nodeB);
        // all collapsed

        Set<File> expandedDirs = new HashSet<>();
        expandedDirs.add(tempDir);
        expandedDirs.add(subA);

        TreeExpansionState.restoreExpanded(root, expandedDirs);

        assertTrue(root.isExpanded(),   "root should be expanded after restore");
        assertTrue(nodeA.isExpanded(),  "subA should be expanded after restore");
        assertFalse(nodeB.isExpanded(), "subB was not in the set, must remain collapsed");
    }

    @Test
    void restoreExpanded_doesNotCollapseAlreadyExpandedNodes() {
        File subA = new File(tempDir, "subA");

        SimpleNode<File> root  = new SimpleNode<>(tempDir, true);
        SimpleNode<File> nodeA = new SimpleNode<>(subA, true);
        root.add(nodeA);
        nodeA.setExpanded(true); // already expanded

        // Restore with an empty set — must NOT collapse nodeA
        TreeExpansionState.restoreExpanded(root, new HashSet<>());

        assertTrue(nodeA.isExpanded(),
                "already-expanded node must not be collapsed by restoreExpanded");
    }

    // ── Round-trip (simulates save → refresh cycle) ─────────────────────────

    /**
     * Simulates what happens during a save: the explorer tree is rebuilt from
     * scratch; collectExpanded + restoreExpanded must reproduce the same
     * expansion state on the newly built tree.
     */
    @Test
    void roundTrip_preservesExpandedStateAfterTreeRebuild() {
        File subA = new File(tempDir, "subA");
        File subB = new File(tempDir, "subB");

        // ── Original tree: root + subA expanded, subB collapsed ───────────
        SimpleNode<File> origRoot = new SimpleNode<>(tempDir, true);
        SimpleNode<File> origA    = new SimpleNode<>(subA, true);
        SimpleNode<File> origB    = new SimpleNode<>(subB, true);
        origRoot.add(origA).add(origB);
        origRoot.setExpanded(true);
        origA.setExpanded(true);

        Set<File> saved = TreeExpansionState.collectExpanded(origRoot);

        // ── New tree (simulates a fresh ProjectExplorerPanel.refresh() call)
        SimpleNode<File> newRoot = new SimpleNode<>(tempDir, true);
        SimpleNode<File> newA    = new SimpleNode<>(subA, true);
        SimpleNode<File> newB    = new SimpleNode<>(subB, true);
        newRoot.add(newA).add(newB);
        newRoot.setExpanded(true); // refresh() always expands the root

        TreeExpansionState.restoreExpanded(newRoot, saved);

        assertTrue(newRoot.isExpanded(),  "root must remain expanded after round-trip");
        assertTrue(newA.isExpanded(),     "subA must be re-expanded after round-trip");
        assertFalse(newB.isExpanded(),    "subB must remain collapsed after round-trip");
    }

    @Test
    void roundTrip_newlyAddedNodeDefaultsToCollapsed() {
        File subA    = new File(tempDir, "subA");
        File subNew  = new File(tempDir, "subNew"); // new since last save

        // Original tree has only subA (expanded)
        SimpleNode<File> origRoot = new SimpleNode<>(tempDir, true);
        SimpleNode<File> origA    = new SimpleNode<>(subA, true);
        origRoot.add(origA);
        origRoot.setExpanded(true);
        origA.setExpanded(true);

        Set<File> saved = TreeExpansionState.collectExpanded(origRoot);

        // New tree has subA AND subNew (newly created)
        SimpleNode<File> newRoot = new SimpleNode<>(tempDir, true);
        SimpleNode<File> newA    = new SimpleNode<>(subA, true);
        SimpleNode<File> newNew  = new SimpleNode<>(subNew, true);
        newRoot.add(newA).add(newNew);
        newRoot.setExpanded(true);

        TreeExpansionState.restoreExpanded(newRoot, saved);

        assertTrue(newA.isExpanded(),    "previously expanded subA must be re-expanded");
        assertFalse(newNew.isExpanded(), "newly added node must default to collapsed");
    }
}
