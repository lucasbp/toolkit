package io.github.lucasbp.datagriptoolkit.mysql;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AccountIdentityService {
    private static final Set<String> PASSWORD_PLUGINS = Set.of("caching_sha2_password", "mysql_native_password", "sha256_password");
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*");
    private final SqlExecutor executor;
    private final MySqlAccountService accounts;

    public AccountIdentityService(SqlExecutor executor) {
        this.executor = Objects.requireNonNull(executor);
        accounts = new MySqlAccountService(executor);
    }

    public record Change(MySqlAccountService.Snapshot baseline, MySqlAccount newIdentity, Boolean locked, boolean resetPassword) {
        public Change {
            Objects.requireNonNull(baseline);
            Objects.requireNonNull(newIdentity);
        }
    }

    private enum Kind { RENAME, LOCK, PASSWORD }
    private record Step(Kind kind, String sql) { }

    public static final class Plan {
        private final Change change;
        private final boolean baselineLocked;
        private final String passwordPlugin;
        private final List<Step> steps;

        private Plan(Change change, boolean baselineLocked, String passwordPlugin, List<Step> steps) {
            this.change = change;
            this.baselineLocked = baselineLocked;
            this.passwordPlugin = passwordPlugin;
            this.steps = List.copyOf(steps);
        }

        public Change change() { return change; }
        public boolean baselineLocked() { return baselineLocked; }
        public String passwordPlugin() { return passwordPlugin; }
        public List<String> statements() { return steps.stream().map(Step::sql).toList(); }
    }

    public record Result(int completedCount, String failedStage, String errorMessage,
                         MySqlAccountService.Snapshot refreshedSnapshot, SqlExecutor.QueryResult passwordResult) {
        public boolean successful() { return failedStage.isEmpty(); }
        @Override
        public String toString() {
            return "Result[completedCount=" + completedCount + ", failedStage=" + failedStage + ", passwordResult=redacted]";
        }
    }

    public Plan prepare(MySqlAccountService.Snapshot baseline, MySqlAccount newIdentity, Boolean locked, boolean resetPassword) throws Exception {
        var change = new Change(baseline, newIdentity, locked, resetPassword);
        validateSnapshot(baseline);
        var info = accounts.serverInfo();
        if (info.readOnly() || !info.equals(baseline.serverInfo())) {
            throw new IllegalArgumentException("The connection changed or is read-only. Reload the account.");
        }
        if (newIdentity.name().isEmpty() && !baseline.account().name().isEmpty()) {
            throw new IllegalArgumentException("Enter the target account name. Anonymous accounts cannot be created here.");
        }
        var entries = accounts.listAccounts();
        var current = find(entries, baseline.account());
        if (current == null) throw new IllegalArgumentException("The original account is no longer listed. Reload users.");
        boolean rename = !sameIdentity(baseline.account(), newIdentity);
        if (rename && find(entries, newIdentity) != null) throw new IllegalArgumentException("The target account already exists.");
        String plugin = null;
        if (resetPassword) {
            if (!supportsRandomPassword(info.version()) || !info.rolesSupported()) {
                throw new IllegalArgumentException("Server-generated passwords require MySQL 8.0.18 or later.");
            }
            plugin = passwordPlugin(baseline.account());
        }
        var steps = new ArrayList<Step>();
        if (rename) steps.add(new Step(Kind.RENAME, "RENAME USER " + baseline.account().sql() + " TO " + newIdentity.sql() + ";"));
        if (locked != null && locked != current.locked()) {
            steps.add(new Step(Kind.LOCK, "ALTER USER " + newIdentity.sql() + " ACCOUNT " + (locked ? "LOCK" : "UNLOCK") + ";"));
        }
        if (resetPassword) {
            steps.add(new Step(Kind.PASSWORD, "ALTER USER " + newIdentity.sql() + " IDENTIFIED WITH "
                    + MySqlNames.identifier(plugin) + " BY RANDOM PASSWORD;"));
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("No identity, lock or password change was selected.");
        return new Plan(change, current.locked(), plugin, steps);
    }

    public Result apply(Plan plan, boolean flush) {
        Objects.requireNonNull(plan);
        var empty = new SqlExecutor.QueryResult(List.of(), List.of());
        MySqlAccountService.Snapshot refreshed = null;
        try {
            refreshed = accounts.snapshot(plan.change().baseline().account());
            validateSnapshot(refreshed);
            if (!equivalent(plan.change().baseline(), refreshed)) throw new IllegalArgumentException("Estado mudou.");
            var verified = prepare(refreshed, plan.change().newIdentity(), plan.change().locked(), plan.change().resetPassword());
            if (verified.baselineLocked() != plan.baselineLocked() || !Objects.equals(verified.passwordPlugin(), plan.passwordPlugin())
                    || !verified.steps.equals(plan.steps)) throw new IllegalArgumentException("Plano mudou.");
        } catch (Exception problem) {
            return new Result(0, "preflight", "Could not verify the original account, target, lock or authentication method. Reload and review before applying.", refreshed, empty);
        }
        int completed = 0;
        String failure = "";
        String error = "";
        var password = empty;
        var target = plan.change().baseline().account();
        for (var step : plan.steps) {
            try {
                if (step.kind() == Kind.PASSWORD) {
                    var result = executor.query(step.sql());
                    password = result == null ? empty : result;
                } else {
                    executor.execute(step.sql());
                }
                completed++;
                if (step.kind() == Kind.RENAME) target = plan.change().newIdentity();
            } catch (Exception problem) {
                failure = "statement";
                error = safeError(problem, "Could not confirm change " + (completed + 1)
                        + ". Earlier changes may have been applied. Reload the state; do not retry automatically.");
                break;
            }
        }
        if (completed > 0 && flush) {
            try {
                executor.execute("FLUSH PRIVILEGES;");
            } catch (Exception problem) {
                if (failure.isEmpty()) {
                    failure = "flush";
                    error = safeError(problem, "Changes were applied but FLUSH PRIVILEGES failed. Do not repeat the changes.");
                } else {
                    error += " " + safeError(problem, "FLUSH PRIVILEGES also failed.");
                }
            }
        }
        if (plan.change().resetPassword() && completed == plan.steps.size() && password.rows().isEmpty()) {
            String warning = "The password changed but the driver did not return the new password. Review the state before retrying.";
            if (failure.isEmpty()) failure = "password_result";
            error = error.isEmpty() ? warning : error + " " + warning;
        }
        try {
            if (failure.equals("statement") && !sameIdentity(target, plan.change().newIdentity())) {
                var entries = accounts.listAccounts();
                if (find(entries, target) == null && find(entries, plan.change().newIdentity()) != null) target = plan.change().newIdentity();
            }
            refreshed = accounts.snapshot(target);
        } catch (Exception problem) {
            refreshed = null;
            if (failure.isEmpty()) {
                failure = "refresh";
                error = safeError(problem, "Changes were applied but the account could not be reloaded. Reload before retrying.");
            } else {
                error += " Could not reload the account.";
            }
        }
        return new Result(completed, failure, error, refreshed, password);
    }

    private String passwordPlugin(MySqlAccount account) throws Exception {
        var rows = executor.query("SELECT plugin FROM mysql.user WHERE User = " + text(account.name())
                + " AND Host = " + text(account.host())).rows();
        if (rows.size() != 1 || rows.getFirst().isEmpty() || rows.getFirst().getFirst() == null) {
            throw new IllegalArgumentException("Could not read the current authentication method. Password reset is blocked.");
        }
        String plugin = rows.getFirst().getFirst().toLowerCase(Locale.ROOT);
        if (!PASSWORD_PLUGINS.contains(plugin)) {
            throw new IllegalArgumentException("The authentication method is external or unsupported. The password and authentication method will not be changed.");
        }
        return plugin;
    }

    private static void validateSnapshot(MySqlAccountService.Snapshot snapshot) {
        if (snapshot.serverInfo().readOnly()) throw new IllegalArgumentException("The connection or server is read-only.");
        if (snapshot.grants().isEmpty() || !snapshot.rolesComplete()) throw new IllegalArgumentException("Privilege information is incomplete. Reload before changing the account.");
    }

    private static MySqlAccountService.AccountEntry find(List<MySqlAccountService.AccountEntry> entries, MySqlAccount account) {
        return entries.stream().filter(entry -> sameIdentity(entry.account(), account)).findFirst().orElse(null);
    }

    private static boolean sameIdentity(MySqlAccount first, MySqlAccount second) {
        return first.name().equals(second.name()) && first.host().equalsIgnoreCase(second.host());
    }

    private static boolean equivalent(MySqlAccountService.Snapshot first, MySqlAccountService.Snapshot second) {
        return sameIdentity(first.account(), second.account()) && new TreeSet<>(first.grants()).equals(new TreeSet<>(second.grants()))
                && roleKeys(first.roles()).equals(roleKeys(second.roles())) && roleKeys(first.defaultRoles()).equals(roleKeys(second.defaultRoles()))
                && first.rolesComplete() == second.rolesComplete() && first.serverInfo().equals(second.serverInfo());
    }

    private static Set<String> roleKeys(List<MySqlAccount> roles) {
        return roles.stream().map(role -> role.name() + "\u0000" + role.host().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static boolean supportsRandomPassword(String version) {
        var match = VERSION.matcher(version);
        if (!match.matches() || version.toLowerCase(Locale.ROOT).contains("mariadb")) return false;
        int major = Integer.parseInt(match.group(1));
        int minor = Integer.parseInt(match.group(2));
        int patch = Integer.parseInt(match.group(3));
        return major > 8 || major == 8 && (minor > 0 || patch >= 18);
    }

    private static String text(String value) {
        return "CONVERT(X'" + HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8)) + "' USING utf8mb4)";
    }

    private static String safeError(Exception problem, String message) {
        if (problem instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().matches("[0-9A-Z]{5}")) {
            return message + " SQLState: " + sql.getSQLState() + ".";
        }
        return message;
    }
}
