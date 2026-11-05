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

    @Test void filteringDoesNotOpenAConnectionAndIgnoresCaseAndAccents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var opened = new ArrayList<String>();
            var workspace = new ConnectionWorkspace(() -> List.of(choice("a", "Produção"), choice("b", "Testing")), item -> {
                opened.add(item.id());
                return new ConnectionWorkspace.View(new JTextField(), () -> {});
            });
            workspace.search.setText("PRODUCAO");
            assertEquals(1, workspace.model.size());
            assertTrue(opened.isEmpty());
            assertNull(workspace.selectedId());
            workspace.list.setSelectedIndex(0);
            assertEquals(List.of("a"), opened);
            assertEquals("a", workspace.selectedId());
            workspace.dispose();
        });
    }

    @Test void switchingConnectionsRetainsEachExistingViewAndItsPendingChanges() throws Exception {
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
            workspace.search.setText("B");
            assertEquals("a", workspace.selectedId());
            assertSame(original, workspace.currentView());
            workspace.dispose();
        });
    }

    @Test void removingAConnectionDisposesItsViewAndDoesNotSwitchToAnotherServer() throws Exception {
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
}
