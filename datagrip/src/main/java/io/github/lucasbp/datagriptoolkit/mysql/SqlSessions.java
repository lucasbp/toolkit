package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.Objects;

public final class SqlSessions {
    private SqlSessions() {
    }

    @FunctionalInterface
    public interface Operation<T> {
        T run(SqlExecutor executor) throws Exception;
    }

    public record Completion<T>(T value, String cleanupWarning) {
        @Override
        public String toString() {
            return "Completion[value=redacted, cleanupWarning=" + cleanupWarning + "]";
        }
    }

    public static <T> Completion<T> run(SqlExecutor executor, Operation<T> operation) throws Exception {
        Objects.requireNonNull(executor, "A SQL session is required.");
        T value;
        try {
            value = Objects.requireNonNull(operation, "A SQL operation is required.").run(executor);
        } catch (Exception | Error original) {
            try {
                executor.close();
            } catch (Exception | Error cleanup) {
                if (original != cleanup) original.addSuppressed(cleanup);
            }
            throw original;
        }
        String warning = "";
        try {
            executor.close();
        } catch (Exception cleanup) {
            warning = "The operation completed but the session could not be closed. Do not repeat changes because of this warning.";
        }
        return new Completion<>(value, warning);
    }
}
