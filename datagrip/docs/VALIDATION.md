# Validation: Server Management 0.4.1

Validated locally on 2026-11-04 against DataGrip 2026.2.6.1, build 262.11879.103, Java 25 and Gradle 9.8.2.

104 tests passed, zero failures. The connection workspace no longer renders a list, combo box or search field. Tests cover delegating selection to the native explorer, preserving pending reviews when changing servers, clearing the active target without losing its cached state, removed servers without fallback, and explorer selection taking priority over a SQL console. Account, scope, monitoring and inline SELECT regressions from 0.4.0 also passed.

Build and plugin structure checks passed. Plugin Verifier reported Compatible with no deprecated or internal API usage. The two experimental ColorBunch usages for native colors remain the same as in 0.4.0. Compatibility is restricted to the 262 SDK line.

Native smoke checks used an isolated DataGrip profile and the separate local fixture plugin outside this repository. Two fake sources targeted loopback port 9, without credentials or automatic synchronization. No real MySQL/Aurora or RSD endpoint was used. Driver downloads were canceled; no driver was installed during the test.

Observed: selecting the purple fake source in Database Explorer and opening management showed the purple server using the full central area, without a duplicate sidebar. Clicking the blue source directly in Database Explorer changed the existing management view to the blue server, updating the server name and color without opening another editor tab. Returning to the SQL console and then clicking the purple source left the SQL console active. The automatic listener applies only when the management editor is selected. Listeners and the debounce timer are detached on project disposal.

Opening without a valid target shows an instruction and a Show Database Explorer button instead of another picker. Source-specific context-menu shortcuts still open Overview, Processes or Users & Access. Server views remain cached by exact source identifier, preserving pending account changes when switching targets.

This release changes navigation only. Monitoring remains read-only; global privileges remain a read-only view; schema/table/role mutations retain review and explicit Apply. Real server reads, account changes and SSH/TLS variations still require authorized homologation. The inline SQL builder and header restoration were exercised in the previous 0.4.0 native smoke check and their implementation is unchanged here.

Install artifact: artifacts/datagrip/server-management-0.4.1.zip with SHA-256 alongside it, ignored by Git. Older ZIPs are archived. Plugin ID and vendor remain io.github.lucasbp.datagrip-toolkit and Lucas Passos. The package is local and has not been pushed or released to GitHub in this iteration. The isolated IDE was stopped; the daily-use IDE was not modified.
