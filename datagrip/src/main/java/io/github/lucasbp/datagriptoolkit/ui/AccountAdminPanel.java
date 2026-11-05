package io.github.lucasbp.datagriptoolkit.ui;

import io.github.lucasbp.datagriptoolkit.mysql.MySqlAccount;
import io.github.lucasbp.datagriptoolkit.mysql.MySqlAccountService;
import io.github.lucasbp.datagriptoolkit.mysql.SqlExecutor;
import io.github.lucasbp.datagriptoolkit.mysql.GrantSummary;
import io.github.lucasbp.datagriptoolkit.mysql.AccountIdentityService;
import io.github.lucasbp.datagriptoolkit.mysql.SqlSessions;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public final class AccountAdminPanel extends JPanel {
    public record ConnectionChoice(String id, String label) { @Override public String toString() { return label; } }
    public record TableChoice(String schema, String name) { @Override public String toString() { return name; } }
    public interface Backend {
        List<ConnectionChoice> connections();
        SqlExecutor open(ConnectionChoice connection) throws Exception;
        default List<TableChoice> tables(ConnectionChoice connection) { return List.of(); }
    }

    private final Backend backend;
    private final String pinnedConnectionId;
    private final JComboBox<ConnectionChoice> connections = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final DefaultListModel<MySqlAccountService.AccountEntry> accountModel = new DefaultListModel<>();
    private final JList<MySqlAccountService.AccountEntry> accounts = new JList<>(accountModel);
    private final JLabel identity = new JLabel("Select a connection and load users");
    private final JLabel detail = new JLabel("User and host identify an account independently.");
    private final JTextArea status = new JTextArea("No connection selected.", 2, 40);
    private final JTextArea grants = textArea(8);
    private final JTable currentPermissions = new JTable();
    private final JTable globalPermissions = new JTable();
    private final JTextArea preview = textArea(6);
    private final JTable roles = new JTable();
    private final JComboBox<String> schemas = new JComboBox<>();
    private final JComboBox<String> tables = new JComboBox<>();
    private final JComboBox<String> scope = new JComboBox<>(new String[]{"Entire schema", "Specific table"});
    private final JComboBox<MySqlAccountService.Privilege> privilege = new JComboBox<>(MySqlAccountService.Privilege.values());
    private final DefaultListModel<MySqlAccountService.Action> changes = new DefaultListModel<>();
    private final JList<MySqlAccountService.Action> pending = new JList<>(changes);
    private final JCheckBox flush = new JCheckBox("Run FLUSH PRIVILEGES after applying changes", true);
    private final JButton apply = new JButton("Apply changes");
    private List<MySqlAccountService.AccountEntry> allAccounts = List.of();
    private List<TableChoice> metadata = List.of();
    private List<MySqlAccount> roleChoices = List.of();
    private MySqlAccountService.Snapshot snapshot;
    private MySqlAccountService.Plan plan;
    private AccountIdentityService.Plan identityPlan;
    private SwingWorker<?, ?> worker;
    private boolean updating;
    private boolean disposed;

    public AccountAdminPanel(Backend backend) {
        this(backend, null);
    }

    public AccountAdminPanel(Backend backend, String connectionId) {
        super(new BorderLayout(12, 12));
        this.backend = backend;
        this.pinnedConnectionId = connectionId;
        connections.setRenderer(Forms.literalRenderer());
        schemas.setRenderer(Forms.literalRenderer());
        tables.setRenderer(Forms.literalRenderer());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setFont(identity.getFont());
        var toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.add(connections, BorderLayout.CENTER);
        var tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        if (connectionId == null) button(tools, "Connections", this::refreshConnections);
        button(tools, "Load users", this::loadAccounts);
        button(tools, "New user / role", this::createAccount);
        toolbar.add(tools, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        var browser = new JPanel(new BorderLayout(0, 8));
        browser.setBorder(BorderFactory.createTitledBorder("Users & Accounts"));
        search.setToolTipText("Search by user or host");
        var searchPanel = new JPanel(new BorderLayout(0, 4));
        searchPanel.add(new JLabel("Search by user or host"), BorderLayout.NORTH);
        searchPanel.add(search, BorderLayout.CENTER);
        browser.add(searchPanel, BorderLayout.NORTH);
        accounts.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        accounts.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                putClientProperty("html.disable", Boolean.TRUE);
                if (value instanceof MySqlAccountService.AccountEntry entry) setText(display(entry.account()) + (entry.locked() ? "  [locked]" : ""));
                return this;
            }
        });
        browser.add(new JScrollPane(accounts), BorderLayout.CENTER);

        var editor = new JPanel(new BorderLayout(0, 12));
        var header = new JPanel(new GridLayout(2, 1, 0, 4));
        identity.setFont(identity.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        identity.putClientProperty("html.disable", Boolean.TRUE);
        detail.putClientProperty("html.disable", Boolean.TRUE);
        var identityRow = new JPanel(new BorderLayout(8, 0));
        identityRow.add(identity, BorderLayout.CENTER);
        var identityActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        button(identityActions, "Edit user", this::editAccount);
        identityRow.add(identityActions, BorderLayout.EAST);
        header.add(identityRow);
        header.add(detail);
        editor.add(header, BorderLayout.NORTH);
        var tabs = new JTabbedPane();
        tabs.addTab("Global Privileges", new JScrollPane(globalPermissions));
        tabs.addTab("Schema & Table Privileges", new JScrollPane(currentPermissions));
        tabs.addTab("Roles", new JScrollPane(roles));
        tabs.addTab("Edit Schema Privileges", permissionEditor());
        tabs.addTab("SQL Grants", new JScrollPane(grants));
        editor.add(tabs, BorderLayout.CENTER);
        var split = Forms.split(browser, editor, 240);
        add(split, BorderLayout.CENTER);

        var review = new JPanel(new BorderLayout(8, 8));
        review.setBorder(BorderFactory.createTitledBorder("Review & Apply"));
        review.add(new JScrollPane(preview), BorderLayout.CENTER);
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(flush);
        button(actions, "Discard changes", this::discard);
        button(actions, "Review changes", this::prepare);
        apply.addActionListener(event -> apply());
        apply.setEnabled(false);
        actions.add(apply);
        var footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.SOUTH);
        review.add(footer, BorderLayout.SOUTH);
        add(review, BorderLayout.SOUTH);

        connections.addActionListener(event -> clearAccountList());
        accounts.addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && !updating) loadSnapshot(); });
        search.getDocument().addDocumentListener(listener(this::filterAccounts));
        flush.addActionListener(event -> invalidatePlan());
        var renderer = new javax.swing.table.DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        roles.setDefaultRenderer(Object.class, renderer);
        currentPermissions.setDefaultRenderer(Object.class, renderer);
        globalPermissions.setDefaultRenderer(Object.class, renderer);
        refreshConnections();
        if (connectionId != null) {
            connections.setEnabled(false);
            if (connections.getItemCount() != 1) throw new IllegalArgumentException("The selected connection is no longer available.");
            connections.setSelectedIndex(0);
            javax.swing.SwingUtilities.invokeLater(() -> { if (!disposed) loadAccounts(); });
        }
    }

    private JPanel permissionEditor() {
        var panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var form = Forms.stack();
        schemas.setEditable(true);
        tables.setEditable(true);
        Forms.field(form, "Scope", scope);
        Forms.field(form, "Schema", schemas);
        Forms.field(form, "Table", tables);
        Forms.field(form, "Privilege", privilege);
        tables.setEnabled(false);
        scope.addActionListener(event -> tables.setEnabled(scope.getSelectedIndex() == 1));
        schemas.addActionListener(event -> fillTables());
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(buttons, "Grant privilege", () -> addPermission(false));
        button(buttons, "Revoke privilege", () -> addPermission(true));
        button(buttons, "Remove from review", () -> { if (pending.getSelectedIndex() >= 0) { changes.remove(pending.getSelectedIndex()); invalidatePlan(); } });
        form.add(buttons);
        panel.add(form, BorderLayout.NORTH);
        pending.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                putClientProperty("html.disable", Boolean.TRUE);
                if (value instanceof MySqlAccountService.Action action) setText((action.type() == MySqlAccountService.ActionType.GRANT_PRIVILEGE ? "Grant " : "Revoke ") + action.privilege() + " on " + action.schema() + "." + (action.table() == null ? "*" : action.table()));
                return this;
            }
        });
        panel.add(new JScrollPane(pending), BorderLayout.CENTER);
        return panel;
    }

    private void refreshConnections() {
        if (worker != null) return;
        connections.removeAllItems();
        for (var connection : backend.connections()) {
            if (pinnedConnectionId == null || pinnedConnectionId.equals(connection.id())) connections.addItem(connection);
        }
        connections.setSelectedIndex(-1);
    }

    private void clearAccountList() {
        updating = true;
        try { accountModel.clear(); } finally { updating = false; }
        allAccounts = List.of();
        metadata = List.of();
        schemas.removeAllItems();
        tables.removeAllItems();
        clearSnapshot();
    }

    private record AccountData(List<MySqlAccountService.AccountEntry> accounts, List<TableChoice> tables) {}

    private void loadAccounts() {
        var connection = connection();
        clearAccountList();
        start(() -> {
            try (var executor = backend.open(connection)) {
                var entries = new MySqlAccountService(executor).listAccounts();
                List<TableChoice> known;
                try { known = backend.tables(connection); } catch (RuntimeException ignored) { known = List.of(); }
                return new AccountData(entries, known);
            }
        }, result -> {
            allAccounts = result.accounts();
            metadata = result.tables();
            var schemaNames = new java.util.TreeSet<String>();
            metadata.forEach(table -> schemaNames.add(table.schema()));
            schemaNames.forEach(schemas::addItem);
            schemas.setSelectedIndex(-1);
            filterAccounts();
            status.setText(allAccounts.size() + " accounts loaded. Select an account to edit.");
        });
    }

    private void filterAccounts() {
        if (worker != null) return;
        updating = true;
        try {
            accountModel.clear();
            String filter = search.getText().toLowerCase(java.util.Locale.ROOT);
            for (var entry : allAccounts) if (display(entry.account()).toLowerCase(java.util.Locale.ROOT).contains(filter)) accountModel.addElement(entry);
        } finally { updating = false; }
        clearSnapshot();
    }

    private void loadSnapshot() {
        var entry = accounts.getSelectedValue();
        clearSnapshot();
        if (entry == null) return;
        var connection = connection();
        start(() -> {
            try (var executor = backend.open(connection)) { return new MySqlAccountService(executor).snapshot(entry.account()); }
        }, this::showSnapshot);
    }

    private void clearSnapshot() {
        snapshot = null;
        changes.clear();
        grants.setText("");
        currentPermissions.setModel(new DefaultTableModel());
        globalPermissions.setModel(new DefaultTableModel());
        roles.setModel(new DefaultTableModel());
        roleChoices = List.of();
        identity.setText("Select a user");
        detail.setText("Changes are prepared only for the selected account.");
        invalidatePlan();
    }

    private void showSnapshot(MySqlAccountService.Snapshot current) {
        snapshot = current;
        changes.clear();
        identity.setText(display(current.account()));
        detail.setText("Connection: " + connection().label() + "  |  MySQL " + current.serverInfo().version());
        grants.setText(String.join("\n\n", current.grants()));
        var permissionsModel = new DefaultTableModel(new String[]{"Type", "Schema / table", "Privileges"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        var globals = new DefaultTableModel(new String[]{"Scope", "Privileges"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (var permission : GrantSummary.summarize(current.grants())) {
            if (permission.global()) globals.addRow(new Object[]{permission.scope(), permission.privileges()});
            else if (permission.kind().equals("Privilege")) permissionsModel.addRow(new Object[]{permission.kind(), permission.scope(), permission.privileges()});
        }
        currentPermissions.setModel(permissionsModel);
        globalPermissions.setModel(globals);
        globalPermissions.setRowHeight(Math.max(28, globalPermissions.getRowHeight()));
        currentPermissions.setRowHeight(Math.max(28, currentPermissions.getRowHeight()));
        roles.setRowHeight(Math.max(28, roles.getRowHeight()));
        var candidates = new LinkedHashSet<MySqlAccount>();
        allAccounts.forEach(entry -> candidates.add(entry.account()));
        candidates.addAll(current.roles());
        candidates.remove(current.account());
        roleChoices = candidates.stream().sorted(Comparator.comparing(AccountAdminPanel::display)).toList();
        var model = new DefaultTableModel(new String[]{"Account / role", "Granted", "Default role"}, 0) {
            @Override public Class<?> getColumnClass(int column) { return column == 0 ? String.class : Boolean.class; }
            @Override public boolean isCellEditable(int row, int column) { return column > 0 && current.rolesComplete() && current.serverInfo().rolesSupported() && !current.serverInfo().readOnly(); }
        };
        for (var role : roleChoices) model.addRow(new Object[]{display(role), current.roles().contains(role), current.defaultRoles().contains(role)});
        model.addTableModelListener(event -> {
            if (updating) return;
            updating = true;
            try {
                int row = event.getFirstRow();
                if (row >= 0 && row < model.getRowCount()) {
                    if (event.getColumn() == 1 && !Boolean.TRUE.equals(model.getValueAt(row, 1))) model.setValueAt(false, row, 2);
                    if (event.getColumn() == 2 && Boolean.TRUE.equals(model.getValueAt(row, 2))) model.setValueAt(true, row, 1);
                }
            } finally { updating = false; }
            invalidatePlan();
        });
        roles.setModel(model);
        invalidatePlan();
        status.setText(current.serverInfo().readOnly() ? "The connection or server is read-only. Applying changes is blocked."
            : !current.rolesComplete() ? "Could not read all roles. Applying changes is blocked. Check read privileges."
            : "Account state loaded. Choose changes and review before applying.");
    }

    private void fillTables() {
        String schema = input(schemas);
        tables.removeAllItems();
        for (var item : metadata) if (item.schema().equals(schema)) tables.addItem(item.name());
        tables.setSelectedIndex(-1);
    }

    private void addPermission(boolean revoke) {
        if (snapshot == null) throw new IllegalArgumentException("Select and load a user.");
        String schema = input(schemas);
        String table = scope.getSelectedIndex() == 1 ? input(tables) : null;
        if (schema.isBlank() || table != null && table.isBlank()) throw new IllegalArgumentException("Choose a schema and, when needed, a specific table.");
        var selected = (MySqlAccountService.Privilege) privilege.getSelectedItem();
        var action = revoke ? MySqlAccountService.Action.revokePrivilege(schema, table, selected) : MySqlAccountService.Action.grantPrivilege(schema, table, selected);
        if (!java.util.Collections.list(changes.elements()).contains(action)) changes.addElement(action);
        invalidatePlan();
        status.setText("Change added to the review for " + display(snapshot.account()) + ".");
    }

    private List<MySqlAccountService.Action> actions() {
        var result = new ArrayList<MySqlAccountService.Action>();
        for (int i = 0; i < roleChoices.size(); i++) {
            var role = roleChoices.get(i);
            boolean assigned = Boolean.TRUE.equals(roles.getValueAt(i, 1));
            boolean byDefault = Boolean.TRUE.equals(roles.getValueAt(i, 2));
            if (!snapshot.roles().contains(role) && assigned) result.add(MySqlAccountService.Action.grantRole(role));
            if (snapshot.roles().contains(role) && !assigned) result.add(MySqlAccountService.Action.revokeRole(role));
            if (snapshot.defaultRoles().contains(role) && !byDefault && assigned) result.add(MySqlAccountService.Action.removeDefaultRole(role));
            if (!snapshot.defaultRoles().contains(role) && byDefault) result.add(MySqlAccountService.Action.addDefaultRole(role));
        }
        result.addAll(java.util.Collections.list(changes.elements()));
        return result;
    }

    private void prepare() {
        if (identityPlan != null) {
            showIdentityPlan(identityPlan, connection());
            return;
        }
        if (snapshot == null) throw new IllegalArgumentException("Load the account state before reviewing.");
        var connection = connection();
        var baseline = snapshot;
        var actions = actions();
        invalidatePlan();
        start(() -> {
            try (var executor = backend.open(connection)) { return new MySqlAccountService(executor).prepare(baseline, actions); }
        }, prepared -> {
            identityPlan = null;
            plan = prepared;
            preview.setText("Account: " + display(prepared.snapshot().account()) + "\nConnection: " + connection.label() + "\n\n" + String.join("\n", prepared.statements()) + (flush.isSelected() ? "\nFLUSH PRIVILEGES;" : ""));
            apply.setEnabled(true);
            status.setText(prepared.statements().size() == 1 ? "1 change ready to apply to the account above." : prepared.statements().size() + " changes ready to apply to the account above.");
        });
    }

    private void apply() {
        if (worker != null || plan == null && identityPlan == null) return;
        var connection = connection();
        var reviewed = plan;
        var identityChange = identityPlan;
        var flushAfter = flush.isSelected();
        invalidatePlan();
        start(() -> SqlSessions.run(backend.open(connection), executor -> {
                if (identityChange != null) {
                    var result = new AccountIdentityService(executor).apply(identityChange, flushAfter);
                    List<MySqlAccountService.AccountEntry> updated;
                    try { updated = new MySqlAccountService(executor).listAccounts(); } catch (Exception ignored) { updated = List.of(); }
                    return new Outcome(result.completedCount(), result.failedStage().isEmpty(), result.errorMessage(), result.refreshedSnapshot(), result.passwordResult(), updated);
                }
                var result = new MySqlAccountService(executor).apply(reviewed, flushAfter);
                return new Outcome(result.completedCount(), result.successful(), result.errorMessage(), result.refreshedSnapshot(), null, null);
        }), completion -> {
            var result = completion.value();
            if (result.accounts() != null) {
                allAccounts = result.accounts();
                updating = true;
                try {
                    accountModel.clear();
                    allAccounts.forEach(accountModel::addElement);
                    if (result.snapshot() != null) {
                        for (int i = 0; i < accountModel.size(); i++) if (accountModel.get(i).account().equals(result.snapshot().account())) accounts.setSelectedIndex(i);
                    }
                } finally { updating = false; }
            }
            if (result.snapshot() != null) showSnapshot(result.snapshot()); else clearSnapshot();
            status.setText(result.successful() ? result.completedCount() + " changes applied" + (flushAfter ? ", FLUSH completed and account state refreshed." : " and account state refreshed.")
                : result.completedCount() + " changes completed. " + result.errorMessage());
            if (!completion.cleanupWarning().isEmpty()) status.append(" " + completion.cleanupWarning());
            showPassword(result.password());
        });
    }

    private record Outcome(int completedCount, boolean successful, String errorMessage, MySqlAccountService.Snapshot snapshot,
                           SqlExecutor.QueryResult password, List<MySqlAccountService.AccountEntry> accounts) {
        @Override public String toString() { return "Outcome[completedCount=" + completedCount + ", password=redacted]"; }
    }

    private void editAccount() {
        if (snapshot == null) throw new IllegalArgumentException("Select and load the user to edit.");
        if (!actions().isEmpty()) {
            if (JOptionPane.showConfirmDialog(this, "Discard pending privilege changes to edit this user?", "Pending changes", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            discard();
        }
        var connection = connection();
        var baseline = snapshot;
        var name = new JTextField(baseline.account().name());
        var host = new JTextField(baseline.account().host());
        boolean wasLocked = allAccounts.stream().filter(entry -> entry.account().equals(baseline.account())).anyMatch(MySqlAccountService.AccountEntry::locked);
        var changeLock = new JCheckBox("Change login lock");
        var locked = new JCheckBox("Keep login locked", wasLocked);
        locked.setEnabled(false);
        changeLock.addActionListener(event -> locked.setEnabled(changeLock.isSelected()));
        var reset = new JCheckBox("Generate a new password (preserve the authentication method)");
        var form = Forms.stack();
        Forms.field(form, "User name", name);
        Forms.field(form, "Host", host);
        form.add(changeLock);
        form.add(locked);
        form.add(reset);
        form.add(new JLabel("Changes will appear in the review before they are applied."));
        if (JOptionPane.showConfirmDialog(this, form, "Edit " + display(baseline.account()), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        var target = new MySqlAccount(name.getText(), host.getText());
        var desiredLock = changeLock.isSelected() ? Boolean.valueOf(locked.isSelected()) : null;
        var resetPassword = reset.isSelected();
        invalidatePlan();
        start(() -> {
            try (var executor = backend.open(connection)) { return new AccountIdentityService(executor).prepare(baseline, target, desiredLock, resetPassword); }
        }, prepared -> showIdentityPlan(prepared, connection));
    }

    private void showIdentityPlan(AccountIdentityService.Plan prepared, ConnectionChoice connection) {
        plan = null;
        identityPlan = prepared;
        changes.clear();
        preview.setText("Edit account: " + display(prepared.change().baseline().account()) + "\nConnection: " + connection.label() + "\n\n" + String.join("\n", prepared.statements()) + (flush.isSelected() ? "\nFLUSH PRIVILEGES;" : ""));
        apply.setEnabled(true);
        status.setText("Review the name, host and other changes. Nothing has been applied yet.");
    }

    private void createAccount() {
        var connection = connection();
        var name = new JTextField();
        var host = new JTextField("%");
        var role = new JCheckBox("Create as a role, without login");
        var form = Forms.stack();
        Forms.field(form, "New account name", name);
        Forms.field(form, "Host", host);
        form.add(role);
        form.add(new JLabel("For a user, the server generates a password shown once in this screen."));
        if (JOptionPane.showConfirmDialog(this, form, "New account on " + connection.label(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        var account = new MySqlAccount(name.getText(), host.getText());
        if (account.name().isEmpty()) throw new IllegalArgumentException("Enter the new account name.");
        var isRole = role.isSelected();
        var flushAfter = flush.isSelected();
        start(() -> SqlSessions.run(backend.open(connection), executor -> new MySqlAccountService(executor).createAccount(account, isRole, flushAfter)), completion -> {
            var result = completion.value();
            clearAccountList();
            status.setText(result.created() ? "Account created: " + display(account) + ". " + (result.flushSucceeded() ? "FLUSH completed. " : "") + result.errorMessage() + " Reload users."
                : result.errorMessage());
            if (!completion.cleanupWarning().isEmpty()) status.append(" " + completion.cleanupWarning());
            showPassword(result.result());
        });
    }

    private void showPassword(SqlExecutor.QueryResult password) {
        if (password == null || password.rows().isEmpty()) return;
        var secret = textArea(5);
        secret.setText(String.join(" | ", password.columns()) + "\n" + password.rows().stream().map(row -> row.stream().map(value -> value == null ? "NULL" : value).collect(java.util.stream.Collectors.joining(" | "))).collect(java.util.stream.Collectors.joining("\n")));
        JOptionPane.showMessageDialog(this, new JScrollPane(secret), "Generated password: store it securely", JOptionPane.INFORMATION_MESSAGE);
        secret.setText("");
    }

    private void discard() { if (snapshot != null) showSnapshot(snapshot); else invalidatePlan(); }
    private void invalidatePlan() { plan = null; identityPlan = null; preview.setText(""); apply.setEnabled(false); }
    private ConnectionChoice connection() {
        var selected = (ConnectionChoice) connections.getSelectedItem();
        if (selected == null) throw new IllegalArgumentException("Select the connection to manage.");
        return selected;
    }
    private static String input(JComboBox<String> combo) { var value = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem(); return value == null ? "" : value.toString(); }
    private static String display(MySqlAccount account) { return (account.name().isEmpty() ? "(anonymous)" : account.name()) + " @ " + account.host(); }
    private static JTextArea textArea(int rows) { var area = new JTextArea(rows, 48); area.setEditable(false); area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)); return area; }
    private void button(JPanel parent, String label, Runnable action) { Forms.button(parent, label, action); }
    private static javax.swing.event.DocumentListener listener(Runnable changed) { return new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent event) { changed.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent event) { changed.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent event) { changed.run(); }
    }; }
    @FunctionalInterface private interface Request<T> { T run() throws Exception; }
    private <T> void start(Request<T> request, Consumer<T> success) {
        if (disposed || worker != null) return;
        setBusy(true);
        status.setText("Waiting for the selected connection...");
        worker = new SwingWorker<T, Void>() {
            protected T doInBackground() throws Exception { return request.run(); }
            protected void done() {
                if (disposed || worker != this) return;
                worker = null;
                setBusy(false);
                try { success.accept(get()); }
                catch (InterruptedException problem) { Thread.currentThread().interrupt(); }
                catch (ExecutionException problem) {
                    var cause = problem.getCause();
                    status.setText(cause instanceof IllegalArgumentException ? cause.getMessage() : "Could not complete the operation. Check privileges, the connection and current state before retrying.");
                }
                catch (RuntimeException problem) { status.setText("The operation was interrupted. Reload the state before continuing."); }
                apply.setEnabled(plan != null || identityPlan != null);
            }
        };
        worker.execute();
    }
    private void setBusy(boolean busy) { enableChildren(this, !busy); connections.setEnabled(!busy && pinnedConnectionId == null); status.setEnabled(true); apply.setEnabled(!busy && (plan != null || identityPlan != null)); tables.setEnabled(!busy && scope.getSelectedIndex() == 1); }
    private static void enableChildren(java.awt.Container parent, boolean enabled) { for (var child : parent.getComponents()) { if (!(child instanceof JLabel)) child.setEnabled(enabled); if (child instanceof java.awt.Container container) enableChildren(container, enabled); } }
    public void dispose() { disposed = true; if (worker != null) worker.cancel(true); worker = null; snapshot = null; plan = null; identityPlan = null; grants.setText(""); preview.setText(""); }
}
