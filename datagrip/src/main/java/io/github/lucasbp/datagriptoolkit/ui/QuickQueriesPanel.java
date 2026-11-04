package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.QueryTemplates;
import java.awt.BorderLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import java.util.List;

final class QuickQueriesPanel extends JPanel {
    private boolean disposed;
    private SwingWorker<List<SqlConsoleBridge.TableChoice>, Void> worker;

    QuickQueriesPanel(SqlConsoleBridge bridge) {
        super(new BorderLayout());
        var body = Forms.stack();
        var sources = new JComboBox<SqlConsoleBridge.Source>();
        var tables = new JComboBox<SqlConsoleBridge.TableChoice>();
        sources.setRenderer(Forms.literalRenderer());
        tables.setRenderer(Forms.literalRenderer());
        var status = new JLabel("Escolha a conexão para listar as tabelas já conhecidas pelo DataGrip.");
        Forms.field(body, "Conexão MySQL / Aurora", sources);
        var refresh = Forms.button(body, "Atualizar conexões", () -> bridge.refresh(sources));
        sources.addActionListener(event -> tables.removeAllItems());
        Forms.button(body, "Carregar tabelas", () -> {
            if (worker != null) return;
            var source = (SqlConsoleBridge.Source) sources.getSelectedItem();
            if (source == null) throw new IllegalArgumentException("Selecione uma conexão MySQL.");
            tables.removeAllItems();
            sources.setEnabled(false);
            tables.setEnabled(false);
            refresh.setEnabled(false);
            status.setText("Lendo as tabelas conhecidas pelo DataGrip...");
            worker = new SwingWorker<>() {
                protected List<SqlConsoleBridge.TableChoice> doInBackground() { return bridge.tables(source); }
                protected void done() {
                    if (disposed || worker != this) return;
                    worker = null;
                    sources.setEnabled(true);
                    tables.setEnabled(true);
                    refresh.setEnabled(true);
                    try {
                        if (!source.equals(sources.getSelectedItem())) return;
                        for (var table : get()) tables.addItem(table);
                        tables.setSelectedIndex(-1);
                        status.setText(tables.getItemCount() + " tabelas. Se faltar alguma, atualize a introspecção no DataGrip.");
                    } catch (Exception problem) { status.setText("Não foi possível ler os metadados. Atualize a conexão no DataGrip."); }
                }
            };
            worker.execute();
        });
        Forms.field(body, "Tabela", tables);
        Forms.button(body, "SELECT das primeiras 1000 linhas", () -> {
            var table = selected(tables);
            bridge.open(bridge.source(table.sourceId()), QueryTemplates.firstThousand(table.schema(), table.table()));
        });
        Forms.button(body, "SELECT com os nomes das colunas", () -> {
            var table = selected(tables);
            bridge.open(bridge.source(table.sourceId()), QueryTemplates.listedColumns(table.schema(), table.table(), table.columns()));
        });
        Forms.button(body, "Contar registros", () -> {
            var table = selected(tables);
            bridge.open(bridge.source(table.sourceId()), QueryTemplates.count(table.schema(), table.table()));
        });
        body.add(status);
        body.add(new JLabel("Também disponíveis com botão direito na tabela: Consultas rápidas."));
        add(new JScrollPane(body), BorderLayout.CENTER);
        bridge.refresh(sources);
    }

    private static SqlConsoleBridge.TableChoice selected(JComboBox<SqlConsoleBridge.TableChoice> tables) {
        var table = (SqlConsoleBridge.TableChoice) tables.getSelectedItem();
        if (table == null) throw new IllegalArgumentException("Selecione uma tabela.");
        return table;
    }

    void dispose() { disposed = true; if (worker != null) worker.cancel(true); worker = null; }
}
