package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.database.console.JdbcConsole;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.BorderLayout;
import javax.swing.*;

public final class ConsoleQueryAction extends AnAction implements DumbAware {
    private static final Key<JPanel> PANEL = Key.create("lucasbp.inline-select");

    public ConsoleQueryAction() { super("Build SELECT…", "Choose a table and build a SELECT in this SQL console", ToolkitIcons.TOOLKIT); }
    @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(AnActionEvent event) {
        var console = JdbcConsole.findConsole(event);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && event.getData(CommonDataKeys.EDITOR) != null && console != null && console.getDataSource() != null && SqlConsoleBridge.supports(console.getDataSource()));
    }

    @Override public void actionPerformed(AnActionEvent event) {
        var project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        var console = JdbcConsole.findConsole(event);
        if (project == null || editor == null || console == null || console.getDataSource() == null) return;
        if (editor.getUserData(PANEL) != null) { editor.getUserData(PANEL).requestFocusInWindow(); return; }
        String id = console.getDataSource().getUniqueId();
        var bridge = new SqlConsoleBridge(project);
        ReadAction.nonBlocking(() -> bridge.tables(bridge.source(id))).expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState(), tables -> {
                if (editor.isDisposed()) return;
                if (editor.getUserData(PANEL) != null) return;
                var previous = editor.getHeaderComponent();
                var wrapper = new JPanel(new BorderLayout());
                if (previous != null) wrapper.add(previous, BorderLayout.NORTH);
                Runnable close = () -> {
                    if (!editor.isDisposed() && editor.getUserData(PANEL) == wrapper) editor.setHeaderComponent(previous);
                    editor.putUserData(PANEL, null);
                };
                var form = new ConsoleQueryPanel(tables.stream().map(table -> new ConsoleQueryPanel.Table(table.schema(), table.table(), table.columns())).toList(), sql -> {
                    if (editor.isDisposed() || console.getDataSource() == null || !id.equals(console.getDataSource().getUniqueId())) throw new IllegalArgumentException("The console connection changed. Reopen Build SELECT.");
                    bridge.source(id);
                    if (!editor.getDocument().isWritable()) throw new IllegalArgumentException("This console is read-only.");
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        var document = editor.getDocument();
                        int offset = document.getTextLength();
                        String text = (offset == 0 ? "" : "\n\n") + sql + "\n";
                        document.insertString(offset, text);
                        editor.getCaretModel().moveToOffset(offset + (offset == 0 ? 0 : 2));
                        editor.getSelectionModel().setSelection(editor.getCaretModel().getOffset(), offset + text.length());
                    });
                    close.run();
                    editor.getContentComponent().requestFocusInWindow();
                }, close);
                var scroll = new JScrollPane(form);
                scroll.setPreferredSize(new java.awt.Dimension(900, 390));
                wrapper.add(scroll, BorderLayout.CENTER);
                editor.putUserData(PANEL, wrapper);
                editor.setHeaderComponent(wrapper);
                form.search.requestFocusInWindow();
            }).submit(AppExecutorUtil.getAppExecutorService()).onError(problem -> com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) Messages.showWarningDialog(project, "Could not read table metadata. Refresh the data source introspection and try again.", "Build SELECT");
            }));
    }
}
