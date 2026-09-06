# Upstream sync assessment (2026-09-06)

Merge base of `all-features-combined` with upstream: `1e1a4dd3` (2026-01-10). Upstream `TGX-Android/Telegram-X` main is at `67501370` (2026-09-05): **160 commits**, 558 files; the fork changed 551 files; **98 files overlap**. Upstream app version 1785 → 1808.

## What upstream gained since the fork's base
| Area | Commits / notes | Value for the fork |
|---|---|---|
| TDLib | 8 upgrades, now `tdlib/td@d1085f9` (Sept 2026) + rebuilt OpenSSL; upstream code now references `richMessage` in `TD.java`, `ContentPreview`, `InputView`, `MediaPreview`, `Lang` | **High.** Upstream TDLib is newer than the fork's custom `rich-messages` bundle (regen from `cecbf129`), and rich messages are now handled upstream. A sync could retire `japananimetime/tdlib` + `tdlib-utils`. |
| Service / message types | new service messages, checklist tasks, replied poll option, `MessageManagedBotCreated`, gifts strings, `0 months` fix | Medium: the fork already routes unknown types to a placeholder; upstream renders them. |
| Notifications / push | Android 12+ reliability (`PushProcessor`, `Tdlib`), fix for >327,675 notifications, Firebase Installation ID on Android 6+, contacts-sync foreground service | Medium-high. The fork ported null-nick's push fix instead; expect conflicts in `PushProcessor`/`Tdlib`. |
| Calls | tgcalls upgrade; **WebRTC/tgcalls split out of the app module** (`app/jni/tgvoip/…`, 35 files) | Conflicts head-on with the fork's native calls (`app/jni/tgvoip.cpp`, `group_call.cpp`, patches). |
| Build system | Gradle 9.7, AGP 9.3.1, built-in Kotlin, NDK r27 LTS, SDK 37, build-tools 37, `marshmallow` flavor, baseline profile, V3/V4 signing, smaller APKs, opus/ffmpeg/libvpx/flac/libyuv upgrades, `patch-opus.sh` / `patch-androidx-media.sh` / `update-dependencies.sh` removed | High for long-term maintenance, but 54 build files rewritten (+2.9k/−2k): the fork's scripts and the flat-TDLib fallbacks would all need redoing. |
| UX fixes | infinite "Connecting" on intro, comment scrolling, clipped call buttons, Instant View media, archive entry, launch time, rotated-video transcode workaround, vendor-bug protection, recent-actions fixes | Medium, mostly small and self-contained. |

## Conflict hot spots (both sides changed, upstream churn in lines)
`Tdlib.java` (993), `app/build.gradle.kts` (799), `TGMessageService.java` (635), `TdlibUi.java` (614), `U.java` (569), `TD.java` (533), `CreatePollController` (470), `strings.xml`, `TGMessagePoll`, `Lang.java`, `TGMessage.java`, `TdlibEqualTypes.kt`, `MessagesController.java` (181), `PageBlock*`, `FormattedText`, `ContentPreview`, `MessagesManager`.

## Options
1. **Do nothing now.** Fork works; upstream deltas are mostly infrastructure. Cost: growing divergence, custom TDLib to maintain.
2. **Cherry-pick a few small fixes** onto afc (intro "Connecting" fix, 327k-notifications fix, gifts/months string fixes, Instant View fix). Low effort each, but several touch `Tdlib.java` and assume the newer TdApi — verify each compiles against the fork's TDLib.
3. **Full re-base of the feature set onto upstream main** (the June 2026 "reconstruction" again): new `core` = upstream `67501370`, drop the custom TDLib if upstream's TdApi covers rich messages, then re-apply feature clusters one by one, compile-gating each. Realistically days, with calls and build system as the two big fights. Biggest payoff: newest TDLib, official rich messages, modern build chain, no private TDLib fork to keep alive.

Recommendation: option 2 now for the user-visible fixes, plan option 3 as its own project when there is time; do not attempt a plain `git merge`.

## Done 2026-09-06: option 2, branch `fix/upstream-cherry-picks-2026-09`
Picked (12, all `-x` referenced, compile-verified): Recent Actions crash typo `0fee54ca`; launch optimization + Tdlib reference ids `cc99c371`; rotated-video transcode workaround `56f3c438`; DB upgrade on 2nd launch `34737faf`; infinite "Connecting" on intro `fe431752`; "0 months" gift text `4d6207e1`; clipped call buttons `e9016599`; `PREMIUM_SUB_ACTIVE_UNTIL_` error `6c4dc941`; gift strings `f43724a5`; comment scrolling `bf670928`; Instant View null caption `88f1a6c1` (by hand); secret-chat lock emoji `70c79232` (by hand, without the checklist separator string).

Skipped, conflict with fork code: archive entry optimization `edc0a57f` (vkryl/core submodule bump); vendor-bug protection `3ed75240` and 327k-notifications fix `1501b648` (both rename foreground notification ids across `U.java`/notification manager, collides with the fork's call/FGS work); `MessageManagedBotCreated` `b8ee9ae7` (fork has no such service message yet); contacts-sync alert `f5d2ec8a` (`TdlibContactManager` diverged); Firebase Installation ID `3bb31c26` (15 files of push infrastructure, do together with a proper sync).
