package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GrantSummaryTest {
    @Test
    void summarizesPrivilegesAndPreservesSql() {
        String sql = "  GRANT SELECT, INSERT, UPDATE ON `sales`.* TO `reader`@`%` WITH GRANT OPTION  ";
        GrantSummary.Entry entry = GrantSummary.summarize(List.of(sql)).getFirst();
        assertEquals("Permissão", entry.kind());
        assertEquals("`sales`.*", entry.scope());
        assertEquals("SELECT, INSERT, UPDATE", entry.privileges());
        assertEquals(sql, entry.sourceSql());
    }

    @Test
    void doesNotSplitOnKeywordsInsideBackticksOrColumnLists() {
        String sql = "GRANT SELECT (`column ON TO`, `other``TO`), UPDATE (`ON`) ON `schema ON main`.`table TO all` TO `user TO ON`@`%`";
        GrantSummary.Entry entry = GrantSummary.summarize(List.of(sql)).getFirst();
        assertEquals("Permissão", entry.kind());
        assertEquals("`schema ON main`.`table TO all`", entry.scope());
        assertEquals("SELECT (`column ON TO`, `other``TO`), UPDATE (`ON`)", entry.privileges());
        assertEquals(sql, entry.sourceSql());
    }

    @Test
    void summarizesRoleNamesWithSlashesBackslashesAndDoubledBackticks() {
        String roles = "`team/read ON`@`%`, `team\\TO\\`@`localhost`, `name`` TO ``role`@`%`";
        String sql = "GRANT " + roles + " TO `person`@`%`";
        GrantSummary.Entry entry = GrantSummary.summarize(List.of(sql)).getFirst();
        assertEquals("Roles", entry.kind());
        assertEquals("Conta", entry.scope());
        assertEquals(roles, entry.privileges());
        assertEquals(sql, entry.sourceSql());
    }

    @Test
    void handlesQuotedRoleNamesAndEscapedQuotes() {
        String roles = "'team\\' ON TO role'@'%', 'it''s TO ON'@'localhost', \"role TO ON\"@'%'";
        String sql = "grant " + roles + " to 'person'@'%' WITH ADMIN OPTION";
        GrantSummary.Entry entry = GrantSummary.summarize(List.of(sql)).getFirst();
        assertEquals("Roles", entry.kind());
        assertEquals(roles, entry.privileges());
        assertEquals(sql, entry.sourceSql());
    }

    @Test
    void preservesLiteralScopeWithBackslashes() {
        String sql = "GRANT SELECT ON `schema\\path/ON`.`table\\TO` TO `person`@`%`";
        GrantSummary.Entry entry = GrantSummary.summarize(List.of(sql)).getFirst();
        assertEquals("`schema\\path/ON`.`table\\TO`", entry.scope());
        assertEquals("SELECT", entry.privileges());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "REVOKE SELECT ON db.* FROM 'person'@'%'",
            "SET DEFAULT ROLE ALL TO 'person'@'%'",
            "GRANT SELECT ON db.*", "GRANT SELECT ON TO user", "GRANT TO user",
            "GRANT SELECT ON db.* TO", "GRANT SELECT ON db.* TO `unclosed",
            "GRANT SELECT (`column` ON db.* TO user",
            "GRANT SELECT ON db.* TO user; REVOKE SELECT ON db.* FROM user",
            "GRANT /* unknown */ SELECT ON db.* TO user", "unrecognized vendor format", ""
    })
    void preservesUnknownFormatsAsDetails(String sql) {
        GrantSummary.Entry entry = GrantSummary.summarize(List.of(sql)).getFirst();
        assertEquals("Detalhe", entry.kind());
        assertEquals("Texto original", entry.scope());
        assertEquals(sql, entry.privileges());
        assertEquals(sql, entry.sourceSql());
    }

    @Test
    void keepsOrderAndDuplicatesWithoutMutatingInput() {
        List<String> input = List.of("GRANT USAGE ON *.* TO `u`@`%`", "unknown", "unknown");
        List<GrantSummary.Entry> entries = GrantSummary.summarize(input);
        assertEquals(input, entries.stream().map(GrantSummary.Entry::sourceSql).toList());
        assertThrows(UnsupportedOperationException.class, () -> entries.clear());
        assertTrue(GrantSummary.summarize(List.of()).isEmpty());
    }
}
