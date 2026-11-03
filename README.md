# Perforce P4 (Helix Core) support for Consulo

Integration with the Perforce version control system through the `p4` command-line client: connections from the
settings, `P4CONFIG` files, the environment and the P4V client configuration; login and tickets; change lists with
two-way synchronization of names and descriptions; checkout on edit; add, delete, move and copy; submit with jobs;
revert, revert unchanged; sync and integrate with resolve; shelve and unshelve (shelved changes are shown under their
change list in Local Changes); history, annotate, diff and merge; committed changes browsing; offline mode;
`.p4ignore` handling. Ported from the JetBrains Perforce plugin (see NOTICE.txt).

## Modules

- `perforce-impl` — all code, the localized messages (`LOCALIZE-LIB`), the icons (`ICON-LIB`) and the unit tests.
- `plugin` — the packaging module (`consulo.perforce`).
- `it` — headless integration tests on `consulo-it`, partly against a real Perforce server.

## Tests

`mvn test` runs the unit tests (p4 output parsers, forms, views, change mapping, jobs). The `it` module starts a
throwaway `p4d` per test when it finds the `p4` and `p4d` binaries in `P4_TEST_BIN_DIR` (or the `p4.test.bin.dir`
system property, or `PATH`); without them those tests are skipped:

```bash
P4_TEST_BIN_DIR=~/.local/p4 mvn test
```

## Not supported

The new-UI main toolbar widgets (workspace combo box, quick actions popup), the clone dialog extension (checkout uses the
classic checkout provider page), the committed changes filter action, the changes view offline status line, automatic
detection of Perforce roots on project opening, and the trusted-project checks.
