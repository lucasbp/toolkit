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

public final class ManageAccountsAction extends AnAction implements DumbAware {
    public ManageAccountsAction() {
        super("Server Management: usuários e permissões", "Administrar a conexão selecionada na área central", ToolkitIcons.TOOLKIT);
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
                if (id == null) manager.openCurrent();
                else manager.open(id);
            }
            catch (IllegalArgumentException problem) { Messages.showWarningDialog(project, problem.getMessage(), "Usuários e permissões"); }
        }).submit(AppExecutorUtil.getAppExecutorService());
    }
}
