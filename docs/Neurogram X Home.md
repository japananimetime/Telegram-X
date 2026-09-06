# Neurogram X — Home

**Neurogram X** is your feature-extended fork of [Telegram X](https://github.com/TGX-Android/Telegram-X) (Android, TDLib-based). App id `space.hikaro.tgx`. This vault (`docs/` in the repo) is the working knowledge base: start here, follow links, and add a note whenever you learn something the code doesn't say by itself.

## Quick facts
| | |
|---|---|
| Fork repo | `github.com/japananimetime/Telegram-X` — remote **`fork`** (push here) |
| Upstream | `github.com/TGX-Android/Telegram-X` — remote `origin`, read-only |
| Product branch | **`all-features-combined`** (afc). Never commit on it directly: branch off it, merge back. |
| Companion repos | `japananimetime/tdlib` (`tgx/tdlib-flat`), `japananimetime/tdlib-utils` (`tgx/td-richmessages-bindings`) — see [[Repositories & Branches]] |
| Dev machine | Windows (PowerShell + Git Bash), JDK 21, wireless ADB to an ARM phone. Linux box has no Android toolchain yet — see [[Build & Environment]] |
| Tracker | MantisBT project 1 (Windows-only MCP). GitHub issues are disabled on the fork — see [[Bug Tracking]] |
| Local clone (Linux) | `~/Projects/Telegram-X`, checked out on `all-features-combined` |

## Notes
- [[Repositories & Branches]] — remotes, branch map, submodule pointers, what diverged from what
- [[Build & Environment]] — toolchain versions, `local.properties`, signing / experimental flag, native patches, setup on a fresh machine
- [[Architecture Map]] — packages, navigation framework, TDLib access, what the fork added where
- [[Forum Topics]] — the topics implementation end to end (your bug hotspot)
- [[Feature Areas]] — every fork feature: branch, key files, state, open issues
- [[Known Issues & Open Items]] — consolidated open list from all audits and handoffs
- [[Bug Tracking]] and [[bugs/Bug Index]] — how bugs are tracked, one note per bug
- [[Debugging Playbook]] — adb, logs, finding the code behind a screen

## Existing project documents (same folder)
- [[FORK_REAUDIT_2026-06-13]] — latest full audit, most items resolved; open tail listed inside
- [[FORK_QUALITY_AUDIT]] and [[FORK_GAP_AUDIT]] — earlier audits (largely resolved, still the best "where things are" references)
- [[HANDOFF_2026-06-15_calls-parity-push]] — last session handoff: native calls + push, "compile-verified, not device-tested"
- [[HANDOFF_2026-06-14]] and [[BRANCH_RECONSTRUCTION_2026-06-13]] — how the branch topology was rebuilt
- [[BUILD_FROM_SCRATCH]] — disaster-recovery build steps
- [[DEPRECATED_BRANCHES]] — NTgCalls branches, do not revive
- [[RICH_TEXT_AUDIT_2026-06-15]] — rich message rendering tracker
- [[GUIDE]] — upstream contributor guide (navigation, animators, themes)
- `../TASKS.md`, `../README.md`, `../.claude/CLAUDE.md` — outside the vault; CLAUDE.md is the authoritative rules file for AI sessions

## When you sit down to fix a bug
1. Open [[bugs/Bug Index]], create a note from [[bugs/_Bug Template]].
2. Find the screen with [[Debugging Playbook]], then the owning code via [[Architecture Map]] or [[Forum Topics]].
3. Check [[Known Issues & Open Items]] — the bug may already be described with a file:line.
4. Branch from `all-features-combined`, fix, build, device-test, merge back, push to `fork`.
5. Record the fix in the bug note (cause, files, commit) so the next one is faster.
