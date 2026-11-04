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
        var backend = new AccountAdminPanel.Backend() {
            public List<AccountAdminPanel.ConnectionChoice> connections() {
                return bridge.sources().stream().map(source -> new AccountAdminPanel.ConnectionChoice(source.source().getUniqueId(), source.source().getName())).toList();
            }
            public SqlExecutor open(AccountAdminPanel.ConnectionChoice connection) { return bridge.executor(connection.id()); }
            public List<AccountAdminPanel.TableChoice> tables(AccountAdminPanel.ConnectionChoice connection) {
                return bridge.tables(bridge.source(connection.id())).stream().map(table -> new AccountAdminPanel.TableChoice(table.schema(), table.table())).toList();
            }
        };
        var manager = new java.util.concurrent.atomic.AtomicReference<AccountManagerDialog>();
        var launcher = Forms.stack();
        var description = new javax.swing.JTextArea("Edite usuários, roles e permissões por schema ou tabela em uma janela própria, com revisão antes de aplicar.");
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 85));
        launcher.add(description);
        Forms.button(launcher, "Abrir gerenciador de usuários e permissões", () -> {
            var current = manager.get();
            if (current != null && current.getWindow() != null && current.getWindow().isShowing()) {
                current.getWindow().toFront();
                return;
            }
            var dialog = new AccountManagerDialog(project, backend);
            manager.set(dialog);
            dialog.show();
        });
        var quick = new QuickQueriesPanel(bridge);
        var tabs = new JTabbedPane();
        tabs.addTab("Usuários e permissões", new javax.swing.JScrollPane(launcher));
        tabs.addTab("Consultas rápidas", quick);
        tabs.addTab("Montar SELECT", new SelectBuilderPanel(bridge));
        var content = ContentFactory.getInstance().createContent(tabs, "", false);
        content.setDisposer(() -> {
            var current = manager.get();
            if (current != null && current.getWindow() != null && current.getWindow().isShowing()) current.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE);
            quick.dispose();
        });
        toolWindow.getContentManager().addContent(content);
    }
}
