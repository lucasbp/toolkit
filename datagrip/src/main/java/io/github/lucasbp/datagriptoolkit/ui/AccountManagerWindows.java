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
import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class AccountManagerWindows implements Disposable {
    private final Project project;
    private final Map<String, ManagementFile> files = new HashMap<>();
    private boolean managing;
    private boolean restoreServices;

    public AccountManagerWindows(Project project) { this.project = project; }

    static String sourceId(com.intellij.psi.PsiElement element) {
        if (!(element instanceof DbElement database) || !database.isValid()) return null;
        var source = database.getDataSource();
        if (source == null || !(source.getDelegateDataSource() instanceof LocalDataSource local)) return null;
        return SqlConsoleBridge.supports(local) ? local.getUniqueId() : null;
    }

    void openCurrent() {
        String id = ReadAction.computeBlocking(() -> {
            String explorer = null;
            var view = DatabaseView.getDatabaseViewIfVisible(project);
            if (view != null && view.getPanel() != null) {
                var context = DataManager.getInstance().getDataContext(view.getPanel());
                explorer = sourceId(CommonDataKeys.PSI_ELEMENT.getData(context));
            }
            String console = null;
            var editor = FileEditorManager.getInstance(project).getSelectedEditor();
            if (editor instanceof ManagementFileEditor management) return ConnectionContext.resolve(explorer, management.getFile().connectionId);
            if (editor != null && editor.getFile() != null) {
                var jdbc = JdbcConsoleProvider.getConsole(project, editor.getFile());
                if (jdbc != null && jdbc.getDataSource() != null && SqlConsoleBridge.supports(jdbc.getDataSource())) console = jdbc.getDataSource().getUniqueId();
            }
            return ConnectionContext.resolve(explorer, console);
        });
        open(id);
    }

    void open(String id) {
        var bridge = new SqlConsoleBridge(project);
        String key = id == null ? "" : id;
        String title = id == null ? "Server Management" : "Management · " + bridge.source(id).source().getName();
        var file = files.computeIfAbsent(key, ignored -> new ManagementFile(title, id));
        FileEditorManager.getInstance(project).openFile(file, true);
        var launcher = ToolWindowManager.getInstance(project).getToolWindow("Server Management");
        if (launcher != null && launcher.isVisible()) launcher.hide();
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

    @Override public void dispose() { files.clear(); }
}
