package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.psi.DbElement;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

public class ManageAccountsAction extends AnAction implements DumbAware {
    private final int section;
    public ManageAccountsAction() {
        this("Server Management", 0);
    }

    protected ManageAccountsAction(String name, int section) {
        super(name, "Manage the selected connection in the central editor", ToolkitIcons.TOOLKIT);
        this.section = section;
    }

    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }

    private static LocalDataSource selected(com.intellij.psi.PsiElement element) {
        if (!(element instanceof DbElement database) || !database.isValid()) return null;
        var source = database.getDataSource();
        if (source == null || !(source.getDelegateDataSource() instanceof LocalDataSource local)) return null;
        return SqlConsoleBridge.supports(local) ? local : null;
    }

    @Override public void update(@NotNull AnActionEvent event) {
        var element = event.getData(CommonDataKeys.PSI_ELEMENT);
        boolean available = event.getProject() != null && (element == null || selected(element) != null);
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null) return;
        var element = event.getData(CommonDataKeys.PSI_ELEMENT);
        ReadAction.nonBlocking(() -> {
            var source = selected(element);
            return source == null ? null : source.getUniqueId();
        }).expireWith(project).finishOnUiThread(ModalityState.defaultModalityState(), id -> {
            try {
                var manager = project.getService(AccountManagerWindows.class);
                if (id == null) manager.openCurrent(section);
                else manager.open(id, section);
            }
            catch (IllegalArgumentException problem) { Messages.showWarningDialog(project, problem.getMessage(), "Users & Access"); }
        }).submit(AppExecutorUtil.getAppExecutorService());
    }

    public static final class Overview extends ManageAccountsAction { public Overview() { super("Server Info", 0); } }
    public static final class Processes extends ManageAccountsAction { public Processes() { super("Processes", 1); } }
    public static final class Users extends ManageAccountsAction { public Users() { super("User Accounts", 2); } }
}
