package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import java.util.List;
import javax.swing.JTabbedPane;
import org.jetbrains.annotations.NotNull;

public final class ToolkitToolWindowFactory implements ToolWindowFactory {
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var bridge = new SqlConsoleBridge(project);
        var admin = new AccountAdminPanel(new AccountAdminPanel.Backend() {
            public List<AccountAdminPanel.ConnectionChoice> connections() {
                return bridge.sources().stream().map(source -> new AccountAdminPanel.ConnectionChoice(source.source().getUniqueId(), source.source().getName())).toList();
            }
            public SqlExecutor open(AccountAdminPanel.ConnectionChoice connection) { return bridge.executor(connection.id()); }
            public List<AccountAdminPanel.TableChoice> tables(AccountAdminPanel.ConnectionChoice connection) {
                return bridge.tables(bridge.source(connection.id())).stream().map(table -> new AccountAdminPanel.TableChoice(table.schema(), table.table())).toList();
            }
        });
        var quick = new QuickQueriesPanel(bridge);
        var tabs = new JTabbedPane();
        tabs.addTab("Usuários e permissões", admin);
        tabs.addTab("Consultas rápidas", quick);
        tabs.addTab("Montar SELECT", new SelectBuilderPanel(bridge));
        var content = ContentFactory.getInstance().createContent(tabs, "", false);
        content.setDisposer(() -> { admin.dispose(); quick.dispose(); });
        toolWindow.getContentManager().addContent(content);
    }
}
