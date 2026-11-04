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
        var launcher = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12));
        launcher.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var description = new javax.swing.JTextArea("Contas, roles e permissões por schema ou tabela. Abra o gerenciador para revisar e aplicar mudanças.", 3, 20);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setFont(javax.swing.UIManager.getFont("Label.font"));
        launcher.add(description, java.awt.BorderLayout.NORTH);
        var launcherActions = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        launcher.add(launcherActions, java.awt.BorderLayout.CENTER);
        Forms.button(launcherActions, "Abrir gerenciador", () -> {
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
