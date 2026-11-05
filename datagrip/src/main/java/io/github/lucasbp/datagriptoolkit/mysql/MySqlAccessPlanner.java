package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MySqlAccessPlanner {
    private MySqlAccessPlanner() {
    }

    public static String grantRole(MySqlAccount role, MySqlAccount user) {
        return "GRANT " + role.sql() + " TO " + user.sql() + ";";
    }

    public static String revokeRole(MySqlAccount role, MySqlAccount user) {
        return "REVOKE " + role.sql() + " FROM " + user.sql() + ";";
    }

    public static String createRole(MySqlAccount role) {
        if (role.name().isEmpty()) throw new IllegalArgumentException("Enter the new role name.");
        return "CREATE ROLE " + role.sql() + ";";
    }

    public static String createUserWithRandomPassword(MySqlAccount user) {
        if (user.name().isEmpty()) throw new IllegalArgumentException("Enter the new user name.");
        return "CREATE USER " + user.sql() + " IDENTIFIED BY RANDOM PASSWORD;";
    }

    public static String inspectAccount(MySqlAccount user) {
        return "SHOW GRANTS FOR " + user.sql() + ";";
    }

    public static String grantSelect(MySqlAccount user, String schema, String table) {
        String scope = MySqlNames.identifier(schema);
        if (table == null) {
            if (schema.indexOf('%') >= 0 || schema.indexOf('_') >= 0 || schema.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("For schemas containing %, _ or a backslash, select a specific table.");
            }
            scope += ".*";
        } else {
            scope += "." + MySqlNames.identifier(table);
        }
        return "GRANT SELECT ON " + scope + " TO " + user.sql() + ";";
    }

    public static String addDefaultRole(MySqlAccount user, Collection<MySqlAccount> existingDefaultRoles,
                                        MySqlAccount role, boolean snapshotComplete) {
        if (!snapshotComplete) {
            throw new IllegalArgumentException("Read all default roles before preparing this change.");
        }
        Objects.requireNonNull(existingDefaultRoles, "A default-role snapshot is required.");
        Objects.requireNonNull(role, "A role is required.");
        var roles = new LinkedHashSet<>(existingDefaultRoles);
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid default-role snapshot.");
        }
        if (!roles.add(role)) {
            return "";
        }
        return "SET DEFAULT ROLE " + roles.stream().map(MySqlAccount::sql).collect(Collectors.joining(", "))
                + " TO " + user.sql() + ";";
    }
}
