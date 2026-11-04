package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlAccessPlannerTest {
    private final MySqlAccount user = new MySqlAccount("dev/exemplo", "%");
    private final MySqlAccount role = new MySqlAccount("leitura\\relatorios", "%");

    @Test
    void representsExistingAnonymousAccountsWithoutAllowingAnonymousCreation() {
        var anonymous = new MySqlAccount("", "localhost");
        assertEquals("``@`localhost`", anonymous.sql());
        assertEquals("SHOW GRANTS FOR ``@`localhost`;", MySqlAccessPlanner.inspectAccount(anonymous));
        assertThrows(IllegalArgumentException.class, () -> MySqlAccessPlanner.createUserWithRandomPassword(anonymous));
        assertThrows(IllegalArgumentException.class, () -> MySqlAccessPlanner.createRole(anonymous));
        assertThrows(NullPointerException.class, () -> new MySqlAccount(null, "localhost"));
    }

    @Test
    void preservesSlashAndBackslashWithoutImplicitRevokeOrDefaultChanges() {
        String sql = MySqlAccessPlanner.grantRole(role, user);
        assertEquals("GRANT `leitura\\relatorios`@`%` TO `dev/exemplo`@`%`;", sql);
        assertFalse(sql.contains("REVOKE"));
        assertFalse(sql.contains("DEFAULT"));
    }

    @Test
    void quotesAccountNameAndHostIndependently() {
        var account = new MySqlAccount("dev'`@x", "host`'\\");
        assertEquals("`dev'``@x`@`host``'\\`", account.sql());
    }

    @Test
    void revokeRequiresExplicitMethod() {
        assertEquals("REVOKE `leitura\\relatorios`@`%` FROM `dev/exemplo`@`%`;",
                MySqlAccessPlanner.revokeRole(role, user));
    }

    @Test
    void createsUserWithServerGeneratedPasswordAndPreservesAccountIdentity() {
        var account = new MySqlAccount("dev/area\\`name", "host`\\%");
        assertEquals("CREATE USER `dev/area\\``name`@`host``\\%` IDENTIFIED BY RANDOM PASSWORD;",
                MySqlAccessPlanner.createUserWithRandomPassword(account));
    }

    @Test
    void rejectsControlsAndNamesBeyondServerLimit() {
        assertThrows(IllegalArgumentException.class, () -> new MySqlAccount("a\u0000b", "%"));
        assertThrows(IllegalArgumentException.class, () -> new MySqlAccount("a".repeat(33), "%"));
        assertThrows(IllegalArgumentException.class, () -> new MySqlAccount("dev", "a".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> new MySqlAccount("dev", "host\nother"));
        assertThrows(IllegalArgumentException.class, () -> new MySqlAccount("dev\ud800", "%"));
    }

    @Test
    void preservesCompleteDefaultSnapshotAndDoesNotGrantUnrelatedRoles() {
        var existing = new MySqlAccount("existing/role", "%");
        assertEquals("SET DEFAULT ROLE `existing/role`@`%`, `leitura\\relatorios`@`%` TO `dev/exemplo`@`%`;",
                MySqlAccessPlanner.addDefaultRole(user, List.of(existing), role, true));
        assertEquals("", MySqlAccessPlanner.addDefaultRole(user, List.of(role), role, true));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlAccessPlanner.addDefaultRole(user, List.of(), role, false));
    }

    @Test
    void neverTreatsSchemaOrTableTextAsSql() {
        assertEquals("GRANT SELECT ON `schema``; GRANT ALL`.`table` TO `dev/exemplo`@`%`;",
                MySqlAccessPlanner.grantSelect(user, "schema`; GRANT ALL", "table"));
        assertEquals("GRANT SELECT ON `nuria_data`.`patient``; DROP USER x` TO `dev/exemplo`@`%`;",
                MySqlAccessPlanner.grantSelect(user, "nuria_data", "patient`; DROP USER x"));
    }

    @Test
    void rejectsSchemaWildcardAmbiguityWithoutServerCapabilities() {
        assertThrows(IllegalArgumentException.class, () -> MySqlAccessPlanner.grantSelect(user, "nuria_data", null));
        assertThrows(IllegalArgumentException.class, () -> MySqlAccessPlanner.grantSelect(user, "nuria%", null));
        assertThrows(IllegalArgumentException.class, () -> MySqlAccessPlanner.grantSelect(user, "nuria\\data", null));
        assertEquals("GRANT SELECT ON `nuria`.* TO `dev/exemplo`@`%`;",
                MySqlAccessPlanner.grantSelect(user, "nuria", null));
    }
}
