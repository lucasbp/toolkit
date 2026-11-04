package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.MySqlAccount;
import io.github.lucasbp.datagriptoolkit.mysql.MySqlAccountService;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AccountAdminPanelTest {
    private final MySqlAccount user = new MySqlAccount("dev/exemplo", "%");
    private final MySqlAccount role = new MySqlAccount("leitura/relatorios", "%");

    @Test
    void loadsAccountReviewsRoleThenAppliesAndFlushes() throws Exception {
        var backend = new FakeBackend();
        var panel = panel(backend);
        try {
            loadUser(panel);
            onEdt(() -> {
                table(panel).setValueAt(true, 0, 1);
                click(panel, "Revisar alterações");
                return null;
            });
            awaitIdle(panel);
            onEdt(() -> {
                assertTrue(button(panel, "Aplicar alterações").isEnabled());
                assertTrue(field(panel, "preview", JTextArea.class).getText().contains("GRANT " + role.sql() + " TO " + user.sql()));
                click(panel, "Aplicar alterações");
                return null;
            });
            awaitIdle(panel);
            assertEquals(List.of("GRANT " + role.sql() + " TO " + user.sql() + ";", "FLUSH PRIVILEGES;"), backend.executed);
            onEdt(() -> {
                assertTrue(Boolean.TRUE.equals(table(panel).getValueAt(0, 1)));
                assertFalse(button(panel, "Aplicar alterações").isEnabled());
                assertTrue(field(panel, "status", javax.swing.JTextArea.class).getText().contains("FLUSH concluído"));
                return null;
            });
        } finally {
            onEdt(() -> { panel.dispose(); return null; });
        }
    }

    @Test
    void changingConnectionClearsReviewedSqlAndSelectedAccount() throws Exception {
        var backend = new FakeBackend();
        var panel = panel(backend);
        try {
            loadUser(panel);
            onEdt(() -> {
                table(panel).setValueAt(true, 0, 1);
                click(panel, "Revisar alterações");
                return null;
            });
            awaitIdle(panel);
            onEdt(() -> {
                field(panel, "connections", JComboBox.class).setSelectedIndex(1);
                assertFalse(button(panel, "Aplicar alterações").isEnabled());
                assertEquals("", field(panel, "preview", JTextArea.class).getText());
                assertEquals(0, field(panel, "accounts", JList.class).getModel().getSize());
                return null;
            });
            assertTrue(backend.executed.isEmpty());
        } finally {
            onEdt(() -> { panel.dispose(); return null; });
        }
    }

    @Test
    void unknownRolesAndReadOnlySnapshotsKeepApplyBlocked() throws Exception {
        for (boolean readOnly : List.of(true, false)) {
            var backend = new FakeBackend();
            backend.readOnly = readOnly;
            backend.denyRoles = !readOnly;
            var panel = panel(backend);
            try {
                loadUser(panel);
                onEdt(() -> {
                    assertFalse(table(panel).isCellEditable(0, 1));
                    assertFalse(button(panel, "Aplicar alterações").isEnabled());
                    assertTrue(field(panel, "status", javax.swing.JTextArea.class).getText().contains("bloqueada"));
                    return null;
                });
                assertTrue(backend.executed.isEmpty());
            } finally {
                onEdt(() -> { panel.dispose(); return null; });
            }
        }
    }

    @Test
    void permissionFormTargetsExplicitTableAndDoesNotRevokeExistingRole() throws Exception {
        var backend = new FakeBackend();
        var panel = panel(backend);
        try {
            loadUser(panel);
            onEdt(() -> {
                field(panel, "scope", JComboBox.class).setSelectedIndex(1);
                field(panel, "schemas", JComboBox.class).setSelectedItem("nuria_data");
                field(panel, "tables", JComboBox.class).setSelectedItem("users");
                click(panel, "Conceder permissão");
                click(panel, "Revisar alterações");
                return null;
            });
            awaitIdle(panel);
            onEdt(() -> {
                String sql = field(panel, "preview", JTextArea.class).getText();
                assertTrue(sql.contains("GRANT SELECT ON `nuria_data`.`users` TO " + user.sql()));
                assertFalse(sql.contains("REVOKE"));
                return null;
            });
        } finally {
            onEdt(() -> { panel.dispose(); return null; });
        }
    }

    @Test
    void rendersLoadedRoleEditorForVisualReview() throws Exception {
        var previousLookAndFeel = javax.swing.UIManager.getLookAndFeel();
        onEdt(() -> { javax.swing.UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel()); return null; });
        var panel = panel(new FakeBackend());
        try {
            loadUser(panel);
            onEdt(() -> {
                table(panel).setValueAt(true, 0, 1);
                descendants(panel, JTabbedPane.class).getFirst().setSelectedIndex(1);
                click(panel, "Revisar alterações");
                return null;
            });
            awaitIdle(panel);
            onEdt(() -> {
                panel.setSize(1180, 780);
                panel.addNotify();
                layout(panel);
                var image = new BufferedImage(1180, 780, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                panel.paint(graphics);
                graphics.dispose();
                Path output = Path.of("build", "preview", "admin.png");
                Files.createDirectories(output.getParent());
                assertTrue(ImageIO.write(image, "png", output.toFile()));
                descendants(panel, JTabbedPane.class).getFirst().setSelectedIndex(0);
                layout(panel);
                var permissionsImage = new BufferedImage(1180, 780, BufferedImage.TYPE_INT_ARGB);
                var permissionsGraphics = permissionsImage.createGraphics();
                panel.paint(permissionsGraphics);
                permissionsGraphics.dispose();
                assertTrue(ImageIO.write(permissionsImage, "png", output.resolveSibling("permissions.png").toFile()));
                panel.removeNotify();
                return null;
            });
        } finally {
            onEdt(() -> { panel.dispose(); javax.swing.UIManager.setLookAndFeel(previousLookAndFeel); return null; });
        }
    }

    private AccountAdminPanel panel(FakeBackend backend) throws Exception {
        return onEdt(() -> new AccountAdminPanel(backend));
    }

    private void loadUser(AccountAdminPanel panel) throws Exception {
        onEdt(() -> {
            field(panel, "connections", JComboBox.class).setSelectedIndex(0);
            click(panel, "Carregar usuários");
            return null;
        });
        awaitIdle(panel);
        onEdt(() -> {
            field(panel, "accounts", JList.class).setSelectedIndex(0);
            return null;
        });
        awaitIdle(panel);
    }

    private void awaitIdle(AccountAdminPanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (onEdt(() -> field(panel, "worker", javax.swing.SwingWorker.class) == null)) return;
            Thread.sleep(10);
        }
        fail("Panel operation did not finish within eight seconds");
    }

    private static JTable table(AccountAdminPanel panel) {
        try { return field(panel, "roles", JTable.class); }
        catch (Exception problem) { throw new AssertionError(problem); }
    }

    private static JButton button(AccountAdminPanel panel, String text) {
        return descendants(panel, JButton.class).stream().filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }

    private static void click(AccountAdminPanel panel, String text) {
        button(panel, text).doClick(0);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
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

    private static <T> List<T> descendants(Container parent, Class<T> type) {
        var result = new ArrayList<T>();
        for (var component : parent.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(descendants(child, type));
        }
        return result;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }

    private final class FakeBackend implements AccountAdminPanel.Backend {
        boolean readOnly;
        boolean denyRoles;
        final List<String> grants = new ArrayList<>(List.of("GRANT USAGE ON *.* TO " + user.sql()));
        final List<MySqlAccount> assigned = new ArrayList<>();
        final List<String> executed = new ArrayList<>();

        @Override
        public List<AccountAdminPanel.ConnectionChoice> connections() {
            return List.of(new AccountAdminPanel.ConnectionChoice("testing", "MySQL de homologação"),
                    new AccountAdminPanel.ConnectionChoice("production", "MySQL de produção"));
        }

        @Override
        public List<AccountAdminPanel.TableChoice> tables(AccountAdminPanel.ConnectionChoice connection) {
            return List.of(new AccountAdminPanel.TableChoice("nuria_data", "users"));
        }

        @Override
        public SqlExecutor open(AccountAdminPanel.ConnectionChoice connection) {
            return new SqlExecutor() {
                @Override
                public QueryResult query(String sql) throws Exception {
                    if (sql.startsWith("SELECT VERSION()")) return result(List.of(List.of("8.0.40", "0")));
                    if (sql.startsWith("SELECT @@partial_revokes")) return result(List.of(List.of("0")));
                    if (sql.contains("FROM mysql.user")) return result(List.of(List.of(user.name(), user.host(), "N"), List.of(role.name(), role.host(), "Y")));
                    if (sql.startsWith("SHOW GRANTS")) return result(grants.stream().map(List::of).toList());
                    if (sql.contains("FROM mysql.role_edges")) {
                        if (denyRoles) throw new SQLException("Permission denied", "42000", 1142);
                        return result(assigned.stream().map(account -> List.of(account.name(), account.host())).toList());
                    }
                    if (sql.contains("FROM mysql.default_roles")) return result(List.of());
                    throw new IllegalStateException("Unexpected query");
                }

                @Override
                public void execute(String sql) {
                    executed.add(sql);
                    if (sql.startsWith("GRANT " + role.sql())) {
                        assigned.add(role);
                        grants.add(sql);
                    }
                }

                @Override
                public boolean isReadOnly() {
                    return readOnly;
                }

                private QueryResult result(List<List<String>> rows) {
                    return new QueryResult(List.of(), rows);
                }
            };
        }
    }
}
