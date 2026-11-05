# Validation: Server Management 0.4.0

Validated locally on 2026-11-04 against DataGrip 2026.2.6.1, build 262.11879.103, Java 25 and Gradle 9.8.2.

103 tests passed, zero failures. Coverage includes quoting, schema/table grants, roles/defaults, identity changes, concurrent-state checks, partial failures, flush behavior and secret redaction. New tests cover inline connection filtering without opening a server, retained pending state when switching connections, removed connections without fallback, monitoring that never mutates the server, global scope versus a quoted schema named *, and SELECT generation with explicit table selection, typed filters, ordering, row limits and filtered counts.

Build and plugin structure checks passed. Plugin Verifier reported Compatible with no deprecated or internal API usage. Two experimental usages are limited to the current ColorBunch API used to reuse DataGrip connection colors. The plugin compatibility range is restricted to the validated 262 SDK line.

Native smoke checks used an isolated DataGrip profile, a safe-mode project and a separate local fixture plugin outside this repository. The fixture created two fake MySQL data sources targeting loopback port 9, with synchronization disabled and no credentials. No real MySQL/Aurora or RSD endpoint was used. The fixture is not included in the shipped ZIP.

Observed in the IDE: one central management tab; inline connection list with search and native color markers; filter reducing two fake connections to one; connection name and colored header; Overview/Processes/Users & Access page labels; Server Management directly below Properties; submenu shortcuts to Server Info, Processes and User Accounts; Build SELECT inside the existing SQL console; Close restoring the console header and preserving existing SQL. Account forms and separated privilege views were also rendered with a fake backend. The white divider was replaced by an adjustable divider painted with the current theme background.

The native check identified two SDK integration issues and both were corrected: editor headers wrap permanent toolbars, so closing the inline form checks its owned editor marker rather than header object identity; opening a managed JDBC connection from Swing workers needs a cancellable progress context. The corrected Close was exercised in the IDE. A fake source without driver files was canceled and the page returned to an explicit failure state without the previous progress-context IDE error.

Overview reads a SELECT of server variables. Processes reads SHOW FULL PROCESSLIST without a kill action or automatic refresh. The server can restrict process visibility to the current account. These reads were validated with fake executor responses; successful monitoring against a live server remains unverified. Real permission mutations, SSH/TLS variations and authentication prompts require authorized homologation.

Global privileges are read-only in this delivery. Schema/table changes and role assignments retain the existing review/apply flow. The complete runtime UI and validation messages are English; user-provided connection and account names remain unchanged.

Current install artifact: artifacts/datagrip/server-management-0.4.0.zip with SHA-256 alongside it, ignored by Git. Plugin ID preserved; vendor Lucas Passos. This iteration has not been pushed or released to GitHub. The daily-use IDE was not modified, and the isolated test IDE was stopped after validation.
