package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

final class ManagementFileEditor extends UserDataHolderBase implements FileEditor {
    private final Project project;
    private final ManagementFile file;
    private final ConnectionWorkspace workspace;
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private boolean disposed;

    ManagementFileEditor(Project project, ManagementFile file) {
        this.project = project;
        this.file = file;
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
        workspace = new ConnectionWorkspace(backend::connections, choice -> {
            var panel = new JPanel(new BorderLayout(0, 8));
            var header = new JLabel("  " + choice.label(), ToolkitIcons.TOOLKIT, SwingConstants.LEFT);
            header.putClientProperty("html.disable", Boolean.TRUE);
            header.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            var color = ConnectionColors.color(project, choice.id());
            if (color != null) header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, color), header.getBorder()));
            panel.add(header, BorderLayout.NORTH);
            var tabs = new JTabbedPane();
            var disposers = new ArrayList<Runnable>();
            var overview = new ServerDataPanel(() -> backend.open(choice), false);
            disposers.add(overview::dispose);
            tabs.addTab("Overview", overview);
            tabs.addTab("Processes", new JPanel());
            tabs.addTab("Users & Access", new JPanel());
            var loaded = new boolean[]{true, false, false};
            tabs.addChangeListener(event -> {
                int index = tabs.getSelectedIndex();
                if (loaded[index]) return;
                loaded[index] = true;
                if (index == 1) {
                    var processes = new ServerDataPanel(() -> backend.open(choice), true);
                    disposers.add(processes::dispose);
                    tabs.setComponentAt(index, processes);
                } else {
                    var admin = new AccountAdminPanel(backend, choice.id());
                    disposers.add(admin::dispose);
                    tabs.setComponentAt(index, admin);
                }
            });
            panel.add(tabs, BorderLayout.CENTER);
            panel.putClientProperty("management.tabs", tabs);
            return new ConnectionWorkspace.View(panel, () -> disposers.forEach(Runnable::run));
        }, () -> project.getService(AccountManagerWindows.class).focusExplorer());
        workspace.onSelectionChanged(id -> {
            file.connectionId = id;
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).updateFileColor(file);
        });
    }

    String connectionId() { return workspace.selectedId(); }
    void showSection(int section) {
        var view = workspace.currentView();
        if (section >= 0 && section <= 2 && view != null && view.getClientProperty("management.tabs") instanceof JTabbedPane tabs) tabs.setSelectedIndex(section);
    }
    void selectConnection(String id) {
        workspace.refresh();
        workspace.select(id);
        file.connectionId = workspace.selectedId();
    }
    @Override public JComponent getComponent() { return workspace; }
    @Override public JComponent getPreferredFocusedComponent() { return workspace; }
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
        workspace.dispose();
        if (!project.isDisposed()) project.getService(AccountManagerWindows.class).leaveManagement();
    }
}
