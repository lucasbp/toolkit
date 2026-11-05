package io.github.lucasbp.datagriptoolkit.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final JPanel content = new JPanel(layout);
    final JTextField search = new JTextField();
    final DefaultListModel<AccountAdminPanel.ConnectionChoice> model = new DefaultListModel<>();
    final JList<AccountAdminPanel.ConnectionChoice> list = new JList<>(model);
    private final JLabel count = new JLabel();
    private List<AccountAdminPanel.ConnectionChoice> all = List.of();
    private String selectedId;
    private boolean updating;
    private final Function<String, java.awt.Color> colors;
    private java.util.function.Consumer<String> selectionChanged = ignored -> {};

    ConnectionWorkspace(Supplier<List<AccountAdminPanel.ConnectionChoice>> connections, Function<AccountAdminPanel.ConnectionChoice, View> factory) {
        this(connections, factory, ignored -> null);
    }

    ConnectionWorkspace(Supplier<List<AccountAdminPanel.ConnectionChoice>> connections, Function<AccountAdminPanel.ConnectionChoice, View> factory, Function<String, java.awt.Color> colors) {
        super(new BorderLayout());
        this.connections = connections;
        this.factory = factory;
        this.colors = colors;
        var browser = new JPanel(new BorderLayout(0, 8));
        browser.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 8));
        var header = new JPanel(new BorderLayout(0, 6));
        header.add(new JLabel("MySQL / Aurora connections"), BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Search connections by name");
        search.getAccessibleContext().setAccessibleName("Search connections by name");
        header.add(search, BorderLayout.CENTER);
        browser.add(header, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                putClientProperty("html.disable", Boolean.TRUE);
                setToolTipText(value == null ? null : value.toString());
                if (value instanceof AccountAdminPanel.ConnectionChoice choice) {
                    var color = colors.apply(choice.id());
                    if (color != null) setIcon(new javax.swing.Icon() {
                        public int getIconWidth() { return 8; }
                        public int getIconHeight() { return 16; }
                        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) { g.setColor(color); g.fillRoundRect(x, y, 6, 16, 4, 4); }
                    });
                }
                setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                return this;
            }
        });
        browser.add(new JScrollPane(list), BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout(0, 6));
        footer.add(count, BorderLayout.NORTH);
        var refresh = new JButton("Refresh connections");
        refresh.addActionListener(event -> refresh());
        footer.add(refresh, BorderLayout.SOUTH);
        browser.add(footer, BorderLayout.SOUTH);
        browser.setMinimumSize(new Dimension(200, 0));
        content.add(new JLabel("Select a connection from the list.", SwingConstants.CENTER), "");
        add(Forms.split(browser, content, 280), BorderLayout.CENTER);
        list.addListSelectionListener(event -> {
            if (!updating && !event.getValueIsAdjusting() && list.getSelectedValue() != null) select(list.getSelectedValue().id());
        });
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { filter(); }
        });
        refresh();
    }

    String selectedId() { return selectedId; }
    void onSelectionChanged(java.util.function.Consumer<String> action) { selectionChanged = action; }
    void focusSearch() { search.requestFocusInWindow(); }

    void refresh() {
        all = List.copyOf(connections.get());
        var removed = views.keySet().stream().filter(id -> all.stream().noneMatch(item -> id.equals(item.id()))).toList();
        for (String id : removed) {
            var view = views.remove(id);
            view.dispose().run();
            content.remove(view.component());
        }
        if (selectedId != null && all.stream().noneMatch(item -> selectedId.equals(item.id()))) {
            selectedId = null;
            layout.show(content, "");
            selectionChanged.accept(null);
        }
        filter();
    }

    void select(String id) {
        var choice = all.stream().filter(item -> id.equals(item.id())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The selected connection is no longer available. Refresh the list."));
        var view = views.get(id);
        if (view == null) {
            view = factory.apply(choice);
            views.put(id, view);
            content.add(view.component(), id);
        }
        selectedId = id;
        layout.show(content, id);
        filter();
        selectionChanged.accept(id);
    }

    JComponent currentView() { return selectedId == null ? null : views.get(selectedId).component(); }

    private void filter() {
        String query = normalize(search.getText());
        updating = true;
        try {
            model.clear();
            for (var item : all) if (normalize(item.label()).contains(query)) model.addElement(item);
            list.clearSelection();
            for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(selectedId)) list.setSelectedIndex(i);
            count.setText(model.size() + " of " + all.size() + " connections");
        } finally { updating = false; }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    void dispose() {
        views.values().forEach(view -> view.dispose().run());
        views.clear();
    }
}
