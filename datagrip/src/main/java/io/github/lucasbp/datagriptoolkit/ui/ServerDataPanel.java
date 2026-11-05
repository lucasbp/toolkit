package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.ServerMonitor;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

final class ServerDataPanel extends JPanel {
    interface Session { SqlExecutor open() throws Exception; }
    private final Session session;
    private final boolean processes;
    private final JTable table = new JTable();
    private final JLabel status = new JLabel("Ready");
    private final JButton refresh = new JButton("Refresh");
    private final JTextField filter = new JTextField(24);
    private SwingWorker<SqlExecutor.QueryResult, Void> worker;
    private boolean disposed;

    ServerDataPanel(Session session, boolean processes) {
        super(new BorderLayout(8, 8));
        this.session = session;
        this.processes = processes;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.add(refresh, BorderLayout.WEST);
        if (processes) {
            filter.putClientProperty("JTextField.placeholderText", "Filter processes");
            filter.getAccessibleContext().setAccessibleName("Filter processes");
            toolbar.add(filter, BorderLayout.CENTER);
            filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent event) { filter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent event) { filter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            });
        }
        refresh.addActionListener(event -> load());
        var renderer = new javax.swing.table.DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, renderer);
        table.setRowHeight(28);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(this::load);
    }

    private void load() {
        if (disposed || worker != null) return;
        refresh.setEnabled(false);
        status.setText("Loading...");
        worker = new SwingWorker<>() {
            @Override protected SqlExecutor.QueryResult doInBackground() throws Exception {
                try (var executor = session.open()) { return processes ? ServerMonitor.processes(executor) : ServerMonitor.overview(executor); }
            }
            @Override protected void done() {
                if (disposed) return;
                worker = null;
                refresh.setEnabled(true);
                try {
                    var result = get();
                    var model = new DefaultTableModel(result.columns().toArray(String[]::new), 0) {
                        @Override public boolean isCellEditable(int row, int column) { return false; }
                    };
                    result.rows().forEach(row -> model.addRow(row.toArray()));
                    table.setModel(model);
                    table.setRowSorter(new TableRowSorter<>(model));
                    filter();
                    status.setText(processes ? result.rows().size() + " processes returned. Visibility depends on your server privileges." : "Server information loaded.");
                } catch (Exception problem) {
                    table.setModel(new DefaultTableModel());
                    status.setText("Could not load server data. Check the connection and your privileges, then refresh.");
                }
            }
        };
        worker.execute();
    }

    private void filter() {
        if (table.getRowSorter() instanceof TableRowSorter<?> sorter) sorter.setRowFilter(filter.getText().isBlank() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(filter.getText())));
    }

    void dispose() { disposed = true; if (worker != null) worker.cancel(true); }
}
