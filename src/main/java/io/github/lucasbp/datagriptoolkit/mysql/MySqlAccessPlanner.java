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
        if (role.name().isEmpty()) throw new IllegalArgumentException("Informe o nome da nova role.");
        return "CREATE ROLE " + role.sql() + ";";
    }

    public static String createUserWithRandomPassword(MySqlAccount user) {
        if (user.name().isEmpty()) throw new IllegalArgumentException("Informe o nome do novo usuário.");
        return "CREATE USER " + user.sql() + " IDENTIFIED BY RANDOM PASSWORD;";
    }

    public static String inspectAccount(MySqlAccount user) {
        return "SHOW GRANTS FOR " + user.sql() + ";";
    }

    public static String grantSelect(MySqlAccount user, String schema, String table) {
        String scope = MySqlNames.identifier(schema);
        if (table == null) {
            if (schema.indexOf('%') >= 0 || schema.indexOf('_') >= 0 || schema.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("Para schemas com %, _ ou contrabarra, selecione uma tabela específica.");
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
            throw new IllegalArgumentException("Leia todas as roles padrão antes de preparar esta alteração.");
        }
        Objects.requireNonNull(existingDefaultRoles, "Snapshot de roles padrão obrigatório.");
        Objects.requireNonNull(role, "Role obrigatória.");
        var roles = new LinkedHashSet<>(existingDefaultRoles);
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Snapshot de roles padrão inválido.");
        }
        if (!roles.add(role)) {
            return "";
        }
        return "SET DEFAULT ROLE " + roles.stream().map(MySqlAccount::sql).collect(Collectors.joining(", "))
                + " TO " + user.sql() + ";";
    }
}
