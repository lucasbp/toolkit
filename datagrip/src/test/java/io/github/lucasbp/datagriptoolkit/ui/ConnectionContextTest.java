package io.github.lucasbp.datagriptoolkit.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionContextTest {
    @Test void opensTheConnectionSelectedOnlyInTheExplorer() {
        assertEquals("production", ConnectionContext.resolve("production", null));
    }

    @Test void opensTheConsoleConnectionWhenTheExplorerHasNoSelection() {
        assertEquals("testing", ConnectionContext.resolve(null, "testing"));
    }

    @Test void nativeExplorerSelectionTakesPriorityOverTheOpenConsole() {
        assertEquals("production", ConnectionContext.resolve("production", "testing"));
        assertEquals("production", ConnectionContext.resolve("production", "production"));
    }

    @Test void missingContextRequiresAChoiceInsteadOfUsingAnArbitraryConnection() {
        assertNull(ConnectionContext.resolve(null, null));
    }
}
