package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.Objects;

public final class MySqlNames {
    private MySqlNames() {
    }

    public static String identifier(String value) {
        validate(value, 64, "Identificador");
        if (value.endsWith(" ")) {
            throw new IllegalArgumentException("Identificador não pode terminar com espaço.");
        }
        return quote(value);
    }

    static String validate(String value, int maximum, String field) {
        Objects.requireNonNull(value, field + " obrigatório.");
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(field + " deve conter entre 1 e " + maximum + " caracteres.");
        }
        if (value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x7f || c > 0xffff || Character.isSurrogate((char) c))) {
            throw new IllegalArgumentException(field + " contém caracteres não suportados.");
        }
        return value;
    }

    static String quote(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}
