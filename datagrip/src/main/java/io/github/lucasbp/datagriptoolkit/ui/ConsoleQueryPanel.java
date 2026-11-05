package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.MySqlSelectBuilder;
import io.github.lucasbp.datagriptoolkit.mysql.QueryTemplates;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

final class ConsoleQueryPanel extends JPanel {
    record Table(String schema, String name, List<String> columns) { @Override public String toString() { return schema + "." + name; } }
    final JTextField search = new JTextField();
    final JComboBox<Table> tables = new JComboBox<>();
    final JList<String> columns = new JList<>();
    final JCheckBox allColumns = new JCheckBox("All columns", true);
    final JComboBox<String> filterColumn = new JComboBox<>();
    final JTextField filterValue = new JTextField();
    final JComboBox<String> filterType = new JComboBox<>(new String[]{"Text", "Number", "NULL"});
    final JComboBox<String> orderColumn = new JComboBox<>();
    final JCheckBox descending = new JCheckBox("Descending");
    final JComboBox<String> resultType = new JComboBox<>(new String[]{"Rows", "Count"});
    final JSpinner limit = new JSpinner(new SpinnerNumberModel(1000, 1, 2000, 1));
    private boolean updating;

    ConsoleQueryPanel(List<Table> available, Consumer<String> insert, Runnable close) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        tables.setRenderer(Forms.literalRenderer());
        filterColumn.setRenderer(Forms.literalRenderer());
        orderColumn.setRenderer(Forms.literalRenderer());
        var header = new JPanel(new BorderLayout(8, 0));
        header.add(new JLabel("Build SELECT for this console"), BorderLayout.WEST);
        var closeButton = new JButton("Close");
        closeButton.addActionListener(event -> close.run());
        header.add(closeButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        var form = new JPanel(new GridLayout(1, 3, 12, 0));
        var target = Forms.stack();
        Forms.field(target, "Find table", search);
        Forms.field(target, "Table", tables);
        Forms.field(target, "Result", resultType);
        Forms.field(target, "Row limit", limit);
        var fields = Forms.stack();
        fields.add(allColumns);
        columns.setVisibleRowCount(5);
        columns.setCellRenderer(Forms.literalRenderer());
        fields.add(new JScrollPane(columns));
        columns.setEnabled(false);
        allColumns.addActionListener(event -> columns.setEnabled(!allColumns.isSelected()));
        resultType.addActionListener(event -> {
            boolean rows = resultType.getSelectedIndex() == 0;
            allColumns.setEnabled(rows);
            columns.setEnabled(rows && !allColumns.isSelected());
            orderColumn.setEnabled(rows);
            descending.setEnabled(rows);
            limit.setEnabled(rows);
        });
        var conditions = Forms.stack();
        Forms.field(conditions, "Filter column (optional)", filterColumn);
        var value = new JPanel(new BorderLayout(4, 0));
        value.add(filterType, BorderLayout.WEST);
        value.add(filterValue, BorderLayout.CENTER);
        Forms.field(conditions, "Equals", value);
        Forms.field(conditions, "Order by (optional)", orderColumn);
        conditions.add(descending);
        form.add(target);
        form.add(fields);
        form.add(conditions);
        add(form, BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout(8, 0));
        var status = new JLabel("Choose a table, then insert the SELECT. It will not execute automatically.");
        status.putClientProperty("html.disable", Boolean.TRUE);
        footer.add(status, BorderLayout.CENTER);
        var button = new JButton("Insert SELECT into console");
        button.addActionListener(event -> {
            try { insert.accept(sql()); }
            catch (IllegalArgumentException problem) { status.setText(problem.getMessage()); }
        });
        footer.add(button, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        tables.addActionListener(event -> {
            if (updating) return;
            var table = (Table) tables.getSelectedItem();
            var names = table == null ? new String[0] : table.columns().toArray(String[]::new);
            columns.setListData(names);
            filterColumn.setModel(new DefaultComboBoxModel<>(names));
            filterColumn.setSelectedIndex(-1);
            orderColumn.setModel(new DefaultComboBoxModel<>(names));
            orderColumn.setSelectedIndex(-1);
            filterValue.setText("");
        });
        Runnable filter = () -> {
            String query = java.text.Normalizer.normalize(search.getText().strip(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
            var previous = tables.getSelectedItem();
            var matches = available.stream().filter(table -> java.text.Normalizer.normalize(table.toString(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
            updating = true;
            var model = new DefaultComboBoxModel<>(matches.toArray(Table[]::new));
            model.setSelectedItem(matches.contains(previous) ? previous : null);
            tables.setModel(model);
            updating = false;
            if (tables.getSelectedItem() == null) {
                columns.setListData(new String[0]);
                filterColumn.removeAllItems();
                orderColumn.removeAllItems();
            }
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { filter.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { filter.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { filter.run(); }
        });
        filter.run();
    }

    String sql() {
        var table = (Table) tables.getSelectedItem();
        if (table == null) throw new IllegalArgumentException("Choose a table first.");
        var selectedColumns = allColumns.isSelected() ? List.<String>of() : columns.getSelectedValuesList();
        if (resultType.getSelectedIndex() == 0 && !allColumns.isSelected() && selectedColumns.isEmpty()) throw new IllegalArgumentException("Select at least one column or enable All columns.");
        List<MySqlSelectBuilder.Filter> filters = List.of();
        if (filterColumn.getSelectedItem() instanceof String column) {
            Object value = switch (filterType.getSelectedIndex()) {
                case 1 -> new BigDecimal(filterValue.getText().strip());
                case 2 -> null;
                default -> filterValue.getText();
            };
            filters = List.of(new MySqlSelectBuilder.Filter(column, value));
        }
        if (resultType.getSelectedIndex() == 1) return MySqlSelectBuilder.count(table.schema(), table.name(), filters);
        var order = orderColumn.getSelectedItem() instanceof String column ? List.of(new MySqlSelectBuilder.Order(column, descending.isSelected())) : List.<MySqlSelectBuilder.Order>of();
        return MySqlSelectBuilder.build(table.schema(), table.name(), selectedColumns, filters, order, (int) limit.getValue());
    }
}
