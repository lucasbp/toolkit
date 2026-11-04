package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.Objects;

public record MySqlAccount(String name, String host) {
    public MySqlAccount {
        Objects.requireNonNull(name, "Nome da conta obrigatório.");
        if (!name.isEmpty()) MySqlNames.validate(name, 32, "Nome da conta");
        MySqlNames.validate(host, 255, "Host");
    }

    public String sql() {
        return MySqlNames.quote(name) + "@" + MySqlNames.quote(host);
    }
}
