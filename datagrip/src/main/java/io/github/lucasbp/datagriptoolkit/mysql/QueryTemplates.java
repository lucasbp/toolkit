package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.List;

public final class QueryTemplates {
    private QueryTemplates() {}

    public static String firstThousand(String schema, String table) {
        return MySqlSelectBuilder.build(schema, table, List.of(), List.of(), List.of(), 1000);
    }

    public static String listedColumns(String schema, String table, List<String> columns) {
        if (columns.isEmpty()) throw new IllegalArgumentException("Atualize os metadados das colunas no DataGrip.");
        return MySqlSelectBuilder.build(schema, table, columns, List.of(), List.of(), 1000);
    }

    public static String count(String schema, String table) {
        return "SELECT COUNT(*) AS total\nFROM " + MySqlNames.identifier(schema) + "." + MySqlNames.identifier(table) + ";";
    }
}
