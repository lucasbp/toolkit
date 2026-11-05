package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

import com.intellij.ui.content.ContentFactory;
import javax.swing.JPanel;

public final class ToolkitToolWindowFactory implements ToolWindowFactory {
    @Override public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        var body = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        Runnable open = () -> {
            if (project.isDisposed() || !toolWindow.isVisible()) return;
            try { project.getService(AccountManagerWindows.class).openCurrent(); }
            catch (IllegalArgumentException problem) { Messages.showWarningDialog(project, problem.getMessage(), "Server Management"); }
        };
        Forms.button(body, "Abrir Server Management", open);
        var content = ContentFactory.getInstance().createContent(body, "", false);
        toolWindow.getContentManager().addContent(content);
        body.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && body.isShowing()) {
                ApplicationManager.getApplication().invokeLater(open);
            }
        });
        ApplicationManager.getApplication().invokeLater(open);
    }
}
