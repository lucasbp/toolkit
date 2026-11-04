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
        super(project, false, IdeModalityType.MODELESS);
        admin = new AccountAdminPanel(backend);
        admin.setPreferredSize(new Dimension(1120, 760));
        setTitle("SQL Toolkit: usuários e permissões");
        init();
        setResizable(true);
        getWindow().setMinimumSize(new Dimension(960, 680));
        Disposer.register(getDisposable(), admin::dispose);
    }

    @Override protected JComponent createCenterPanel() { return admin; }
    @Override protected Action @NotNull [] createActions() { return new Action[0]; }
    @Override protected String getDimensionServiceKey() { return "io.github.lucasbp.toolkit.account-manager"; }
}
