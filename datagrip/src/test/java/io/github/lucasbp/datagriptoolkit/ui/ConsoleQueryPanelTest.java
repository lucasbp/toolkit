package io.github.lucasbp.datagriptoolkit.ui;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleQueryPanelTest {
    private ConsoleQueryPanel panel() {
        return new ConsoleQueryPanel(List.of(new ConsoleQueryPanel.Table("demo", "people", List.of("id", "name"))), ignored -> {}, () -> {});
    }

    @Test void requiresAnExplicitTableAndDefaultsToOneThousandRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel();
            assertThrows(IllegalArgumentException.class, panel::sql);
            panel.tables.setSelectedIndex(0);
            assertEquals("SELECT *\nFROM `demo`.`people`\nLIMIT 1000;", panel.sql());
            panel.search.setText("missing");
            assertThrows(IllegalArgumentException.class, panel::sql);
        });
    }

    @Test void selectedColumnsTypedFilterAndOrderingStayInTheSameGeneratedQuery() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel();
            panel.tables.setSelectedIndex(0);
            panel.allColumns.setSelected(false);
            panel.columns.setSelectedIndex(1);
            panel.filterColumn.setSelectedItem("id");
            panel.filterType.setSelectedIndex(1);
            panel.filterValue.setText("42");
            panel.orderColumn.setSelectedItem("name");
            panel.descending.setSelected(true);
            assertEquals("SELECT `name`\nFROM `demo`.`people`\nWHERE `id` = 42\nORDER BY `name` DESC\nLIMIT 1000;", panel.sql());
        });
    }

    @Test void countingRespectsTheFilterWithoutLimitingTheCount() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel();
            panel.tables.setSelectedIndex(0);
            panel.filterColumn.setSelectedItem("id");
            panel.filterType.setSelectedIndex(2);
            panel.resultType.setSelectedIndex(1);
            assertEquals("SELECT COUNT(*)\nFROM `demo`.`people`\nWHERE `id` IS NULL;", panel.sql());
        });
    }
}
