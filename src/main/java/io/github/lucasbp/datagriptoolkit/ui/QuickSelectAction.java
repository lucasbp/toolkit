package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.QueryTemplates;
import com.intellij.database.psi.DbTable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class QuickSelectAction extends AnAction implements DumbAware {
    protected String sql(SqlConsoleBridge.TableChoice table) { return QueryTemplates.firstThousand(table.schema(), table.table()); }

    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }

    @Override public void update(@NotNull AnActionEvent event) {
        var element = event.getData(CommonDataKeys.PSI_ELEMENT);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && element instanceof DbTable table && SqlConsoleBridge.table(table) != null);
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null || !(event.getData(CommonDataKeys.PSI_ELEMENT) instanceof DbTable table)) return;
        var selected = SqlConsoleBridge.table(table);
        if (selected == null) return;
        try {
            var bridge = new SqlConsoleBridge(event.getProject());
            bridge.open(bridge.source(selected.sourceId()), sql(selected));
        } catch (IllegalArgumentException problem) { Messages.showWarningDialog(event.getProject(), problem.getMessage(), "DataGrip Toolkit"); }
    }

    public static final class Columns extends QuickSelectAction {
        @Override protected String sql(SqlConsoleBridge.TableChoice table) { return QueryTemplates.listedColumns(table.schema(), table.table(), table.columns()); }
    }

    public static final class Count extends QuickSelectAction {
        @Override protected String sql(SqlConsoleBridge.TableChoice table) { return QueryTemplates.count(table.schema(), table.table()); }
    }
}
