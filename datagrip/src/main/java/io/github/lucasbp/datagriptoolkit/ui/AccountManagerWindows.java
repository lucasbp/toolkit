package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class AccountManagerWindows implements Disposable {
    private final Project project;
    private final Map<String, AccountManagerDialog> windows = new HashMap<>();

    public AccountManagerWindows(Project project) { this.project = project; }

    void open(String id) {
        var current = windows.get(id);
        if (current != null && current.getWindow() != null && current.getWindow().isShowing()) {
            current.getWindow().toFront();
            current.getWindow().requestFocus();
            return;
        }
        var bridge = new SqlConsoleBridge(project);
        bridge.source(id);
        var backend = new AccountAdminPanel.Backend() {
            public List<AccountAdminPanel.ConnectionChoice> connections() {
                return bridge.sources().stream().map(source -> new AccountAdminPanel.ConnectionChoice(source.source().getUniqueId(), source.source().getName())).toList();
            }
            public SqlExecutor open(AccountAdminPanel.ConnectionChoice connection) { return bridge.executor(connection.id()); }
            public List<AccountAdminPanel.TableChoice> tables(AccountAdminPanel.ConnectionChoice connection) {
                return bridge.tables(bridge.source(connection.id())).stream().map(table -> new AccountAdminPanel.TableChoice(table.schema(), table.table())).toList();
            }
        };
        var dialog = new AccountManagerDialog(project, backend, id);
        windows.put(id, dialog);
        dialog.show();
    }

    @Override public void dispose() {
        for (var dialog : windows.values()) {
            if (dialog.getWindow() != null && dialog.getWindow().isShowing()) dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
        windows.clear();
    }
}
