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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import javax.swing.JComboBox;

final class SqlConsoleBridge {
    private final Project project;

    SqlConsoleBridge(Project project) { this.project = project; }

    record Source(LocalDataSource source) {
        @Override public String toString() { return source.getName(); }
    }

    List<Source> sources() {
        return LocalDataSourceManager.getInstance(project).getDataSources().stream()
            .filter(SqlConsoleBridge::supports)
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
        var result = ReadAction.nonBlocking(() -> {
            checkCanceled();
            var data = DbPsiFacade.getInstance(project).findDataSource(selected.source().getUniqueId());
            if (data == null) throw new IllegalArgumentException("Atualize a introspecção da conexão no DataGrip.");
            var snapshot = new ArrayList<TableChoice>();
            for (var table : DasUtil.getTables(data)) {
                checkCanceled();
                String schema = DasUtil.getSchema(table);
                if (schema == null || schema.isBlank()) schema = DasUtil.getCatalog(table);
                if (schema == null || schema.isBlank()) continue;
                var columns = new ArrayList<String>();
                for (var column : DasUtil.getColumns(table)) {
                    checkCanceled();
                    columns.add(column.getName());
                }
                snapshot.add(new TableChoice(selected.source().getUniqueId(), schema, table.getName(), List.copyOf(columns)));
            }
            return snapshot;
        }).expireWith(project).executeSynchronously();
        checkCanceled();
        result.sort((left, right) -> {
            checkCanceled();
            return left.toString().compareTo(right.toString());
        });
        return List.copyOf(result);
    }

    static boolean supports(DbTable table) {
        if (!table.isValid()) return false;
        var dataSource = table.getDataSource();
        return dataSource != null && dataSource.getDelegateDataSource() instanceof LocalDataSource source
            && supports(source);
    }

    static boolean supports(LocalDataSource source) {
        return source.getDbms() == com.intellij.database.Dbms.MYSQL || source.getDbms() == com.intellij.database.Dbms.MYSQL_AURORA;
    }

    static TableChoice table(DbTable table) {
        checkCanceled();
        if (!supports(table)) return null;
        var dataSource = table.getDataSource();
        var source = (LocalDataSource) dataSource.getDelegateDataSource();
        String schema = DasUtil.getSchema(table.getDasObject());
        if (schema == null || schema.isBlank()) schema = DasUtil.getCatalog(table.getDasObject());
        if (schema == null || schema.isBlank()) return null;
        var columns = new ArrayList<String>();
        for (var column : DasUtil.getColumns(table.getDasObject())) {
            checkCanceled();
            columns.add(column.getName());
        }
        return new TableChoice(source.getUniqueId(), schema, table.getName(), List.copyOf(columns));
    }

    private static void checkCanceled() {
        if (Thread.currentThread().isInterrupted()) throw new ProcessCanceledException();
        ProgressManager.checkCanceled();
    }

    void refresh(JComboBox<Source> selector) {
        var previous = (Source) selector.getSelectedItem();
        selector.removeAllItems();
        for (var source : LocalDataSourceManager.getInstance(project).getDataSources()) {
            if (supports(source)) selector.addItem(new Source(source));
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
        WriteCommandAction.runWriteCommandAction(project, () -> {
            var file = com.intellij.database.editor.DatabaseEditorHelper.createNewConsoleVirtualFile(selected.source());
            if (file == null) throw new IllegalArgumentException("Não foi possível criar o arquivo de consulta para esta conexão.");
            var console = JdbcConsole.newConsole(project).fromDataSource(selected.source()).forFile(file).build();
            console.getDocument().setText(sql);
            FileEditorManager.getInstance(project).openFile(console.getVirtualFile(), true);
        });
    }
}
