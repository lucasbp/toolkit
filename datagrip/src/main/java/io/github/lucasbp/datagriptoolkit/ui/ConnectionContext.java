package io.github.lucasbp.datagriptoolkit.ui;

final class ConnectionContext {
    private ConnectionContext() {}

    static String resolve(String explorer, String console) {
        return explorer != null ? explorer : console;
    }
}
