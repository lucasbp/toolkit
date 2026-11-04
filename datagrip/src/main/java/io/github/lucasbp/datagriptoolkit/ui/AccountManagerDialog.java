package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import java.awt.Dimension;
import javax.swing.Action;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

final class AccountManagerDialog extends DialogWrapper {
    private final AccountAdminPanel admin;

    AccountManagerDialog(Project project, AccountAdminPanel.Backend backend) {
        this(project, backend, null);
    }

    AccountManagerDialog(Project project, AccountAdminPanel.Backend backend, String connectionId) {
        super(project, false, IdeModalityType.MODELESS);
        admin = new AccountAdminPanel(backend, connectionId);
        admin.setPreferredSize(new Dimension(1120, 760));
        String name = connectionId == null ? "" : backend.connections().stream().filter(item -> connectionId.equals(item.id())).findFirst().map(item -> " · " + item.label()).orElse("");
        setTitle("Usuários e permissões" + name);
        init();
        setResizable(true);
        getWindow().setMinimumSize(new Dimension(960, 680));
        Disposer.register(getDisposable(), admin::dispose);
    }

    @Override protected JComponent createCenterPanel() { return admin; }
    @Override protected Action @NotNull [] createActions() { return new Action[0]; }
    @Override protected String getDimensionServiceKey() { return "io.github.lucasbp.toolkit.account-manager"; }
}
