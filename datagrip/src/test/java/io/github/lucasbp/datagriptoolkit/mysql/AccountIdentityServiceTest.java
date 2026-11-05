package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountIdentityServiceTest {
    private final MySqlAccount original = new MySqlAccount("dev/old\\name", "localhost");
    private final MySqlAccount renamed = new MySqlAccount("dev/new\\`name", "10.%");

    @Test
    void preparesRenameLockAndPasswordWithoutDirectTableWritesOrPluginMigration() throws Exception {
        var executor = new FakeExecutor();
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), renamed, true, true);
        assertEquals(List.of("RENAME USER " + original.sql() + " TO " + renamed.sql() + ";",
                "ALTER USER " + renamed.sql() + " ACCOUNT LOCK;",
                "ALTER USER " + renamed.sql() + " IDENTIFIED WITH `caching_sha2_password` BY RANDOM PASSWORD;"), plan.statements());
        assertFalse(plan.baselineLocked());
        assertEquals("caching_sha2_password", plan.passwordPlugin());
        assertFalse(plan.statements().stream().anyMatch(sql -> sql.startsWith("UPDATE ") || sql.startsWith("INSERT ") || sql.startsWith("DELETE ")));
    }

    @Test
    void appliesSequentiallyRefreshesRenamedIdentityAndRedactsPasswordInToString() throws Exception {
        var executor = new FakeExecutor();
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), renamed, true, true);
        var result = service.apply(plan, true);
        assertTrue(result.successful());
        assertEquals(3, result.completedCount());
        assertEquals(renamed, result.refreshedSnapshot().account());
        assertTrue(executor.locked);
        assertEquals(List.of(plan.statements().get(0), plan.statements().get(1), plan.statements().get(2), "FLUSH PRIVILEGES;"), executor.executed);
        assertEquals("server-generated-secret", result.passwordResult().rows().getFirst().get(2));
        assertFalse(result.toString().contains("server-generated-secret"));
    }

    @Test
    void refusesExistingTargetReadOnlyAndNoOpPlans() throws Exception {
        var executor = new FakeExecutor();
        var service = new AccountIdentityService(executor);
        executor.other = renamed;
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot(executor), renamed, null, false));
        executor.other = null;
        executor.readOnly = true;
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot(executor), renamed, null, false));
        executor.readOnly = false;
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot(executor), original, false, false));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot(executor), new MySqlAccount("", "%"), null, false));
        assertTrue(executor.executed.isEmpty());
    }

    @Test
    void deniesExternalAuthenticationPasswordChangesButAllowsNonPasswordEdits() throws Exception {
        var executor = new FakeExecutor();
        executor.plugin = "AWSAuthenticationPlugin";
        var service = new AccountIdentityService(executor);
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot(executor), original, null, true));
        var rename = service.prepare(snapshot(executor), renamed, null, false);
        assertEquals(1, rename.statements().size());
        assertFalse(rename.statements().getFirst().contains("IDENTIFIED"));
        assertTrue(executor.executed.isEmpty());
    }

    @Test
    void preservesEachSupportedAuthenticationPlugin() throws Exception {
        for (String plugin : List.of("caching_sha2_password", "mysql_native_password", "sha256_password")) {
            var executor = new FakeExecutor();
            executor.plugin = plugin;
            var plan = new AccountIdentityService(executor).prepare(snapshot(executor), original, null, true);
            assertEquals(List.of("ALTER USER " + original.sql() + " IDENTIFIED WITH `" + plugin + "` BY RANDOM PASSWORD;"), plan.statements());
        }
    }

    @Test
    void missingPluginPermissionBlocksOnlyPasswordReset() throws Exception {
        var executor = new FakeExecutor();
        executor.denyPlugin = true;
        var service = new AccountIdentityService(executor);
        assertThrows(SQLException.class, () -> service.prepare(snapshot(executor), original, null, true));
        assertEquals(1, service.prepare(snapshot(executor), renamed, null, false).statements().size());
    }

    @Test
    void validatesMinimumRandomPasswordVersion() throws Exception {
        var executor = new FakeExecutor();
        executor.version = "8.0.17";
        var service = new AccountIdentityService(executor);
        assertThrows(IllegalArgumentException.class, () -> service.prepare(snapshot(executor), original, null, true));
        assertEquals(1, service.prepare(snapshot(executor), renamed, null, false).statements().size());
    }

    @Test
    void changedAuthenticationPluginOrLockInvalidatesReviewedPlan() throws Exception {
        var executor = new FakeExecutor();
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), original, true, true);
        executor.plugin = "mysql_native_password";
        assertEquals("preflight", service.apply(plan, true).failedStage());
        assertTrue(executor.executed.isEmpty());
        executor.plugin = "caching_sha2_password";
        executor.locked = true;
        assertEquals("preflight", service.apply(plan, true).failedStage());
        assertTrue(executor.executed.isEmpty());
    }

    @Test
    void changedPermissionsOrDefaultRolesInvalidatesReviewedPlan() throws Exception {
        var executor = new FakeExecutor();
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), renamed, null, false);
        executor.extraGrant = true;
        assertEquals("preflight", service.apply(plan, true).failedStage());
        executor.extraGrant = false;
        executor.defaults.add(new MySqlAccount("new-default", "%"));
        assertEquals("preflight", service.apply(plan, true).failedStage());
        assertTrue(executor.executed.isEmpty());
    }

    @Test
    void partialFailureFlushesCompletedRenameAndRefreshesNewAccountWithoutRetry() throws Exception {
        var executor = new FakeExecutor();
        executor.failMutation = 2;
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), renamed, true, true);
        var result = service.apply(plan, true);
        assertEquals(1, result.completedCount());
        assertEquals("statement", result.failedStage());
        assertEquals(renamed, result.refreshedSnapshot().account());
        assertEquals(List.of(plan.statements().get(0), plan.statements().get(1), "FLUSH PRIVILEGES;"), executor.executed);
        assertTrue(result.passwordResult().rows().isEmpty());
        assertFalse(result.errorMessage().contains("sensitive"));
    }

    @Test
    void firstFailureDoesNotFlushAndUnconfirmedRenameIsResolvedForRefreshOnly() throws Exception {
        var executor = new FakeExecutor();
        executor.failMutation = 1;
        executor.renameBeforeFailure = true;
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), renamed, null, false);
        var result = service.apply(plan, true);
        assertEquals(0, result.completedCount());
        assertEquals("statement", result.failedStage());
        assertEquals(List.of(plan.statements().getFirst()), executor.executed);
        assertEquals(renamed, result.refreshedSnapshot().account());
        assertTrue(result.errorMessage().contains("Could not confirm"));
    }

    @Test
    void flushFailureRetainsReturnedPasswordAndDoesNotRepeatReset() throws Exception {
        var executor = new FakeExecutor();
        executor.failFlush = true;
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), original, null, true);
        var result = service.apply(plan, true);
        assertEquals(1, result.completedCount());
        assertEquals("flush", result.failedStage());
        assertEquals("server-generated-secret", result.passwordResult().rows().getFirst().get(2));
        assertNotNull(result.refreshedSnapshot());
        assertEquals(2, executor.executed.size());
    }

    @Test
    void resetWithoutReturnedPasswordIsReportedWithoutPretendingNothingChanged() throws Exception {
        var executor = new FakeExecutor();
        executor.emptyPassword = true;
        var service = new AccountIdentityService(executor);
        var plan = service.prepare(snapshot(executor), original, null, true);
        var result = service.apply(plan, false);
        assertEquals(1, result.completedCount());
        assertEquals("password_result", result.failedStage());
        assertTrue(result.errorMessage().contains("The password changed"));
        assertEquals(1, executor.executed.size());
    }

    @Test
    void lockOnlyWorksWithoutPasswordPluginAndUnlockUsesAccountUnlock() throws Exception {
        var executor = new FakeExecutor();
        executor.locked = true;
        executor.denyPlugin = true;
        var plan = new AccountIdentityService(executor).prepare(snapshot(executor), original, false, false);
        assertEquals(List.of("ALTER USER " + original.sql() + " ACCOUNT UNLOCK;"), plan.statements());
    }

    private MySqlAccountService.Snapshot snapshot(FakeExecutor executor) throws Exception {
        return new MySqlAccountService(executor).snapshot(executor.current);
    }

    private final class FakeExecutor implements SqlExecutor {
        MySqlAccount current = original;
        MySqlAccount other;
        String version = "8.0.40";
        String plugin = "caching_sha2_password";
        boolean locked;
        boolean readOnly;
        boolean denyPlugin;
        boolean extraGrant;
        boolean failFlush;
        boolean emptyPassword;
        boolean renameBeforeFailure;
        int failMutation = -1;
        int mutationCount;
        final List<String> executed = new ArrayList<>();
        final List<MySqlAccount> defaults = new ArrayList<>();

        @Override
        public QueryResult query(String sql) throws Exception {
            if (sql.startsWith("SELECT VERSION()")) return result(List.of(List.of(version, "0")));
            if (sql.startsWith("SELECT @@partial_revokes")) return result(List.of(List.of("0")));
            if (sql.startsWith("SELECT plugin")) {
                if (denyPlugin) throw new SQLException("sensitive credentials", "42000", 1142);
                return result(List.of(List.of(plugin)));
            }
            if (sql.contains("FROM mysql.user")) {
                var rows = new ArrayList<List<String>>();
                rows.add(List.of(current.name(), current.host(), locked ? "Y" : "N"));
                if (other != null) rows.add(List.of(other.name(), other.host(), "N"));
                return result(rows);
            }
            if (sql.startsWith("SHOW GRANTS")) {
                if (!sql.equals(MySqlAccessPlanner.inspectAccount(current))) throw new SQLException("Unknown account", "42000", 1141);
                var rows = new ArrayList<List<String>>();
                rows.add(List.of("GRANT USAGE ON *.* TO " + current.sql()));
                if (extraGrant) rows.add(List.of("GRANT SELECT ON `other`.* TO " + current.sql()));
                return result(rows);
            }
            if (sql.contains("FROM mysql.role_edges")) return result(List.of());
            if (sql.contains("FROM mysql.default_roles")) return result(defaults.stream().map(role -> List.of(role.name(), role.host())).toList());
            if (sql.startsWith("ALTER USER")) {
                mutation(sql);
                return emptyPassword ? result(List.of())
                        : new QueryResult(List.of("user", "host", "generated password"), List.of(List.of(current.name(), current.host(), "server-generated-secret")));
            }
            throw new IllegalStateException("Unexpected query");
        }

        @Override
        public void execute(String sql) throws Exception {
            if (sql.equals("FLUSH PRIVILEGES;")) {
                executed.add(sql);
                if (failFlush) throw new SQLException("sensitive credentials", "42000", 1227);
                return;
            }
            mutation(sql);
            if (sql.startsWith("RENAME USER")) current = renamed;
            if (sql.endsWith(" ACCOUNT LOCK;")) locked = true;
            if (sql.endsWith(" ACCOUNT UNLOCK;")) locked = false;
        }

        @Override
        public boolean isReadOnly() { return readOnly; }

        private void mutation(String sql) throws Exception {
            executed.add(sql);
            mutationCount++;
            if (mutationCount == failMutation) {
                if (renameBeforeFailure && sql.startsWith("RENAME USER")) current = renamed;
                throw new TimeoutException("sensitive credentials");
            }
        }

        private QueryResult result(List<List<String>> rows) { return new QueryResult(List.of(), rows); }
    }
}
