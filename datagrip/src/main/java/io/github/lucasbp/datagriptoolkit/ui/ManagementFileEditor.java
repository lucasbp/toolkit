package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import javax.swing.*;

final class ManagementFileEditor extends UserDataHolderBase implements FileEditor {
    private final Project project;
    private final ManagementFile file;
    private final JPanel body = new JPanel(new BorderLayout(0, 8));
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private AccountAdminPanel admin;
    private QuickQueriesPanel quick;
    private boolean disposed;

    ManagementFileEditor(Project project, ManagementFile file) {
        this.project = project;
        this.file = file;
        var bridge = new SqlConsoleBridge(project);
        if (file.connectionId == null) {
            var form = Forms.stack();
            var connections = new JComboBox<SqlConsoleBridge.Source>();
            connections.setRenderer(Forms.literalRenderer());
            form.add(new JLabel("Escolha a conexão para abrir o Server Management."));
            Forms.field(form, "Conexão MySQL / Aurora", connections);
            Forms.button(form, "Atualizar conexões", () -> bridge.refresh(connections));
            Forms.button(form, "Gerenciar conexão", () -> {
                var selected = (SqlConsoleBridge.Source) connections.getSelectedItem();
                if (selected == null) throw new IllegalArgumentException("Selecione uma conexão.");
                project.getService(AccountManagerWindows.class).open(selected.source().getUniqueId());
            });
            bridge.refresh(connections);
            body.add(new JScrollPane(form), BorderLayout.CENTER);
            return;
        }
        var backend = new AccountAdminPanel.Backend() {
            public List<AccountAdminPanel.ConnectionChoice> connections() {
                return bridge.sources().stream().map(source -> new AccountAdminPanel.ConnectionChoice(source.source().getUniqueId(), source.source().getName())).toList();
            }
            public SqlExecutor open(AccountAdminPanel.ConnectionChoice connection) { return bridge.executor(connection.id()); }
            public List<AccountAdminPanel.TableChoice> tables(AccountAdminPanel.ConnectionChoice connection) {
                return bridge.tables(bridge.source(connection.id())).stream().map(table -> new AccountAdminPanel.TableChoice(table.schema(), table.table())).toList();
            }
        };
        var header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        var title = new JLabel("Server Management · " + bridge.source(file.connectionId).source().getName(), ToolkitIcons.TOOLKIT, SwingConstants.LEFT);
        title.putClientProperty("html.disable", Boolean.TRUE);
        header.add(title);
        Forms.button(header, "Outra conexão", () -> project.getService(AccountManagerWindows.class).open(null));
        body.add(header, BorderLayout.NORTH);
        admin = new AccountAdminPanel(backend, file.connectionId);
        quick = new QuickQueriesPanel(bridge, file.connectionId);
        var tabs = new JTabbedPane();
        tabs.addTab("Usuários e permissões", admin);
        tabs.addTab("Consultas rápidas", quick);
        tabs.addTab("Montar SELECT", new SelectBuilderPanel(bridge, file.connectionId));
        body.add(tabs, BorderLayout.CENTER);
    }

    @Override public JComponent getComponent() { return body; }
    @Override public JComponent getPreferredFocusedComponent() { return body; }
    @Override public String getName() { return "Server Management"; }
    @Override public ManagementFile getFile() { return file; }
    @Override public void setState(FileEditorState state) {}
    @Override public boolean isModified() { return false; }
    @Override public boolean isValid() { return !disposed && file.isValid(); }
    @Override public void addPropertyChangeListener(PropertyChangeListener listener) { changes.addPropertyChangeListener(listener); }
    @Override public void removePropertyChangeListener(PropertyChangeListener listener) { changes.removePropertyChangeListener(listener); }
    @Override public void selectNotify() { project.getService(AccountManagerWindows.class).enterManagement(); }
    @Override public void deselectNotify() { project.getService(AccountManagerWindows.class).leaveManagement(); }
    @Override public void dispose() {
        disposed = true;
        if (admin != null) admin.dispose();
        if (quick != null) quick.dispose();
        if (!project.isDisposed()) project.getService(AccountManagerWindows.class).leaveManagement();
    }
}
