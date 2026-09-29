# Deprecated branches

Branches kept for history but **intentionally not merged** into
`all-features-combined`. If an audit reports one of these as "missing from afc", that is
expected — see the reason below.

## NTgCalls calling stack — superseded by native tgcalls (#856)

An earlier calling implementation based on **NTgCalls** (by @Laky-64) was evaluated and
then replaced by this fork's own **native tgcalls** integration (1:1 + group video +
screen sharing), which lives in `all-features-combined` (`TgCallsController`,
`GroupCallController`, `app/jni/{tgvoip,group_call}.cpp`, MantisBT #856). The NTgCalls code
(`org/pytgcalls/ntgcallsx/*`) is **not** in afc and will not be merged.

| Deprecated branch | Was | Notes |
|---|---|---|
| `deprecated/ntgcalls` | `feature/ntgcalls` | NTgCalls implementation by @Laky-64 |
| `deprecated/calls-invite-to-video-chat` | `feature/calls` | "Invite to Video Chat" UI, built on the NTgCalls stack. The only user-facing feature unique to it is the invite-link UI (`btn_inviteToCall` / `InviteToVideoChat`); not ported because it is NTgCalls-coupled |
| `deprecated/pr-916-videocalls` | `pr-916-videocalls` | Source of the NTgCalls integration (PR #916) |
| `deprecated/ntgcalls-integration` | `ntgcalls-integration` | Early NTgCalls integration work |

`laky64-main` is an upstream mirror of @Laky-64's branch, not part of this fork's feature
set — left as-is.

### If you ever want the "Invite to Video Chat" link UI
Re-implement it on top of the native tgcalls `GroupCallController` rather than reviving the
NTgCalls branches — the underlying call engine is different.
