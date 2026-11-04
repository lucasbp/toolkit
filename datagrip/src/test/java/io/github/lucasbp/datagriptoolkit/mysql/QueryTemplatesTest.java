package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryTemplatesTest {
    @Test
    void firstThousandIncludesLimitAndQuotesQualifiedTable() {
        assertEquals("SELECT *\nFROM `nuria_data`.`user/order`\nLIMIT 1000;",
                QueryTemplates.firstThousand("nuria_data", "user/order"));
    }

    @Test
    void listedColumnsUsesActualNamesWithoutSqlInterpolation() {
        assertEquals("SELECT `id`, `name``; DROP USER x`\nFROM `db``name`.`table\\name`\nLIMIT 1000;",
                QueryTemplates.listedColumns("db`name", "table\\name", List.of("id", "name`; DROP USER x")));
    }

    @Test
    void listedColumnsRequiresAvailableMetadata() {
        assertThrows(IllegalArgumentException.class, () -> QueryTemplates.listedColumns("data", "users", List.of()));
    }

    @Test
    void countQuotesBothPartsAndDoesNotAcceptSchemaSqlFragments() {
        assertEquals("SELECT COUNT(*) AS total\nFROM `data``; DROP DATABASE x`.`table``; --`;",
                QueryTemplates.count("data`; DROP DATABASE x", "table`; --"));
    }

    @Test
    void everyTemplateRejectsInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> QueryTemplates.firstThousand("data", "table "));
        assertThrows(IllegalArgumentException.class, () -> QueryTemplates.listedColumns("data", "users", List.of("id\u0000")));
        assertThrows(IllegalArgumentException.class, () -> QueryTemplates.count("data".repeat(17), "users"));
    }
}
