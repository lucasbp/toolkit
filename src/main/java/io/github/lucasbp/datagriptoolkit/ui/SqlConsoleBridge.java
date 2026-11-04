package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.database.console.JdbcConsole;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.dataSource.LocalDataSourceManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.database.psi.DbTable;
import com.intellij.database.util.DasUtil;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import java.util.List;
import java.util.ArrayList;
import com.intellij.openapi.application.ReadAction;
import javax.swing.JComboBox;

final class SqlConsoleBridge {
    private final Project project;

    SqlConsoleBridge(Project project) { this.project = project; }

    record Source(LocalDataSource source) {
        @Override public String toString() { return source.getName(); }
    }

    List<Source> sources() {
        return LocalDataSourceManager.getInstance(project).getDataSources().stream()
            .filter(source -> source.getDbms().getName().equalsIgnoreCase("MySQL"))
            .map(Source::new).toList();
    }

    SqlExecutor executor(String id) {
        return new DataGripSqlExecutor(project, source(id).source());
    }

    Source source(String id) {
        return sources().stream().filter(item -> item.source().getUniqueId().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Selecione uma conexão MySQL válida do DataGrip."));
    }

    record TableChoice(String sourceId, String schema, String table, List<String> columns) {
        @Override public String toString() { return schema + "." + table; }
    }

    List<TableChoice> tables(Source selected) {
        if (selected == null) throw new IllegalArgumentException("Selecione uma conexão MySQL.");
        return ReadAction.compute(() -> {
            var data = DbPsiFacade.getInstance(project).findDataSource(selected.source().getUniqueId());
            if (data == null) throw new IllegalArgumentException("Atualize a introspecção da conexão no DataGrip.");
            var result = new ArrayList<TableChoice>();
            for (var table : DasUtil.getTables(data)) {
                String schema = DasUtil.getSchema(table);
                if (schema == null || schema.isBlank()) schema = DasUtil.getCatalog(table);
                if (schema == null || schema.isBlank()) continue;
                var columns = DasUtil.getColumns(table).map(column -> column.getName()).toList();
                result.add(new TableChoice(selected.source().getUniqueId(), schema, table.getName(), columns));
            }
            result.sort(java.util.Comparator.comparing(TableChoice::toString));
            return List.copyOf(result);
        });
    }

    static TableChoice table(DbTable table) {
        var dataSource = table.getDataSource();
        if (!(dataSource.getDelegateDataSource() instanceof LocalDataSource source) || !source.getDbms().getName().equalsIgnoreCase("MySQL")) return null;
        String schema = DasUtil.getSchema(table.getDasObject());
        if (schema == null || schema.isBlank()) schema = DasUtil.getCatalog(table.getDasObject());
        if (schema == null || schema.isBlank()) return null;
        return new TableChoice(source.getUniqueId(), schema, table.getName(), DasUtil.getColumns(table.getDasObject()).map(column -> column.getName()).toList());
    }

    void refresh(JComboBox<Source> selector) {
        var previous = (Source) selector.getSelectedItem();
        selector.removeAllItems();
        for (var source : LocalDataSourceManager.getInstance(project).getDataSources()) {
            if (source.getDbms().getName().equalsIgnoreCase("MySQL")) selector.addItem(new Source(source));
        }
        selector.setSelectedIndex(-1);
        if (previous != null) {
            for (int i = 0; i < selector.getItemCount(); i++) {
                if (selector.getItemAt(i).source().getUniqueId().equals(previous.source().getUniqueId())) {
                    selector.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    void open(Source selected, String sql) {
        if (selected == null) throw new IllegalArgumentException("Selecione uma conexão MySQL do DataGrip.");
        if (sql.isBlank()) throw new IllegalArgumentException("Gere o SQL antes de abrir o console.");
        if (!LocalDataSourceManager.getInstance(project).getDataSources().contains(selected.source())) {
            throw new IllegalArgumentException("A conexão foi removida. Atualize a lista.");
        }
        var console = JdbcConsole.newConsole(project).fromDataSource(selected.source()).build();
        WriteCommandAction.runWriteCommandAction(project, () -> console.getDocument().setText(sql));
        FileEditorManager.getInstance(project).openFile(console.getVirtualFile(), true);
    }
}
