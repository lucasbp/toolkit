package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public final class ManagementEditorProvider implements FileEditorProvider, DumbAware {
    @Override public boolean accept(Project project, VirtualFile file) { return file instanceof ManagementFile; }
    @Override public FileEditor createEditor(Project project, VirtualFile file) { return new ManagementFileEditor(project, (ManagementFile) file); }
    @Override public String getEditorTypeId() { return "lucasbp.server-management"; }
    @Override public FileEditorPolicy getPolicy() { return FileEditorPolicy.HIDE_DEFAULT_EDITOR; }
}
