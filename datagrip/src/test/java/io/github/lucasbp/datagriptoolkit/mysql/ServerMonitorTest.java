package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerMonitorTest {
    @Test void overviewKeepsNullValuesAndUsesOnlyAReadStatement() throws Exception {
        var executor = new SqlExecutor() {
            public QueryResult query(String sql) {
                assertTrue(sql.startsWith("SELECT "));
                return new QueryResult(List.of("server_hostname", "current_schema"), List.of(java.util.Arrays.asList("fake-server", null)));
            }
            public void execute(String sql) { fail("Monitoring must not mutate the server"); }
        };
        var result = ServerMonitor.overview(executor);
        assertEquals(List.of("Property", "Value"), result.columns());
        assertNull(result.rows().get(1).get(1));
        assertEquals("server hostname", result.rows().getFirst().getFirst());
    }

    @Test void processListDoesNotAttemptKillOrRetryWithBroaderPrivileges() throws Exception {
        var calls = new java.util.ArrayList<String>();
        var executor = new SqlExecutor() {
            public QueryResult query(String sql) throws Exception {
                calls.add(sql);
                throw new java.sql.SQLException("denied");
            }
            public void execute(String sql) { fail("Monitoring must not mutate the server"); }
        };
        assertThrows(java.sql.SQLException.class, () -> ServerMonitor.processes(executor));
        assertEquals(List.of("SHOW FULL PROCESSLIST"), calls);
    }

    @Test void separatesGlobalScopeFromQuotedSchemaNamesAndRoleAssignments() {
        var entries = GrantSummary.summarize(List.of("GRANT USAGE ON *.* TO 'u'@'%'", "GRANT SELECT ON `*`.* TO 'u'@'%'", "GRANT 'r'@'%' TO 'u'@'%'"));
        assertTrue(entries.get(0).global());
        assertFalse(entries.get(1).global());
        assertFalse(entries.get(2).global());
    }
}
