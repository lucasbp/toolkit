package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import com.intellij.database.dataSource.DatabaseConnection;
import com.intellij.database.dataSource.DatabaseConnectionManager;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.dataSource.LocalDataSourceManager;
import com.intellij.database.remote.jdbc.RemoteResultSet;
import com.intellij.database.remote.jdbc.RemoteStatement;
import com.intellij.database.util.GuardedRef;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.SwingUtilities;

public final class DataGripSqlExecutor implements SqlExecutor {
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_ROWS = 10_000;
    private static final long MAX_RESULT_CHARACTERS = 16L * 1024 * 1024;
    private final Project project;
    private final LocalDataSource source;
    private GuardedRef<DatabaseConnection> connection;
    private volatile RemoteStatement activeStatement;
    private boolean closed;

    public DataGripSqlExecutor(Project project, LocalDataSource source) {
        this.project = Objects.requireNonNull(project);
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public synchronized QueryResult query(String sql) throws Exception {
        checkReady(sql);
        RemoteStatement statement = null;
        RemoteResultSet result = null;
        try {
            statement = createStatement();
            statement.setMaxRows(MAX_ROWS + 1);
            result = statement.executeQuery(sql);
            var metadata = result.getMetaData();
            int count = metadata.getColumnCount();
            if (count < 1 || count > 1024) {
                throw new ResultLimitException("O resultado tem uma quantidade de colunas incompatível com esta tela.");
            }
            List<String> columns = new ArrayList<>(count);
            for (int column = 1; column <= count; column++) {
                columns.add(metadata.getColumnLabel(column));
            }
            List<List<String>> rows = new ArrayList<>();
            long characters = 0;
            while (result.next()) {
                checkCanceled();
                if (rows.size() >= MAX_ROWS) {
                    throw new ResultLimitException("O resultado excedeu 10.000 linhas. Refine a consulta antes de continuar.");
                }
                List<String> row = new ArrayList<>(count);
                for (int column = 1; column <= count; column++) {
                    String value = result.getString(column);
                    characters += value == null ? 0 : value.length();
                    if (characters > MAX_RESULT_CHARACTERS) {
                        throw new ResultLimitException("O resultado excedeu o limite de dados desta tela. Refine a consulta.");
                    }
                    row.add(value);
                }
                rows.add(row);
            }
            return new QueryResult(columns, rows);
        } catch (ProcessCanceledException exception) {
            cancel();
            throw exception;
        } catch (InterruptedException exception) {
            cancel();
            Thread.currentThread().interrupt();
            throw exception;
        } catch (SQLException exception) {
            throw sanitized(exception);
        } catch (ResultLimitException exception) {
            throw exception;
        } catch (Exception exception) {
            throw connectionFailure();
        } finally {
            closeResult(result);
            closeStatement(statement);
            activeStatement = null;
        }
    }

    @Override
    public synchronized void execute(String sql) throws Exception {
        checkReady(sql);
        if (source.isReadOnly()) {
            throw new IllegalStateException("A conexão está configurada como somente leitura no DataGrip.");
        }
        RemoteStatement statement = null;
        try {
            statement = createStatement();
            if (statement.execute(sql)) {
                closeResult(statement.getResultSet());
            }
        } catch (ProcessCanceledException exception) {
            cancel();
            throw exception;
        } catch (InterruptedException exception) {
            cancel();
            Thread.currentThread().interrupt();
            throw exception;
        } catch (SQLException exception) {
            throw sanitized(exception);
        } catch (Exception exception) {
            throw connectionFailure();
        } finally {
            closeStatement(statement);
            activeStatement = null;
        }
    }

    public void cancel() {
        RemoteStatement statement = activeStatement;
        if (statement != null) {
            try {
                statement.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public synchronized boolean isReadOnly() throws Exception {
        try {
            return source.isReadOnly() || (connection != null && connection.get().isReadOnly());
        } catch (SQLException exception) {
            throw sanitized(exception);
        } catch (Exception exception) {
            throw connectionFailure();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (connection != null) {
            GuardedRef<DatabaseConnection> reference = connection;
            connection = null;
            try {
                reference.close();
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Não foi possível confirmar o encerramento da sessão dedicada no DataGrip.");
            }
        }
    }

    private RemoteStatement createStatement() throws Exception {
        checkCanceled();
        if (connection == null) {
            connection = DatabaseConnectionManager.getInstance()
                    .build(project, source)
                    .setAskPassword(true)
                    .createBlocking();
        }
        checkCanceled();
        RemoteStatement statement = connection.get().getRemoteConnection().createStatement();
        activeStatement = statement;
        try {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            return statement;
        } catch (Exception exception) {
            closeStatement(statement);
            activeStatement = null;
            throw exception;
        }
    }

    private void checkReady(String sql) throws InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("A operação SQL deve executar em segundo plano.");
        }
        if (closed || project.isDisposed()) {
            throw new IllegalStateException("A sessão de banco foi encerrada.");
        }
        if (!LocalDataSourceManager.getInstance(project).getDataSources().contains(source)) {
            throw new IllegalStateException("A conexão foi removida do DataGrip. Atualize a lista.");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Informe o comando SQL.");
        }
        checkCanceled();
    }

    private static void checkCanceled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("A espera foi interrompida. Confirme o estado no banco antes de repetir a operação.");
        }
        ProgressManager.checkCanceled();
    }

    private static SQLException sanitized(SQLException exception) {
        String state = exception.getSQLState();
        String safeState = state != null && state.matches("[A-Za-z0-9]{5}") ? state : "indisponível";
        return new SQLException("O banco recusou a operação. Código " + exception.getErrorCode()
                + ", SQLState " + safeState + ". Confira permissões e o estado atual antes de repetir.",
                state != null && state.matches("[A-Za-z0-9]{5}") ? state : null, exception.getErrorCode());
    }

    private static SQLException connectionFailure() {
        return new SQLException("A comunicação com a conexão do DataGrip falhou. Confira a conexão e o estado atual no banco antes de repetir a operação.");
    }

    private static final class ResultLimitException extends IllegalStateException {
        private ResultLimitException(String message) {
            super(message);
        }
    }

    private static void closeResult(RemoteResultSet result) {
        if (result != null) {
            try {
                result.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeStatement(RemoteStatement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (Exception ignored) {
            }
        }
    }
}
