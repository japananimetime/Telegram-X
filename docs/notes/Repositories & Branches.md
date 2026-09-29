# Repositories & Branches

## Remotes (local clone `~/Projects/Telegram-X`)
```
fork    git@github.com:japananimetime/Telegram-X.git   # push here (HTTPS fallback if SSH fails)
origin  https://github.com/TGX-Android/Telegram-X.git   # upstream, read-only
```
Upstream `origin/main` moves on without us: as of 2026-09-06 afc is 140 commits ahead and 160 behind upstream. Rebasing onto upstream is a project in itself (see [[BRANCH_RECONSTRUCTION_2026-06-13]] for how painful the last one was).

## The three repos that must stay alive
| Repo | Branch | Role |
|---|---|---|
| `japananimetime/Telegram-X` | `all-features-combined` | the app |
| `japananimetime/tdlib` (39 MB) | `tgx/tdlib-flat` | TDLib Java bundle for the **rich-messages TdApi** (regenerated from `tdlib/td@cecbf129`), prebuilt `libtdjni.so` for 4 ABIs committed as plain blobs (no LFS, no native TDLib build needed) |
| `japananimetime/tdlib-utils` | `tgx/td-richmessages-bindings` | `vkryl/td` bindings regenerated for that TdApi, plus `copyOf` helpers |

`.gitmodules` on `all-features-combined` points `tdlib` and `vkryl/td` at these forks; the other 29 submodules (ffmpeg, webrtc, tgcalls, ...) point at upstream `TGX-Android`. `tgcalls`/`webrtc` get two local patches at setup time, see [[Build & Environment]].

> **Trap:** `fork/main` still points its `tdlib` submodule at commit `a6b22173`, which exists in **no** public repo. `main` does not build from a fresh clone. Only `all-features-combined` (and `parity-fixes/2026-06-14`, identical head) is reproducible.

## Branch map (on `fork`)
| Branch | What it is |
|---|---|
| `all-features-combined` | **Product / integration branch.** Head `e33134ba` (2026-06-17). Rule from CLAUDE.md: never commit here directly, merge feature branches in. |
| `parity-fixes/2026-06-14` | Last working branch, already fast-forwarded into afc (same head). |
| `main` | Old pile: 196 fork commits on top of a Jan-2026 upstream, diverged from afc (merge-base 2026-01-10). Superseded; keep for history only. |
| `core/tdlib`, `base/tdlib` | The clean core = upstream + TDLib upgrade; feature branches were rebased onto `core/tdlib`. |
| `feature/*` | One branch per feature: `rich-messages`, `mini-apps`, `mini-apps-hardened`, `stories`, `gifts`, `stars`, `premium-billing`, `quotes`, `saved-tags`, `profile-notes`, `playback-speed`, `disposable-voices`, `reactions-improvements`, `voice-transcription`, `native-video-calls`, `community-features` |
| `forum-topics-implementation`, `stories-implementation` | Older feature branches (note: no `feature/` prefix, unlike what the old CLAUDE.md table claims) |
| `fix/post-reconstruction-gaps` | Fix batch after the reconstruction |
| `backup/*` | Pre-migration snapshots of feature branches (`afc-pre-tdlib`, `afc-pre-p18`, `rich-messages-presplit`, ...) |
| `deprecated/*` | NTgCalls call stack; intentionally not merged, see [[DEPRECATED_BRANCHES]] |

afc history shape: 1 commit 2024-08, 5 in 2026-01, 134 in 2026-06 (the reconstruction rebased everything onto the core in June 2026, so blame dates are misleading).

## Day-to-day workflow (from CLAUDE.md)
```bash
git checkout all-features-combined && git pull fork all-features-combined
git checkout -b fix/<topic> 
# ... work, build, test ...
git checkout all-features-combined && git merge fix/<topic>
git push fork all-features-combined   # only when asked / ready
```
Commit messages on afc use `type(scope): summary [#mantis-id]`, e.g. `fix(stories): clamp stale story index [#864]`.

## Working-tree hygiene
- `local.properties`, `keystore/`, `.claude/` toolset are gitignored and machine-specific.
- After any `git submodule update`, re-run the native patch script (see [[Build & Environment]]) or group calls crash on join.
- Generated files in `vkryl/td` (`TdCompileAssert.kt`, `TdUnsupported.kt`, `TdEqualsTo.kt`) are rebuilt by `:app:generateResourcesAndThemes`; never hand-edit, add types in `buildSrc/.../config/TdlibEqualTypes.kt`.
