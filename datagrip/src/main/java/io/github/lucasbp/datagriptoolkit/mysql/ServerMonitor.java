package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.ArrayList;
import java.util.List;

public final class ServerMonitor {
    private ServerMonitor() {}

    public static SqlExecutor.QueryResult overview(SqlExecutor executor) throws Exception {
        var server = executor.query("SELECT @@hostname AS server_hostname, @@port AS server_port, VERSION() AS version, @@version_comment AS edition, DATABASE() AS current_schema, CURRENT_USER() AS authenticated_account, CONNECTION_ID() AS connection_id, @@read_only AS read_only, @@max_connections AS max_connections");
        if (server.rows().size() != 1 || server.rows().getFirst().size() != server.columns().size()) throw new IllegalStateException("The server returned incomplete information.");
        var rows = new ArrayList<List<String>>();
        for (int i = 0; i < server.columns().size(); i++) {
            rows.add(java.util.Arrays.asList(server.columns().get(i).replace('_', ' '), server.rows().getFirst().get(i)));
        }
        return new SqlExecutor.QueryResult(List.of("Property", "Value"), rows);
    }

    public static SqlExecutor.QueryResult processes(SqlExecutor executor) throws Exception {
        return executor.query("SHOW FULL PROCESSLIST");
    }
}
