package io.github.lucasbp.datagriptoolkit.mysql;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MySqlSelectBuilder {
    private MySqlSelectBuilder() {
    }

    public record Filter(String column, Object value) {
        public Filter {
            MySqlNames.identifier(column);
            literal(value);
        }
    }

    public record Order(String column, boolean descending) {
        public Order {
            MySqlNames.identifier(column);
        }
    }

    public static String build(String schema, String table, List<String> columns, List<Filter> filters,
                               List<Order> orders, int limit) {
        Objects.requireNonNull(columns, "Columns are required.");
        Objects.requireNonNull(filters, "Filters are required.");
        Objects.requireNonNull(orders, "Ordering is required.");
        if (limit < 1 || limit > 2000) {
            throw new IllegalArgumentException("The limit must be between 1 and 2000.");
        }
        String columnSql = columns.isEmpty() || columns.equals(List.of("*")) ? "*"
                : columns.stream().map(MySqlNames::identifier).collect(Collectors.joining(", "));
        String sql = "SELECT " + columnSql + "\nFROM " + MySqlNames.identifier(schema) + "." + MySqlNames.identifier(table);
        if (!filters.isEmpty()) {
            sql += "\nWHERE " + filters.stream().map(filter -> MySqlNames.identifier(filter.column())
                    + (filter.value() == null ? " IS NULL" : " = " + literal(filter.value())))
                    .collect(Collectors.joining("\n  AND "));
        }
        if (!orders.isEmpty()) {
            sql += "\nORDER BY " + orders.stream().map(order -> MySqlNames.identifier(order.column())
                    + (order.descending() ? " DESC" : " ASC")).collect(Collectors.joining(", "));
        }
        return sql + "\nLIMIT " + limit + ";";
    }

    public static String count(String schema, String table, List<Filter> filters) {
        String rows = build(schema, table, List.of(), filters, List.of(), 1);
        return "SELECT COUNT(*)" + rows.substring("SELECT *".length(), rows.length() - "\nLIMIT 1;".length()) + ";";
    }

    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String text) {
            return "CONVERT(X'" + HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8)) + "' USING utf8mb4)";
        }
        if (value instanceof BigDecimal decimal) {
            return new BigDecimal(decimal.toString()).toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        throw new IllegalArgumentException("Filters accept only text, BigDecimal, boolean or null.");
    }
}
