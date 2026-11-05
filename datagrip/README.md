# Server Management

DataGrip plugin by **Lucas Passos** for managing MySQL/Aurora servers and building everyday SELECT statements. This module belongs to the public [lucasbp/toolkit](https://github.com/lucasbp/toolkit) monorepo. Nuria/RSD integrations are maintained separately.

## Server workspace

Open Server Management from the Database Explorer toolbar or the context menu directly below Properties. The submenu provides Server Info, Processes and User Accounts shortcuts. Management uses one central editor tab and the native Database Explorer as its only connection selector. There is no second connection list in the management view. While the management tab is selected, choosing a different MySQL/Aurora connection, schema or table in Database Explorer switches the central view to that server and preserves each server's pending review. The explorer selection takes priority over an open SQL console. With no valid target, the page directs you to Database Explorer. While a SQL editor is selected, explorer clicks do not activate management.

The workspace has three pages: Overview, Processes and Users & Access. Overview reads server hostname, port, version, edition, current schema, authenticated account, connection ID, read-only state and connection limit. Processes reads SHOW FULL PROCESSLIST and supports local filtering and manual refresh. It does not kill sessions. Process visibility follows the authenticated account's server privileges, as documented in the [MySQL manual](https://dev.mysql.com/doc/refman/8.4/en/show-processlist.html).

Connections reuse their DataGrip colors in the list, workspace header and editor tab. Active-tab highlighting still follows the IDE theme. Services is collapsed while management is selected and restored on exit when previously visible. Existing SQL consoles stay open.

## Users and access

Users & Access loads accounts for the chosen connection. Search by user or host. Global Privileges and Schema & Table Privileges are separate views of direct grants; role assignments have their own page and SQL Grants retains the original statements, including formats not summarized by the visual view. Global grants are displayed read-only. The privilege editor changes schema/table permissions; it does not silently promote them to global grants.

Edit user prepares identity, login-lock and server-generated password changes. External authentication methods are preserved. Role changes are explicit and default roles are reviewed separately. Changes are reviewed before Apply, then the account state is read again to detect concurrent changes. Statements execute sequentially without a promise of atomic DCL rollback. Partial failures report confirmed progress and do not retry automatically.

Run FLUSH PRIVILEGES after applying changes is selected by default. A flush failure is reported separately from confirmed account changes. Generated passwords are displayed once and are not written to SQL history by the plugin. Server-generated passwords require MySQL 8.0.18 or a compatible Aurora version.

## Build SELECT inside the SQL console

Use Build SELECT on a connected MySQL/Aurora console toolbar or editor context menu. The form opens inside the SQL editor and uses that console's exact connection. Search and select a table from DataGrip metadata, choose columns, an optional typed equality filter, ordering and a limit (1000 by default, maximum 2000). Count also respects the filter. Insert SELECT into console appends and selects the generated statement without executing it or replacing existing SQL. Close restores the original editor header.

Refresh DataGrip introspection if tables or columns are missing. Identifiers are quoted; text filters use hexadecimal UTF-8 literals. A changed or removed console connection blocks insertion until the form is reopened.

## Build and install

Target: DataGrip 2026.2.6.1, build 262.11879.103, Java 25, Gradle Wrapper 9.8.2 and IntelliJ Platform Gradle Plugin 2.19.0. The declared compatibility range is the 262 line starting at 262.11879.

Run build.ps1 with optional LocalIdePath, JdkPath and ArtifactDirectory arguments. Tests, structural checks and packaging run before the script copies the exact current ZIP and SHA-256 to artifacts/datagrip in the main checkout, including when building from a worktree. That folder is Git-ignored. Current package: server-management-0.4.1.zip. Older install packages are archived.

Install the ZIP without extracting it using Settings > Plugins > gear > Install Plugin from Disk, then restart when requested. The plugin ID remains io.github.lucasbp.datagrip-toolkit, so this updates the previous installation. The display name and vendor are Server Management and Lucas Passos. The package is not Marketplace-signed.

The workflow tests, packages and verifies compatibility. A matching datagrip-v* tag publishes the installable ZIP and checksum as a prerelease asset. This 0.4.1 package is currently local; it has not been published to GitHub in this iteration. See [validation](docs/VALIDATION.md) for current evidence and limits.
