package io.github.lucasbp.datagriptoolkit.mysql;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MySqlAccountService {
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*");
    private final SqlExecutor executor;

    public MySqlAccountService(SqlExecutor executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    public record AccountEntry(MySqlAccount account, boolean locked) {
    }

    public record ServerInfo(String version, boolean rolesSupported, Boolean partialRevokes, boolean readOnly) {
    }

    public record Snapshot(MySqlAccount account, List<String> grants, List<MySqlAccount> roles,
                           List<MySqlAccount> defaultRoles, boolean rolesComplete, ServerInfo serverInfo) {
        public Snapshot {
            Objects.requireNonNull(account);
            grants = List.copyOf(grants);
            roles = List.copyOf(roles);
            defaultRoles = List.copyOf(defaultRoles);
            Objects.requireNonNull(serverInfo);
        }
    }

    public enum ActionType {
        GRANT_ROLE, REVOKE_ROLE, GRANT_PRIVILEGE, REVOKE_PRIVILEGE, ADD_DEFAULT_ROLE, REMOVE_DEFAULT_ROLE
    }

    public enum Privilege {
        SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW_VIEW, CREATE_VIEW
    }

    public record Action(ActionType type, MySqlAccount role, String schema, String table, Privilege privilege) {
        public Action {
            Objects.requireNonNull(type, "Tipo de ação obrigatório.");
        }

        public static Action grantRole(MySqlAccount role) {
            return new Action(ActionType.GRANT_ROLE, role, null, null, null);
        }

        public static Action revokeRole(MySqlAccount role) {
            return new Action(ActionType.REVOKE_ROLE, role, null, null, null);
        }

        public static Action addDefaultRole(MySqlAccount role) {
            return new Action(ActionType.ADD_DEFAULT_ROLE, role, null, null, null);
        }

        public static Action removeDefaultRole(MySqlAccount role) {
            return new Action(ActionType.REMOVE_DEFAULT_ROLE, role, null, null, null);
        }

        public static Action grantPrivilege(String schema, String table, Privilege privilege) {
            return new Action(ActionType.GRANT_PRIVILEGE, null, schema, table, privilege);
        }

        public static Action revokePrivilege(String schema, String table, Privilege privilege) {
            return new Action(ActionType.REVOKE_PRIVILEGE, null, schema, table, privilege);
        }
    }

    public static final class Plan {
        private final Snapshot snapshot;
        private final List<Action> actions;
        private final List<String> statements;

        private Plan(Snapshot snapshot, List<Action> actions, List<String> statements) {
            this.snapshot = snapshot;
            this.actions = List.copyOf(actions);
            this.statements = List.copyOf(statements);
        }

        public Snapshot snapshot() { return snapshot; }
        public List<Action> actions() { return actions; }
        public List<String> statements() { return statements; }
    }

    public record ApplyResult(int completedCount, String failedStage, String errorMessage, Snapshot refreshedSnapshot) {
        public boolean successful() { return failedStage.isEmpty(); }
    }

    public record CreationResult(SqlExecutor.QueryResult result, boolean created, boolean flushSucceeded, String errorMessage) {
        @Override
        public String toString() {
            return "CreationResult[created=" + created + ", flushSucceeded=" + flushSucceeded + ", result=redacted]";
        }
    }

    public CreationResult createAccount(MySqlAccount account, boolean role, boolean flush) {
        String sql = role ? MySqlAccessPlanner.createRole(account) : MySqlAccessPlanner.createUserWithRandomPassword(account);
        var empty = new SqlExecutor.QueryResult(List.of(), List.of());
        try {
            var info = serverInfo();
            if (info.readOnly()) return new CreationResult(empty, false, false, "Conexão ou servidor está em somente leitura.");
            if (role && !info.rolesSupported()) {
                return new CreationResult(empty, false, false, "Servidor não suporta roles MySQL.");
            }
            if (!role && !supportsRandomPassword(info.version())) {
                return new CreationResult(empty, false, false, "Senha gerada pelo servidor exige MySQL 8.0.18 ou superior.");
            }
            if (listAccounts().stream().anyMatch(existing -> sameIdentity(existing.account(), account))) {
                return new CreationResult(empty, false, false, "Conta já existe. Recarregue a lista para editá-la.");
            }
        } catch (Exception problem) {
            return new CreationResult(empty, false, false, safeError(problem, "Não foi possível verificar servidor e contas. Nenhum comando de criação foi enviado."));
        }
        SqlExecutor.QueryResult result = empty;
        try {
            if (role) executor.execute(sql);
            else result = executor.query(sql);
        } catch (Exception problem) {
            return new CreationResult(empty, false, false, safeError(problem, "Não foi possível confirmar a criação. Recarregue as contas antes de tentar novamente."));
        }
        if (result == null) result = empty;
        String warning = !role && result.rows().isEmpty()
                ? "Conta criada, mas o resultado da senha não foi retornado pelo driver." : "";
        if (flush) {
            try {
                executor.execute("FLUSH PRIVILEGES;");
            } catch (Exception problem) {
                String error = safeError(problem, "Conta criada, mas FLUSH PRIVILEGES falhou. Não repita a criação.");
                if (!warning.isEmpty()) error += " " + warning;
                return new CreationResult(result, true, false, error);
            }
        }
        return new CreationResult(result, true, flush, warning);
    }

    public ServerInfo serverInfo() throws Exception {
        var rows = executor.query("SELECT VERSION() AS version, @@read_only AS read_only").rows();
        if (rows.size() != 1 || rows.getFirst().size() < 2 || rows.getFirst().get(0) == null) {
            throw new IllegalStateException("Não foi possível identificar versão e estado de leitura do servidor.");
        }
        String version = rows.getFirst().getFirst();
        var match = VERSION.matcher(version);
        boolean roles = match.matches() && Integer.parseInt(match.group(1)) >= 8
                && !version.toLowerCase(Locale.ROOT).contains("mariadb");
        boolean readOnly = flag(rows.getFirst().get(1)) || executor.isReadOnly();
        Boolean partialRevokes = null;
        try {
            var setting = executor.query("SELECT @@partial_revokes AS partial_revokes").rows();
            if (setting.size() != 1 || setting.getFirst().isEmpty()) {
                throw new IllegalStateException("Estado de partial_revokes indisponível.");
            }
            partialRevokes = flag(setting.getFirst().getFirst());
        } catch (SQLException error) {
            if (error.getErrorCode() != 1193) throw error;
        }
        return new ServerInfo(version, roles, partialRevokes, readOnly);
    }

    public List<AccountEntry> listAccounts() throws Exception {
        var result = executor.query("SELECT User, Host, account_locked FROM mysql.user ORDER BY User, Host");
        var accounts = new ArrayList<AccountEntry>();
        for (var row : result.rows()) {
            if (row.size() < 3) throw new IllegalStateException("Resposta incompleta ao listar contas.");
            accounts.add(new AccountEntry(new MySqlAccount(row.get(0), row.get(1)), flag(row.get(2))));
        }
        return List.copyOf(accounts);
    }

    public Snapshot snapshot(MySqlAccount account) throws Exception {
        var info = serverInfo();
        var grantResult = executor.query(MySqlAccessPlanner.inspectAccount(account));
        var grants = new ArrayList<String>();
        for (var row : grantResult.rows()) {
            if (row.isEmpty() || row.getFirst() == null) throw new IllegalStateException("Resposta incompleta ao ler grants.");
            grants.add(row.getFirst());
        }
        if (grants.isEmpty()) throw new IllegalStateException("Nenhum grant retornado; estado da conta desconhecido.");
        var roles = List.<MySqlAccount>of();
        var defaults = List.<MySqlAccount>of();
        boolean complete = !info.rolesSupported();
        if (info.rolesSupported()) {
            try {
                String predicate = " WHERE TO_USER = " + text(account.name()) + " AND TO_HOST = " + text(account.host());
                roles = accounts(executor.query("SELECT FROM_USER, FROM_HOST FROM mysql.role_edges" + predicate + " ORDER BY FROM_USER, FROM_HOST"));
                defaults = accounts(executor.query("SELECT DEFAULT_ROLE_USER, DEFAULT_ROLE_HOST FROM mysql.default_roles WHERE USER = "
                        + text(account.name()) + " AND HOST = " + text(account.host()) + " ORDER BY DEFAULT_ROLE_USER, DEFAULT_ROLE_HOST"));
                complete = true;
            } catch (SQLException error) {
                if (!Set.of(1044, 1045, 1142, 1143, 1227).contains(error.getErrorCode())) throw error;
                roles = List.of();
                defaults = List.of();
            }
        }
        return new Snapshot(account, grants, roles, defaults, complete, info);
    }

    public Plan prepare(Snapshot snapshot, List<Action> actions) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(actions);
        if (snapshot.serverInfo().readOnly()) throw new IllegalArgumentException("Conexão ou servidor está em somente leitura.");
        if (snapshot.grants().isEmpty()) throw new IllegalArgumentException("Leia os grants da conta antes de preparar alterações.");
        if (!snapshot.rolesComplete()) throw new IllegalArgumentException("Estado de roles incompleto. Recarregue com permissão para consultar roles e roles padrão.");
        if (actions.isEmpty()) throw new IllegalArgumentException("Selecione ao menos uma alteração.");
        var statements = compile(snapshot, actions);
        if (statements.isEmpty()) throw new IllegalArgumentException("Nenhuma alteração efetiva foi selecionada.");
        return new Plan(snapshot, actions, statements);
    }

    public ApplyResult apply(Plan plan) {
        return apply(plan, true);
    }

    public ApplyResult apply(Plan plan, boolean flushPrivileges) {
        Objects.requireNonNull(plan);
        int completed = 0;
        String failure = "";
        String error = "";
        Snapshot refreshed = null;
        try {
            refreshed = snapshot(plan.snapshot().account());
            if (refreshed.serverInfo().readOnly()) throw new IllegalStateException("Conexão em somente leitura.");
            if (!equivalent(plan.snapshot(), refreshed)) throw new IllegalStateException("Estado da conta mudou. Recarregue e revise o plano.");
            var checked = prepare(refreshed, plan.actions());
            if (!checked.statements().equals(plan.statements())) throw new IllegalStateException("Plano mudou. Gere nova revisão.");
        } catch (Exception problem) {
            return new ApplyResult(0, "preflight", "O estado da conta ou da conexão mudou, ficou incompleto ou não pôde ser verificado. Recarregue antes de aplicar.", refreshed);
        }
        try {
            for (var statement : plan.statements()) {
                executor.execute(statement);
                completed++;
            }
        } catch (Exception problem) {
            failure = "statement";
            error = safeError(problem, "Falha na alteração " + (completed + 1) + ". As anteriores podem ter sido aplicadas.");
        }
        if (completed > 0 && flushPrivileges) {
            try {
                executor.execute("FLUSH PRIVILEGES;");
            } catch (Exception problem) {
                if (failure.isEmpty()) {
                    failure = "flush";
                    error = safeError(problem, "As alterações foram executadas, mas FLUSH PRIVILEGES falhou.");
                } else {
                    error += " " + safeError(problem, "FLUSH PRIVILEGES também falhou.");
                }
            }
        }
        try {
            refreshed = snapshot(plan.snapshot().account());
        } catch (Exception problem) {
            refreshed = null;
            if (failure.isEmpty()) {
                failure = "refresh";
                error = safeError(problem, "Alterações executadas, mas não foi possível reler o estado da conta.");
            } else {
                error += " Não foi possível reler o estado da conta.";
            }
        }
        return new ApplyResult(completed, failure, error, refreshed);
    }

    private List<String> compile(Snapshot snapshot, List<Action> actions) {
        var sql = new ArrayList<String>();
        var assigned = new LinkedHashSet<>(snapshot.roles());
        var defaults = new LinkedHashSet<>(snapshot.defaultRoles());
        for (var action : actions) {
            Objects.requireNonNull(action);
            switch (action.type()) {
                case GRANT_ROLE, REVOKE_ROLE, ADD_DEFAULT_ROLE, REMOVE_DEFAULT_ROLE -> {
                    if (!snapshot.serverInfo().rolesSupported() || !snapshot.rolesComplete()) {
                        throw new IllegalArgumentException("Estado de roles indisponível. Recarregue com permissão de leitura das roles.");
                    }
                    Objects.requireNonNull(action.role(), "Role obrigatória.");
                    if (action.type() == ActionType.GRANT_ROLE) {
                        if (assigned.add(action.role())) sql.add(MySqlAccessPlanner.grantRole(action.role(), snapshot.account()));
                    } else if (action.type() == ActionType.REVOKE_ROLE) {
                        if (assigned.remove(action.role())) {
                            sql.add(MySqlAccessPlanner.revokeRole(action.role(), snapshot.account()));
                            defaults.remove(action.role());
                        }
                    } else if (action.type() == ActionType.ADD_DEFAULT_ROLE) {
                        if (!assigned.contains(action.role())) throw new IllegalArgumentException("Conceda a role antes de torná-la padrão.");
                        var statement = MySqlAccessPlanner.addDefaultRole(snapshot.account(), defaults, action.role(), true);
                        if (!statement.isEmpty()) sql.add(statement);
                        defaults.add(action.role());
                    } else if (defaults.remove(action.role())) {
                        String remaining = defaults.isEmpty() ? "NONE" : defaults.stream().map(MySqlAccount::sql).collect(Collectors.joining(", "));
                        sql.add("SET DEFAULT ROLE " + remaining + " TO " + snapshot.account().sql() + ";");
                    }
                }
                case GRANT_PRIVILEGE, REVOKE_PRIVILEGE -> {
                    Objects.requireNonNull(action.privilege(), "Privilégio obrigatório.");
                    String scope = scope(action.schema(), action.table(), snapshot.serverInfo().partialRevokes());
                    if (action.privilege() == Privilege.EXECUTE && action.table() != null) {
                        throw new IllegalArgumentException("EXECUTE deve ser concedido no schema; routines não são tabelas.");
                    }
                    String verb = action.type() == ActionType.GRANT_PRIVILEGE ? "GRANT " : "REVOKE ";
                    String target = action.type() == ActionType.GRANT_PRIVILEGE ? " TO " : " FROM ";
                    sql.add(verb + action.privilege().name().replace('_', ' ') + " ON " + scope + target + snapshot.account().sql() + ";");
                }
            }
        }
        return List.copyOf(sql);
    }

    private static String scope(String schema, String table, Boolean partialRevokes) {
        String schemaSql = MySqlNames.identifier(schema);
        if (table != null) return schemaSql + "." + MySqlNames.identifier(table);
        if (schema.indexOf('\\') >= 0) throw new IllegalArgumentException("Selecione tabela específica para schema com contrabarra.");
        if (schema.indexOf('%') >= 0 || schema.indexOf('_') >= 0) {
            if (partialRevokes == null) throw new IllegalArgumentException("Estado de partial_revokes desconhecido; selecione tabela específica.");
            if (!partialRevokes) schemaSql = MySqlNames.quote(schema.replace("_", "\\_").replace("%", "\\%"));
        }
        return schemaSql + ".*";
    }

    private static List<MySqlAccount> accounts(SqlExecutor.QueryResult result) {
        var accounts = new ArrayList<MySqlAccount>();
        for (var row : result.rows()) {
            if (row.size() < 2) throw new IllegalStateException("Resposta de roles incompleta.");
            accounts.add(new MySqlAccount(row.get(0), row.get(1)));
        }
        return List.copyOf(accounts);
    }

    private static boolean equivalent(Snapshot first, Snapshot second) {
        return first.account().equals(second.account()) && new TreeSet<>(first.grants()).equals(new TreeSet<>(second.grants()))
                && roleKeys(first.roles()).equals(roleKeys(second.roles()))
                && roleKeys(first.defaultRoles()).equals(roleKeys(second.defaultRoles()))
                && first.rolesComplete() == second.rolesComplete()
                && first.serverInfo().equals(second.serverInfo());
    }

    private static Set<String> roleKeys(List<MySqlAccount> roles) {
        return roles.stream().map(role -> role.name() + "\u0000" + role.host().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static String text(String value) {
        return "CONVERT(X'" + HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8)) + "' USING utf8mb4)";
    }

    private static boolean flag(String value) {
        if (value == null) throw new IllegalStateException("Estado booleano desconhecido.");
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "1", "Y", "YES", "ON", "TRUE" -> true;
            case "0", "N", "NO", "OFF", "FALSE" -> false;
            default -> throw new IllegalStateException("Estado booleano inválido.");
        };
    }

    private static String safeError(Exception problem, String message) {
        if (problem instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().matches("[0-9A-Z]{5}")) {
            return message + " SQLState: " + sql.getSQLState() + ".";
        }
        return message;
    }

    private static boolean supportsRandomPassword(String version) {
        if (version.toLowerCase(Locale.ROOT).contains("mariadb")) return false;
        var match = VERSION.matcher(version);
        if (!match.matches() || match.group(3) == null) return false;
        int major = Integer.parseInt(match.group(1));
        int minor = Integer.parseInt(match.group(2));
        int patch = Integer.parseInt(match.group(3));
        return major > 8 || major == 8 && (minor > 0 || patch >= 18);
    }

    private static boolean sameIdentity(MySqlAccount first, MySqlAccount second) {
        return first.name().equals(second.name()) && first.host().equalsIgnoreCase(second.host());
    }
}
