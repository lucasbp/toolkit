package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.Objects;

public record MySqlAccount(String name, String host) {
    public MySqlAccount {
        Objects.requireNonNull(name, "An account name is required.");
        if (!name.isEmpty()) MySqlNames.validate(name, 32, "Account name");
        MySqlNames.validate(host, 255, "Host");
    }

    public String sql() {
        return MySqlNames.quote(name) + "@" + MySqlNames.quote(host);
    }
}
