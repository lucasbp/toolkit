package io.github.lucasbp.datagriptoolkit.mysql;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSessionsTest {
    @Test
    void returnsConfirmedResultAndClosesExactlyOnce() throws Exception {
        var executor = new ClosingExecutor();
        var calls = new AtomicInteger();
        Object confirmed = new Object();
        var result = SqlSessions.run(executor, session -> {
            calls.incrementAndGet();
            assertSame(executor, session);
            assertEquals(0, executor.closeCount);
            return confirmed;
        });
        assertSame(confirmed, result.value());
        assertEquals("", result.cleanupWarning());
        assertEquals(1, calls.get());
        assertEquals(1, executor.closeCount);
    }

    @Test
    void cleanupFailurePreservesGeneratedPasswordAndNeverRetriesTheOperation() throws Exception {
        var executor = new ClosingExecutor();
        executor.closeFailure = new SQLException("sensitive connection credential", "08006", 0);
        var password = new SqlExecutor.QueryResult(List.of("generated password"), List.of(List.of("generated-secret")));
        var calls = new AtomicInteger();
        var result = SqlSessions.run(executor, session -> {
            calls.incrementAndGet();
            return password;
        });
        assertSame(password, result.value());
        assertEquals("generated-secret", result.value().rows().getFirst().getFirst());
        assertFalse(result.cleanupWarning().isEmpty());
        assertTrue(result.cleanupWarning().contains("Não repita"));
        assertFalse(result.cleanupWarning().contains("sensitive"));
        assertFalse(result.toString().contains("generated-secret"));
        assertEquals(1, executor.closeCount);
        assertEquals(1, calls.get());
    }

    @Test
    void operationFailureRemainsThePrimaryExceptionEvenWhenClosingAlsoFails() {
        var executor = new ClosingExecutor();
        var original = new SQLException("operation failure", "42000", 1142);
        executor.closeFailure = new SQLException("cleanup failure", "08006", 0);
        var calls = new AtomicInteger();
        var thrown = assertThrows(SQLException.class, () -> SqlSessions.run(executor, session -> {
            calls.incrementAndGet();
            throw original;
        }));
        assertSame(original, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(executor.closeFailure, thrown.getSuppressed()[0]);
        assertEquals(1, calls.get());
        assertEquals(1, executor.closeCount);
    }

    @Test
    void closeThrowingTheSameExceptionCannotReplaceOriginalWithSelfSuppressionError() {
        var executor = new ClosingExecutor();
        var original = new SQLException("shared failure", "08006", 0);
        executor.closeFailure = original;
        assertSame(original, assertThrows(SQLException.class, () -> SqlSessions.run(executor, session -> { throw original; })));
        assertEquals(0, original.getSuppressed().length);
        assertEquals(1, executor.closeCount);
    }

    @Test
    void fatalOperationErrorStillClosesAndRemainsPrimary() {
        var executor = new ClosingExecutor();
        var original = new AssertionError("operation failure");
        assertSame(original, assertThrows(AssertionError.class, () -> SqlSessions.run(executor, session -> { throw original; })));
        assertEquals(1, executor.closeCount);
    }

    @Test
    void missingOperationStillClosesTheProvidedSession() {
        var executor = new ClosingExecutor();
        assertThrows(NullPointerException.class, () -> SqlSessions.run(executor, null));
        assertEquals(1, executor.closeCount);
    }

    private static final class ClosingExecutor implements SqlExecutor {
        int closeCount;
        SQLException closeFailure;

        @Override
        public QueryResult query(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws SQLException {
            closeCount++;
            if (closeFailure != null) throw closeFailure;
        }
    }
}
