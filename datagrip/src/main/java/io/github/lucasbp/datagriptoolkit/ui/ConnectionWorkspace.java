package io.github.lucasbp.datagriptoolkit.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.*;

final class ConnectionWorkspace extends JPanel {
    record View(JComponent component, Runnable dispose) {}
    private final Supplier<List<AccountAdminPanel.ConnectionChoice>> connections;
    private final Function<AccountAdminPanel.ConnectionChoice, View> factory;
    private final Map<String, View> views = new HashMap<>();
    private final CardLayout layout = new CardLayout();
    private String selectedId;
    private java.util.function.Consumer<String> selectionChanged = ignored -> {};

    ConnectionWorkspace(Supplier<List<AccountAdminPanel.ConnectionChoice>> connections, Function<AccountAdminPanel.ConnectionChoice, View> factory) {
        this(connections, factory, () -> {});
    }

    ConnectionWorkspace(Supplier<List<AccountAdminPanel.ConnectionChoice>> connections, Function<AccountAdminPanel.ConnectionChoice, View> factory, Runnable showExplorer) {
        this.connections = connections;
        this.factory = factory;
        setLayout(layout);
        var empty = new JPanel(new java.awt.GridBagLayout());
        var message = new JPanel(new BorderLayout(0, 12));
        message.add(new JLabel("Select a MySQL / Aurora connection in Database Explorer."), BorderLayout.NORTH);
        var button = new JButton("Show Database Explorer");
        button.addActionListener(event -> showExplorer.run());
        message.add(button, BorderLayout.SOUTH);
        empty.add(message);
        add(empty, "");
    }

    String selectedId() { return selectedId; }
    void onSelectionChanged(java.util.function.Consumer<String> action) { selectionChanged = action; }

    void refresh() {
        var available = connections.get();
        var removed = views.keySet().stream().filter(id -> available.stream().noneMatch(item -> id.equals(item.id()))).toList();
        for (String id : removed) {
            var view = views.remove(id);
            view.dispose().run();
            remove(view.component());
        }
        if (selectedId != null && available.stream().noneMatch(item -> selectedId.equals(item.id()))) select(null);
    }

    void select(String id) {
        if (id == null) {
            selectedId = null;
            layout.show(this, "");
            selectionChanged.accept(null);
            return;
        }
        var choice = connections.get().stream().filter(item -> id.equals(item.id())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The selected connection is no longer available. Select another connection in Database Explorer."));
        var view = views.get(id);
        if (view == null) {
            view = factory.apply(choice);
            views.put(id, view);
            add(view.component(), id);
        }
        selectedId = id;
        layout.show(this, id);
        selectionChanged.accept(id);
    }

    JComponent currentView() { return selectedId == null ? null : views.get(selectedId).component(); }

    void dispose() {
        views.values().forEach(view -> view.dispose().run());
        views.clear();
    }
}
