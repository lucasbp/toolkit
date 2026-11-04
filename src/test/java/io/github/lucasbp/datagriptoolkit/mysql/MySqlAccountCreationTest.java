package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlAccountCreationTest {
    private final MySqlAccount account = new MySqlAccount("new/area\\`name", "localhost");

    @Test
    void createsUserAndReturnsServerPasswordOnlyInMemoryBeforeFlush() {
        var executor = new CreationExecutor();
        var result = new MySqlAccountService(executor).createAccount(account, false, true);
        assertTrue(result.created());
        assertTrue(result.flushSucceeded());
        assertEquals("", result.errorMessage());
        assertEquals("server-generated-secret", result.result().rows().getFirst().get(2));
        assertFalse(result.toString().contains("server-generated-secret"));
        assertEquals(List.of(MySqlAccessPlanner.createUserWithRandomPassword(account), "FLUSH PRIVILEGES;"), executor.mutations);
    }

    @Test
    void roleCreationDoesNotRequestOrReturnPassword() {
        var executor = new CreationExecutor();
        var result = new MySqlAccountService(executor).createAccount(account, true, true);
        assertTrue(result.created());
        assertTrue(result.result().rows().isEmpty());
        assertEquals(List.of(MySqlAccessPlanner.createRole(account), "FLUSH PRIVILEGES;"), executor.mutations);
    }

    @Test
    void oldServerAndReadOnlyConnectionCannotCreateAccounts() {
        var old = new CreationExecutor();
        old.version = "8.0.17";
        assertFalse(new MySqlAccountService(old).createAccount(account, false, true).created());
        assertTrue(old.mutations.isEmpty());
        var readOnly = new CreationExecutor();
        readOnly.readOnly = true;
        assertFalse(new MySqlAccountService(readOnly).createAccount(account, false, true).created());
        assertTrue(readOnly.mutations.isEmpty());
        var noRoles = new CreationExecutor();
        noRoles.version = "5.7.44";
        assertFalse(new MySqlAccountService(noRoles).createAccount(account, true, true).created());
        assertTrue(noRoles.mutations.isEmpty());
    }

    @Test
    void existingIdentityUsesCaseSensitiveNameAndCaseInsensitiveHost() {
        var executor = new CreationExecutor();
        executor.existing.add(new MySqlAccount(account.name(), "LOCALHOST"));
        assertFalse(new MySqlAccountService(executor).createAccount(account, false, true).created());
        assertTrue(executor.mutations.isEmpty());
        executor.existing.clear();
        executor.existing.add(new MySqlAccount("NEW/area\\`name", "LOCALHOST"));
        assertTrue(new MySqlAccountService(executor).createAccount(account, false, false).created());
    }

    @Test
    void flushFailureDoesNotLoseConfirmedCreationOrPasswordAndNeverRetries() {
        var executor = new CreationExecutor();
        executor.failFlush = true;
        var result = new MySqlAccountService(executor).createAccount(account, false, true);
        assertTrue(result.created());
        assertFalse(result.flushSucceeded());
        assertTrue(result.errorMessage().contains("Não repita a criação"));
        assertFalse(result.errorMessage().contains("sensitive"));
        assertEquals("server-generated-secret", result.result().rows().getFirst().get(2));
        assertEquals(2, executor.mutations.size());
    }

    @Test
    void timeoutReportsUnconfirmedCreationWithoutAutomaticRetryOrFlush() {
        var executor = new CreationExecutor();
        executor.failCreate = true;
        var result = new MySqlAccountService(executor).createAccount(account, false, true);
        assertFalse(result.created());
        assertFalse(result.flushSucceeded());
        assertTrue(result.errorMessage().contains("Não foi possível confirmar"));
        assertTrue(result.errorMessage().contains("Recarregue"));
        assertFalse(result.errorMessage().contains("sensitive"));
        assertEquals(1, executor.mutations.size());
    }

    @Test
    void incompleteAccountListPreventsCreation() {
        var executor = new CreationExecutor();
        executor.denyList = true;
        var result = new MySqlAccountService(executor).createAccount(account, false, true);
        assertFalse(result.created());
        assertTrue(result.errorMessage().contains("Nenhum comando de criação foi enviado"));
        assertTrue(executor.mutations.isEmpty());
    }

    @Test
    void listsExistingAnonymousAccountsAndCreatesNamedAccountWithoutFlushWhenDisabled() throws Exception {
        var executor = new CreationExecutor();
        var anonymous = new MySqlAccount("", "localhost");
        executor.existing.add(anonymous);
        var service = new MySqlAccountService(executor);
        assertEquals(List.of(new MySqlAccountService.AccountEntry(anonymous, false)), service.listAccounts());
        var result = service.createAccount(account, false, false);
        assertTrue(result.created());
        assertFalse(result.flushSucceeded());
        assertEquals(List.of(MySqlAccessPlanner.createUserWithRandomPassword(account)), executor.mutations);
    }

    @Test
    void missingPasswordResultDoesNotPretendTheConfirmedAccountWasNotCreated() {
        var executor = new CreationExecutor();
        executor.emptyPasswordResult = true;
        var result = new MySqlAccountService(executor).createAccount(account, false, true);
        assertTrue(result.created());
        assertTrue(result.flushSucceeded());
        assertTrue(result.errorMessage().contains("resultado da senha não foi retornado"));
        assertEquals(2, executor.mutations.size());
    }

    private final class CreationExecutor implements SqlExecutor {
        String version = "8.0.40";
        boolean readOnly;
        boolean failFlush;
        boolean failCreate;
        boolean denyList;
        boolean emptyPasswordResult;
        final List<MySqlAccount> existing = new ArrayList<>();
        final List<String> mutations = new ArrayList<>();

        @Override
        public QueryResult query(String sql) throws Exception {
            if (sql.startsWith("SELECT VERSION()")) return new QueryResult(List.of(), List.of(List.of(version, "0")));
            if (sql.startsWith("SELECT @@partial_revokes")) return new QueryResult(List.of(), List.of(List.of("0")));
            if (sql.contains("FROM mysql.user")) {
                if (denyList) throw new SQLException("sensitive credential", "42000", 1142);
                return new QueryResult(List.of(), existing.stream().map(item -> List.of(item.name(), item.host(), "N")).toList());
            }
            if (sql.startsWith("CREATE USER")) {
                mutations.add(sql);
                if (failCreate) throw new TimeoutException("sensitive credential");
                return emptyPasswordResult ? new QueryResult(List.of(), List.of())
                        : new QueryResult(List.of("user", "host", "generated password"),
                        List.of(List.of(account.name(), account.host(), "server-generated-secret")));
            }
            throw new IllegalStateException("Unexpected query");
        }

        @Override
        public void execute(String sql) throws Exception {
            mutations.add(sql);
            if (sql.equals("FLUSH PRIVILEGES;") && failFlush) throw new SQLException("sensitive credential", "42000", 1227);
            if (sql.startsWith("CREATE ROLE") && failCreate) throw new TimeoutException("sensitive credential");
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }
    }
}
