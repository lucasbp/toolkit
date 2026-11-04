package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.MySqlSelectBuilder;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

final class SelectBuilderPanel extends JPanel {
    SelectBuilderPanel(SqlConsoleBridge bridge) {
        super(new BorderLayout());
        var body = Forms.stack();
        var sources = new JComboBox<SqlConsoleBridge.Source>();
        sources.setRenderer(Forms.literalRenderer());
        Forms.field(body, "Conexão MySQL / Aurora MySQL", sources);
        Forms.button(body, "Atualizar conexões", () -> bridge.refresh(sources));
        var schema = new JTextField();
        var table = new JTextField();
        var columns = new JTextField();
        var filterColumn = new JTextField();
        var filterValue = new JTextField();
        var type = new JComboBox<>(new String[]{"Texto", "Número", "Nulo"});
        var order = new JTextField();
        var descending = new javax.swing.JCheckBox("Ordem decrescente");
        var limit = new JSpinner(new SpinnerNumberModel(1000, 1, 2000, 1));
        Forms.field(body, "Schema", schema);
        Forms.field(body, "Tabela", table);
        Forms.field(body, "Colunas separadas por vírgula (vazio para todas)", columns);
        Forms.field(body, "Coluna do filtro de igualdade (opcional)", filterColumn);
        Forms.field(body, "Tipo do valor", type);
        Forms.field(body, "Valor do filtro", filterValue);
        Forms.field(body, "Ordenar pela coluna (opcional)", order);
        body.add(descending);
        Forms.field(body, "Limite de registros", limit);
        var preview = Forms.preview(body);
        Forms.button(body, "Gerar SELECT", () -> {
            preview.setText("");
            var selectedColumns = columns.getText().isBlank() ? List.<String>of() : Arrays.stream(columns.getText().split(",", -1)).map(String::strip).toList();
            List<MySqlSelectBuilder.Filter> filters = List.of();
            if (!filterColumn.getText().isBlank()) {
                Object value = switch (type.getSelectedIndex()) {
                    case 1 -> new BigDecimal(filterValue.getText());
                    case 2 -> null;
                    default -> filterValue.getText();
                };
                filters = List.of(new MySqlSelectBuilder.Filter(filterColumn.getText(), value));
            }
            var ordering = order.getText().isBlank() ? List.<MySqlSelectBuilder.Order>of() : List.of(new MySqlSelectBuilder.Order(order.getText(), descending.isSelected()));
            preview.setText(MySqlSelectBuilder.build(schema.getText(), table.getText(), selectedColumns, filters, ordering, (int) limit.getValue()));
        });
        Forms.button(body, "Abrir SELECT em novo console", () -> bridge.open((SqlConsoleBridge.Source) sources.getSelectedItem(), preview.getText()));
        var invalidate = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { preview.setText(""); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { preview.setText(""); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { preview.setText(""); }
        };
        for (var input : new JTextField[]{schema, table, columns, filterColumn, filterValue, order}) input.getDocument().addDocumentListener(invalidate);
        type.addActionListener(event -> preview.setText(""));
        sources.addActionListener(event -> preview.setText(""));
        descending.addActionListener(event -> preview.setText(""));
        limit.addChangeListener(event -> preview.setText(""));
        add(new JScrollPane(body), BorderLayout.CENTER);
        bridge.refresh(sources);
    }
}
