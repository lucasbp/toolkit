package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface SqlExecutor extends AutoCloseable {
    record QueryResult(List<String> columns, List<List<String>> rows) {
        public QueryResult {
            columns = List.copyOf(columns);
            rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
        }
    }

    QueryResult query(String sql) throws Exception;

    void execute(String sql) throws Exception;

    default boolean isReadOnly() throws Exception {
        return false;
    }

    @Override
    default void close() throws Exception {
    }
}
