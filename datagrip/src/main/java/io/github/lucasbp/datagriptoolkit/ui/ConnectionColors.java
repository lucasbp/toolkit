package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.database.psi.DbPsiFacade;
import com.intellij.database.view.DatabaseColorManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;

final class ConnectionColors {
    private ConnectionColors() {}

    static java.awt.Color color(Project project, String id) {
        if (id == null || project.isDisposed()) return null;
        return ReadAction.computeBlocking(() -> {
            var source = DbPsiFacade.getInstance(project).findDataSource(id);
            var colors = source == null ? null : DatabaseColorManager.getEffectiveColorBunch(project, source);
            return colors == null ? null : colors.getFileColor();
        });
    }
}
