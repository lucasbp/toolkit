package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.AccountIdentityService;
import io.github.lucasbp.datagriptoolkit.mysql.MySqlAccount;
import io.github.lucasbp.datagriptoolkit.mysql.MySqlAccountService;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AccountIdentityReviewRegressionTest {
    @Test
    void reviewingAnExistingIdentityPlanKeepsItApplicableInsteadOfReplacingItWithAnEmptyPrivilegePlan() throws Exception {
        var old = new MySqlAccount("old/user", "%");
        var target = new MySqlAccount("new/user", "%");
        var executor = new ReviewExecutor(old);
        var baseline = new MySqlAccountService.Snapshot(old, List.of("GRANT USAGE ON *.* TO " + old.sql()),
                List.of(), List.of(), true, new MySqlAccountService.ServerInfo("8.0.40", true, false, false));
        var plan = new AccountIdentityService(executor).prepare(baseline, target, null, false);
        var panel = onEdt(() -> new AccountAdminPanel(new AccountAdminPanel.Backend() {
            @Override public List<AccountAdminPanel.ConnectionChoice> connections() {
                return List.of(new AccountAdminPanel.ConnectionChoice("test", "Test connection"));
            }
            @Override public SqlExecutor open(AccountAdminPanel.ConnectionChoice choice) { return executor; }
        }));
        try {
            onEdt(() -> {
                ((JComboBox<?>) field(panel, "connections")).setSelectedIndex(0);
                var showSnapshot = AccountAdminPanel.class.getDeclaredMethod("showSnapshot", MySqlAccountService.Snapshot.class);
                showSnapshot.setAccessible(true);
                showSnapshot.invoke(panel, baseline);
                setField(panel, "identityPlan", plan);
                ((JTextArea) field(panel, "preview")).setText(String.join("\n", plan.statements()));
                button(panel, "Aplicar alterações").setEnabled(true);
                button(panel, "Revisar alterações").doClick(0);
                return null;
            });
            awaitIdle(panel);
            onEdt(() -> {
                assertNotNull(field(panel, "identityPlan"), "Revisar deve manter o plano de edição de usuário");
                assertTrue(button(panel, "Aplicar alterações").isEnabled());
                assertTrue(((JTextArea) field(panel, "preview")).getText().contains("RENAME USER " + old.sql() + " TO " + target.sql()));
                return null;
            });
            assertTrue(executor.mutations.isEmpty());
        } finally {
            onEdt(() -> { panel.dispose(); return null; });
        }
    }

    private static void awaitIdle(AccountAdminPanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onEdt(() -> field(panel, "worker") == null)) return;
            Thread.sleep(10);
        }
        fail("Review worker did not finish");
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static JButton button(Container parent, String text) {
        for (var component : parent.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container container) {
                var found = button(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var result = new AtomicReference<T>();
        var problem = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(action.call()); } catch (Throwable error) { problem.set(error); }
        });
        if (problem.get() instanceof Exception error) throw error;
        if (problem.get() instanceof Error error) throw error;
        return result.get();
    }

    private static final class ReviewExecutor implements SqlExecutor {
        final MySqlAccount original;
        final List<String> mutations = new ArrayList<>();

        ReviewExecutor(MySqlAccount original) { this.original = original; }

        @Override
        public QueryResult query(String sql) {
            if (sql.startsWith("SELECT VERSION()")) return result(List.of(List.of("8.0.40", "0")));
            if (sql.startsWith("SELECT @@partial_revokes")) return result(List.of(List.of("0")));
            if (sql.contains("FROM mysql.user")) return result(List.of(List.of(original.name(), original.host(), "N")));
            throw new IllegalStateException("Unexpected query");
        }

        @Override public void execute(String sql) { mutations.add(sql); }
        private QueryResult result(List<List<String>> rows) { return new QueryResult(List.of(), rows); }
    }
}
