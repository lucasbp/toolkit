package io.github.lucasbp.datagriptoolkit.ui;

final class ConnectionContext {
    private ConnectionContext() {}

    static String resolve(String explorer, String console) {
        if (explorer != null && console != null && !explorer.equals(console)) return null;
        return explorer != null ? explorer : console;
    }
}
