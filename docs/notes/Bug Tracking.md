# Bug Tracking

## Where bugs live
1. **MantisBT, project id 1** — the tracker of record, categories General / Interface / Purchases / Stories / Topics (categories only creatable in the web UI). Statuses `new → assigned → resolved → closed`. Reached through the `mcp__mantisbt__*` MCP tools which exist only in the Windows box's gitignored `.claude/` toolset (needs `MANTIS_API_TOKEN`). Commit messages reference tickets as `[#864]`; known ranges: #813–#837 bug audit loop, #838–#859 parity audit, #856 native calls, #862 rich text math, #864 stories fixes, #869 typing fix.
2. **GitHub issues are disabled** on `japananimetime/Telegram-X`, and there are no PRs. Do not look for bugs there.
3. **This vault** — [[bugs/Bug Index]] with one note per bug from [[bugs/_Bug Template]]. It is the only tracker reachable from the Linux box, and it keeps the *why* next to the code. Mirror the Mantis id in the note title when one exists.

## Workflow per bug
1. Create `notes/bugs/<Mantis id or short slug> - <title>.md` from the template; fill symptom, device, build, repro.
2. Locate: [[Debugging Playbook]] → screen → controller; check [[Known Issues & Open Items]] and the regression list in [[Forum Topics]].
3. Branch `fix/<slug>` off `all-features-combined`; commit as `fix(<scope>): <summary> [#id]`.
4. Build, install, verify on the phone; note logcat evidence in the bug note.
5. Merge into afc, push to `fork`; in Mantis add the implementation-summary note (files, key changes, TDLib functions, commit + branch) and set resolved/fixed.

## Severity vocabulary (from the audits)
P0 crash / security / data loss / fake-working · P1 correctness · P2 quality/polish · gap = missing vs official · design = UX shortfall.
