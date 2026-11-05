package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlAccountServiceTest {
    private final MySqlAccount account = new MySqlAccount("dev/area\\name", "%");
    private final MySqlAccount role = new MySqlAccount("leitura/role", "%");

    @Test
    void readsAccountIdentityAndFailsExplicitlyWhenUserTableIsForbidden() throws Exception {
        var executor = new FakeExecutor();
        var service = new MySqlAccountService(executor);
        assertEquals(List.of(new MySqlAccountService.AccountEntry(account, false)), service.listAccounts());
        executor.denyUsers = true;
        assertThrows(SQLException.class, service::listAccounts);
    }

    @Test
    void unknownRolesAreNotConfusedWithAnEmptyRoleSet() throws Exception {
        var executor = new FakeExecutor();
        executor.denyRoles = true;
        var service = new MySqlAccountService(executor);
        var snapshot = service.snapshot(account);
        assertFalse(snapshot.rolesComplete());
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot,
                List.of(MySqlAccountService.Action.grantRole(role))));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot,
                List.of(MySqlAccountService.Action.grantPrivilege("data", "table", MySqlAccountService.Privilege.SELECT))));
    }

    @Test
    void mysql57SupportsAccountsWithoutInventingRolesOrPartialRevokes() throws Exception {
        var executor = new FakeExecutor();
        executor.version = "5.7.44";
        executor.unknownPartialRevokes = true;
        var snapshot = new MySqlAccountService(executor).snapshot(account);
        assertFalse(snapshot.serverInfo().rolesSupported());
        assertNull(snapshot.serverInfo().partialRevokes());
        assertTrue(snapshot.rolesComplete());
        assertFalse(executor.queries.stream().anyMatch(sql -> sql.contains("mysql.role_edges")));
    }

    @Test
    void readOnlyServerOrConnectionCannotProduceAnApplyPlan() throws Exception {
        for (boolean serverReadOnly : List.of(true, false)) {
            var executor = new FakeExecutor();
            executor.serverReadOnly = serverReadOnly;
            executor.connectionReadOnly = !serverReadOnly;
            var service = new MySqlAccountService(executor);
            assertThrows(IllegalArgumentException.class, () -> service.prepare(service.snapshot(account),
                    List.of(MySqlAccountService.Action.grantRole(role))));
        }
    }

    @Test
    void refusesConcurrentGrantChangesBeforeAnyMutationOrFlush() throws Exception {
        var executor = new FakeExecutor();
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.grantRole(role)));
        executor.grants.add("GRANT SELECT ON `other`.* TO `dev`@`%`");
        var result = service.apply(plan);
        assertEquals("preflight", result.failedStage());
        assertEquals(0, result.completedCount());
        assertTrue(executor.executed.isEmpty());
        assertNotNull(result.refreshedSnapshot());
    }

    @Test
    void snapshotComparisonIgnoresGrantAndDefaultRoleOrdering() throws Exception {
        var executor = new FakeExecutor();
        executor.grants.add("GRANT SELECT ON `data`.`table` TO `dev`@`%`");
        var existing = new MySqlAccount("existing", "%");
        executor.roles.add(existing);
        executor.roles.add(role);
        executor.defaults.add(existing);
        executor.defaults.add(role);
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account),
                List.of(MySqlAccountService.Action.grantPrivilege("data", "other", MySqlAccountService.Privilege.SELECT)));
        java.util.Collections.reverse(executor.grants);
        java.util.Collections.reverse(executor.defaults);
        java.util.Collections.reverse(executor.roles);
        assertTrue(service.apply(plan).successful());
    }

    @Test
    void appliesSequentiallyFlushesLastAndRefreshes() throws Exception {
        var executor = new FakeExecutor();
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.grantRole(role),
                MySqlAccountService.Action.grantPrivilege("data", "table", MySqlAccountService.Privilege.SELECT)));
        var result = service.apply(plan);
        assertTrue(result.successful());
        assertEquals(2, result.completedCount());
        assertEquals(List.of(plan.statements().get(0), plan.statements().get(1), "FLUSH PRIVILEGES;"), executor.executed);
        assertNotNull(result.refreshedSnapshot());
        assertTrue(executor.snapshotReads >= 3);
    }

    @Test
    void reportsPartialFailureWithoutRetryOrImplicitRevocation() throws Exception {
        var executor = new FakeExecutor();
        executor.failMutation = 2;
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.grantRole(role),
                MySqlAccountService.Action.grantPrivilege("data", "table", MySqlAccountService.Privilege.SELECT),
                MySqlAccountService.Action.grantPrivilege("data", "other", MySqlAccountService.Privilege.SELECT)));
        var result = service.apply(plan);
        assertEquals(1, result.completedCount());
        assertEquals("statement", result.failedStage());
        assertEquals(List.of(plan.statements().get(0), plan.statements().get(1), "FLUSH PRIVILEGES;"), executor.executed);
        assertFalse(result.errorMessage().contains("secret"));
        assertTrue(result.errorMessage().contains("SQLState: 42000"));
        assertNotNull(result.refreshedSnapshot());
    }

    @Test
    void flushFailureStillRefreshesAndDoesNotRepeatMutation() throws Exception {
        var executor = new FakeExecutor();
        executor.failFlush = true;
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.grantRole(role)));
        var result = service.apply(plan);
        assertEquals(1, result.completedCount());
        assertEquals("flush", result.failedStage());
        assertEquals(List.of(plan.statements().getFirst(), "FLUSH PRIVILEGES;"), executor.executed);
        assertNotNull(result.refreshedSnapshot());
        assertTrue(executor.snapshotReads >= 3);
    }

    @Test
    void attemptsFlushAfterPartialApplyAndPreservesOriginalFailureStage() throws Exception {
        var executor = new FakeExecutor();
        executor.failMutation = 2;
        executor.failFlush = true;
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.grantRole(role),
                MySqlAccountService.Action.grantPrivilege("data", "table", MySqlAccountService.Privilege.SELECT)));
        var result = service.apply(plan);
        assertEquals("statement", result.failedStage());
        assertEquals(1, result.completedCount());
        assertTrue(result.errorMessage().contains("FLUSH PRIVILEGES also failed"));
        assertEquals("FLUSH PRIVILEGES;", executor.executed.getLast());
        assertNotNull(result.refreshedSnapshot());
    }

    @Test
    void firstMutationFailureDoesNotFlushAndNoOpPlanIsRejected() throws Exception {
        var executor = new FakeExecutor();
        executor.roles.add(role);
        var service = new MySqlAccountService(executor);
        var snapshot = service.snapshot(account);
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot, List.of(MySqlAccountService.Action.grantRole(role))));
        executor.failMutation = 1;
        var plan = service.prepare(snapshot, List.of(MySqlAccountService.Action.revokeRole(role)));
        var result = service.apply(plan);
        assertEquals(0, result.completedCount());
        assertEquals(1, executor.executed.size());
        assertFalse(executor.executed.contains("FLUSH PRIVILEGES;"));
    }

    @Test
    void removingDefaultPreservesOtherDefaultsWithoutRevokingRole() throws Exception {
        var executor = new FakeExecutor();
        var existing = new MySqlAccount("existing", "%");
        executor.roles.add(existing);
        executor.roles.add(role);
        executor.defaults.add(existing);
        executor.defaults.add(role);
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.removeDefaultRole(role)));
        assertEquals(List.of("SET DEFAULT ROLE `existing`@`%` TO " + account.sql() + ";"), plan.statements());
        var clear = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.removeDefaultRole(role),
                MySqlAccountService.Action.removeDefaultRole(existing)));
        assertEquals("SET DEFAULT ROLE NONE TO " + account.sql() + ";", clear.statements().getLast());
        assertFalse(clear.statements().stream().anyMatch(sql -> sql.contains("REVOKE")));
    }

    @Test
    void escapesDatabaseWildcardsAccordingToConfirmedServerSetting() throws Exception {
        var executor = new FakeExecutor();
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account),
                List.of(MySqlAccountService.Action.grantPrivilege("nuria_data", null, MySqlAccountService.Privilege.SELECT)));
        assertTrue(plan.statements().getFirst().contains("ON `nuria\\_data`.*"));
        executor.partialRevokes = true;
        var literal = service.prepare(service.snapshot(account),
                List.of(MySqlAccountService.Action.grantPrivilege("nuria_data", null, MySqlAccountService.Privilege.SELECT)));
        assertTrue(literal.statements().getFirst().contains("ON `nuria_data`.*"));
    }

    @Test
    void defaultChangesPreserveAllExistingDefaultsAndRequireGrantedRole() throws Exception {
        var executor = new FakeExecutor();
        var existing = new MySqlAccount("existing", "%");
        executor.roles.add(existing);
        executor.defaults.add(existing);
        var service = new MySqlAccountService(executor);
        var snapshot = service.snapshot(account);
        assertThrows(IllegalArgumentException.class,
                () -> service.prepare(snapshot, List.of(MySqlAccountService.Action.addDefaultRole(role))));
        var plan = service.prepare(snapshot, List.of(MySqlAccountService.Action.grantRole(role),
                MySqlAccountService.Action.addDefaultRole(role)));
        assertEquals(MySqlAccessPlanner.grantRole(role, account), plan.statements().getFirst());
        assertEquals("SET DEFAULT ROLE `existing`@`%`, `leitura/role`@`%` TO " + account.sql() + ";", plan.statements().get(1));
    }

    @Test
    void changingReadOnlyStateOrDefaultsInvalidatesAnExistingPlan() throws Exception {
        var executor = new FakeExecutor();
        executor.roles.add(role);
        var service = new MySqlAccountService(executor);
        var plan = service.prepare(service.snapshot(account), List.of(MySqlAccountService.Action.addDefaultRole(role)));
        executor.defaults.add(role);
        assertEquals("preflight", service.apply(plan).failedStage());
        assertTrue(executor.executed.isEmpty());
        executor.defaults.clear();
        executor.serverReadOnly = true;
        assertEquals("preflight", service.apply(plan).failedStage());
        assertTrue(executor.executed.isEmpty());
    }

    private final class FakeExecutor implements SqlExecutor {
        String version = "8.0.40";
        boolean partialRevokes;
        boolean unknownPartialRevokes;
        boolean denyUsers;
        boolean denyRoles;
        boolean serverReadOnly;
        boolean connectionReadOnly;
        boolean failFlush;
        int failMutation = -1;
        int snapshotReads;
        final List<String> grants = new ArrayList<>(List.of("GRANT USAGE ON *.* TO " + account.sql()));
        final List<MySqlAccount> roles = new ArrayList<>();
        final List<MySqlAccount> defaults = new ArrayList<>();
        final List<String> executed = new ArrayList<>();
        final List<String> queries = new ArrayList<>();

        @Override
        public QueryResult query(String sql) throws Exception {
            queries.add(sql);
            if (sql.startsWith("SELECT VERSION()")) return result(List.of(List.of(version, serverReadOnly ? "1" : "0")));
            if (sql.startsWith("SELECT @@partial_revokes")) {
                if (unknownPartialRevokes) throw new SQLException("Unknown system variable", "HY000", 1193);
                return result(List.of(List.of(partialRevokes ? "1" : "0")));
            }
            if (sql.contains("FROM mysql.user")) {
                if (denyUsers) throw denied();
                return result(List.of(List.of(account.name(), account.host(), "N")));
            }
            if (sql.startsWith("SHOW GRANTS")) {
                snapshotReads++;
                return result(grants.stream().map(List::of).toList());
            }
            if (sql.contains("mysql.role_edges")) {
                if (denyRoles) throw denied();
                return result(roles.stream().map(role -> List.of(role.name(), role.host())).toList());
            }
            if (sql.contains("mysql.default_roles")) {
                if (denyRoles) throw denied();
                return result(defaults.stream().map(role -> List.of(role.name(), role.host())).toList());
            }
            throw new IllegalStateException("Unexpected query");
        }

        @Override
        public void execute(String sql) throws Exception {
            executed.add(sql);
            if (sql.equals("FLUSH PRIVILEGES;")) {
                if (failFlush) throw denied();
                return;
            }
            if (executed.size() == failMutation) throw denied();
            grants.add(sql);
        }

        @Override
        public boolean isReadOnly() {
            return connectionReadOnly;
        }

        private SQLException denied() {
            return new SQLException("secret database credentials must never reach UI", "42000", 1142);
        }

        private QueryResult result(List<List<String>> rows) {
            return new QueryResult(List.of(), rows);
        }
    }
}
