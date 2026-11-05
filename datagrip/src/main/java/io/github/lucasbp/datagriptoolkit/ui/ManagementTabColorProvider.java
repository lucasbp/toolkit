package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public final class ManagementTabColorProvider implements EditorTabColorProvider {
    @Override public java.awt.Color getEditorTabColor(Project project, VirtualFile file) {
        return file instanceof ManagementFile management ? ConnectionColors.color(project, management.connectionId) : null;
    }
}
