package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.database.console.JdbcConsoleProvider;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.psi.DbElement;
import com.intellij.database.view.DatabaseView;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.event.TreeSelectionListener;

@Service(Service.Level.PROJECT)
public final class AccountManagerWindows implements Disposable {
    private final Project project;
    private ManagementFile file;
    private boolean managing;
    private boolean restoreServices;
    private JTree explorerTree;
    private final Timer selectionTimer;
    private final TreeSelectionListener selectionListener;
    private javax.swing.JComponent explorerComponent;
    private final java.awt.event.HierarchyListener explorerVisibilityListener;

    public AccountManagerWindows(Project project) {
        this.project = project;
        explorerVisibilityListener = event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                ApplicationManager.getApplication().invokeLater(() -> { if (!project.isDisposed()) bindExplorer(); });
            }
        };
        selectionTimer = new Timer(150, event -> followExplorerSelection());
        selectionTimer.setRepeats(false);
        selectionListener = event -> selectionTimer.restart();
    }

    static String sourceId(com.intellij.psi.PsiElement element) {
        if (!(element instanceof DbElement database) || !database.isValid()) return null;
        var source = database.getDataSource();
        if (source == null || !(source.getDelegateDataSource() instanceof LocalDataSource local)) return null;
        return SqlConsoleBridge.supports(local) ? local.getUniqueId() : null;
    }

    void openCurrent() {
        openCurrent(-1);
    }

    void openCurrent(int section) {
        String id = ReadAction.computeBlocking(() -> {
            String explorer = null;
            var view = DatabaseView.getDatabaseViewIfVisible(project);
            if (view != null && view.getPanel() != null) {
                var context = DataManager.getInstance().getDataContext(view.getPanel());
                explorer = sourceId(CommonDataKeys.PSI_ELEMENT.getData(context));
                var tree = findTree(view.getPanel().getTreeComponent());
                if (tree != null && tree.getSelectionCount() > 0) return explorer;
            }
            String console = null;
            var editor = FileEditorManager.getInstance(project).getSelectedEditor();
            if (editor instanceof ManagementFileEditor management) return ConnectionContext.resolve(explorer, management.connectionId());
            if (editor != null && editor.getFile() != null) {
                var jdbc = JdbcConsoleProvider.getConsole(project, editor.getFile());
                if (jdbc != null && jdbc.getDataSource() != null && SqlConsoleBridge.supports(jdbc.getDataSource())) console = jdbc.getDataSource().getUniqueId();
            }
            return ConnectionContext.resolve(explorer, console);
        });
        open(id, section);
    }

    void open(String id) {
        open(id, -1);
    }

    void open(String id, int section) {
        var bridge = new SqlConsoleBridge(project);
        if (id != null) bridge.source(id);
        if (file == null) file = new ManagementFile("Server Management", null);
        var editors = FileEditorManager.getInstance(project).openFile(file, true);
        for (var editor : editors) if (editor instanceof ManagementFileEditor management) {
            management.selectConnection(id);
            management.showSection(section);
        }
        var launcher = ToolWindowManager.getInstance(project).getToolWindow("Server Management");
        if (launcher != null && launcher.isVisible()) launcher.hide();
        bindExplorer();
        if (id == null) focusExplorer();
    }

    void focusExplorer() {
        var window = ToolWindowManager.getInstance(project).getToolWindow("Database");
        if (window == null) return;
        window.activate(() -> {
            if (project.isDisposed()) return;
            bindExplorer();
            var view = DatabaseView.getDatabaseViewIfVisible(project);
            if (view != null && view.getPanel() != null) view.getPanel().requestFocusOnTree();
            followExplorerSelection();
        });
    }

    private void bindExplorer() {
        var window = ToolWindowManager.getInstance(project).getToolWindow("Database");
        var component = window == null ? null : window.getComponent();
        if (component != explorerComponent) {
            if (explorerComponent != null) explorerComponent.removeHierarchyListener(explorerVisibilityListener);
            explorerComponent = component;
            if (component != null) component.addHierarchyListener(explorerVisibilityListener);
        }
        var view = DatabaseView.getDatabaseViewIfVisible(project);
        var tree = view == null || view.getPanel() == null ? null : findTree(view.getPanel().getTreeComponent());
        if (tree == explorerTree) return;
        if (explorerTree != null) explorerTree.removeTreeSelectionListener(selectionListener);
        explorerTree = tree;
        if (tree != null) tree.addTreeSelectionListener(selectionListener);
    }

    private void followExplorerSelection() {
        if (project.isDisposed()) return;
        if (!(FileEditorManager.getInstance(project).getSelectedEditor() instanceof ManagementFileEditor management)) return;
        if (DatabaseView.getDatabaseViewIfVisible(project) == null) return;
        String id = ReadAction.computeBlocking(() -> {
            var view = DatabaseView.getDatabaseViewIfVisible(project);
            if (view == null || view.getPanel() == null) return null;
            var context = DataManager.getInstance().getDataContext(view.getPanel());
            return sourceId(CommonDataKeys.PSI_ELEMENT.getData(context));
        });
        if (java.util.Objects.equals(id, management.connectionId())) return;
        try { management.selectConnection(id); }
        catch (IllegalArgumentException problem) { management.selectConnection(null); }
    }

    private static JTree findTree(Component component) {
        if (component instanceof JTree tree) return tree;
        if (component instanceof Container container) {
            for (var child : container.getComponents()) {
                var tree = findTree(child);
                if (tree != null) return tree;
            }
        }
        return null;
    }

    void enterManagement() {
        if (managing) return;
        managing = true;
        var services = ToolWindowManager.getInstance(project).getToolWindow("Services");
        restoreServices = services != null && services.isVisible();
        if (restoreServices) services.hide();
    }

    void leaveManagement() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || FileEditorManager.getInstance(project).getSelectedEditor() instanceof ManagementFileEditor) return;
            managing = false;
            var services = ToolWindowManager.getInstance(project).getToolWindow("Services");
            if (restoreServices && services != null) services.show();
            restoreServices = false;
        });
    }

    @Override public void dispose() {
        selectionTimer.stop();
        if (explorerTree != null) explorerTree.removeTreeSelectionListener(selectionListener);
        if (explorerComponent != null) explorerComponent.removeHierarchyListener(explorerVisibilityListener);
        explorerComponent = null;
        explorerTree = null;
        file = null;
    }
}
