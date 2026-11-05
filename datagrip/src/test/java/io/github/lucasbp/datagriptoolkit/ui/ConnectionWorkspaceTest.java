package io.github.lucasbp.datagriptoolkit.ui;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionWorkspaceTest {
    private static AccountAdminPanel.ConnectionChoice choice(String id, String name) { return new AccountAdminPanel.ConnectionChoice(id, name); }

    @Test void usesTheNativeExplorerWithoutRenderingAnotherConnectionSelector() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var opened = new ArrayList<String>();
            var focused = new AtomicInteger();
            var workspace = new ConnectionWorkspace(() -> List.of(choice("a", "Production")), item -> {
                opened.add(item.id());
                return new ConnectionWorkspace.View(new JTextField(), () -> {});
            }, focused::incrementAndGet);
            assertTrue(opened.isEmpty());
            assertNull(workspace.selectedId());
            assertFalse(hasConnectionSelector(workspace));
            findExplorerButton(workspace).doClick();
            assertEquals(1, focused.get());
            assertTrue(opened.isEmpty());
            workspace.select("a");
            assertEquals(List.of("a"), opened);
            workspace.dispose();
        });
    }

    @Test void switchingNativeSelectionsRetainsEachViewAndItsPendingChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var created = new AtomicInteger();
            var workspace = new ConnectionWorkspace(() -> List.of(choice("a", "A"), choice("b", "B")), item -> {
                created.incrementAndGet();
                return new ConnectionWorkspace.View(new JTextField(), () -> {});
            });
            workspace.select("a");
            var original = (JTextField) workspace.currentView();
            original.setText("pending review");
            workspace.select("b");
            workspace.select("a");
            assertSame(original, workspace.currentView());
            assertEquals("pending review", original.getText());
            assertEquals(2, created.get());
            workspace.dispose();
        });
    }

    @Test void removingAConnectionDisposesItsViewAndNeverSelectsAnotherServer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var available = new AtomicReference<>(List.of(choice("a", "A"), choice("b", "B")));
            var disposed = new AtomicInteger();
            var workspace = new ConnectionWorkspace(available::get, item -> new ConnectionWorkspace.View(new JPanel(), disposed::incrementAndGet));
            workspace.select("a");
            available.set(List.of(choice("b", "B")));
            workspace.refresh();
            assertNull(workspace.selectedId());
            assertEquals(1, disposed.get());
            assertThrows(IllegalArgumentException.class, () -> workspace.select("a"));
            workspace.dispose();
        });
    }

    @Test void clearingTheNativeSelectionHidesTheTargetButKeepsItsPendingReview() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var workspace = new ConnectionWorkspace(() -> List.of(choice("a", "A")), item -> new ConnectionWorkspace.View(new JTextField(), () -> {}));
            workspace.select("a");
            var original = (JTextField) workspace.currentView();
            original.setText("pending");
            workspace.select(null);
            assertNull(workspace.selectedId());
            assertNull(workspace.currentView());
            workspace.select("a");
            assertSame(original, workspace.currentView());
            assertEquals("pending", original.getText());
            workspace.dispose();
        });
    }

    private static boolean hasConnectionSelector(java.awt.Container parent) {
        for (var child : parent.getComponents()) {
            if (child instanceof JList<?> || child instanceof JComboBox<?> || child instanceof JTextField) return true;
            if (child instanceof java.awt.Container nested && hasConnectionSelector(nested)) return true;
        }
        return false;
    }

    private static JButton findExplorerButton(java.awt.Container parent) {
        for (var child : parent.getComponents()) {
            if (child instanceof JButton button) return button;
            if (child instanceof java.awt.Container nested) {
                var button = findExplorerButton(nested);
                if (button != null) return button;
            }
        }
        return null;
    }
}
