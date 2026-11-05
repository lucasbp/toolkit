package io.github.lucasbp.datagriptoolkit.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import javax.swing.Icon;

final class ManagementFile extends LightVirtualFile {
    final String connectionId;

    ManagementFile(String name, String connectionId) {
        super(name, new FileType() {
            public String getName() { return "Server Management"; }
            public String getDescription() { return "Server Management"; }
            public String getDefaultExtension() { return "servermanagement"; }
            public Icon getIcon() { return ToolkitIcons.TOOLKIT; }
            public boolean isBinary() { return true; }
            public String getCharset(VirtualFile file, byte[] content) { return null; }
        }, "");
        this.connectionId = connectionId;
        setWritable(false);
    }
}
