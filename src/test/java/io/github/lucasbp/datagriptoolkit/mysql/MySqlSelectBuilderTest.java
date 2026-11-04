package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSelectBuilderTest {
    @Test
    void rejectsTrailingSpacesRatherThanNormalizingIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> MySqlNames.identifier("schema "));
        assertThrows(IllegalArgumentException.class, () -> MySqlSelectBuilder.build("data", "users ", List.of(), List.of(), List.of(), 10));
        assertThrows(IllegalArgumentException.class, () -> new MySqlSelectBuilder.Filter("column ", "value"));
    }

    @Test
    void buildsTypedFiltersOrderingAndLimit() {
        assertEquals("SELECT `id`, `name`\nFROM `nuria_data`.`users`\nWHERE `amount` = 12.50\n  AND `active` = TRUE\n  AND `deleted_at` IS NULL\nORDER BY `id` DESC\nLIMIT 200;",
                MySqlSelectBuilder.build("nuria_data", "users", List.of("id", "name"),
                        List.of(new MySqlSelectBuilder.Filter("amount", new BigDecimal("12.50")),
                                new MySqlSelectBuilder.Filter("active", true), new MySqlSelectBuilder.Filter("deleted_at", null)),
                        List.of(new MySqlSelectBuilder.Order("id", true)), 200));
    }

    @Test
    void encodesUntrustedTextAsHexIndependentOfBackslashMode() {
        String value = "ação\\'; DROP USER admin; --\u0000";
        String sql = MySqlSelectBuilder.build("data", "users", List.of(),
                List.of(new MySqlSelectBuilder.Filter("name", value)), List.of(), 1);
        assertTrue(sql.contains("CONVERT(X'" + HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8)) + "' USING utf8mb4)"));
        assertFalse(sql.contains("DROP USER"));
        assertFalse(sql.contains("\\"));
    }

    @Test
    void rejectsUnsupportedFilterTypesAndUnboundedQueries() {
        assertThrows(IllegalArgumentException.class, () -> new MySqlSelectBuilder.Filter("id", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlSelectBuilder.build("data", "users", List.of(), List.of(), List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlSelectBuilder.build("data", "users", List.of(), List.of(), List.of(), 2001));
    }

    @Test
    void quotesEveryIdentifierAndDoesNotAllowColumnSqlExpressions() {
        String sql = MySqlSelectBuilder.build("data`; DROP DATABASE x", "users", List.of("id` FROM users; --"), List.of(), List.of(), 10);
        assertEquals("SELECT `id`` FROM users; --`\nFROM `data``; DROP DATABASE x`.`users`\nLIMIT 10;", sql);
    }

    @Test
    void handlesEmptyStringsAndScientificDecimalWithoutSqlFragments() {
        String sql = MySqlSelectBuilder.build("data", "users", List.of("*"),
                List.of(new MySqlSelectBuilder.Filter("name", ""), new MySqlSelectBuilder.Filter("amount", new BigDecimal("1E+3"))),
                List.of(new MySqlSelectBuilder.Order("id", false)), 2000);
        assertTrue(sql.contains("`name` = CONVERT(X'' USING utf8mb4)"));
        assertTrue(sql.contains("`amount` = 1000"));
        assertTrue(sql.endsWith("ORDER BY `id` ASC\nLIMIT 2000;"));
    }
}
