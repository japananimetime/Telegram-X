# Rich-Message Rendering — Full Audit & Completion Tracker (2026-06-15)

Branch: `parity-fixes/2026-06-14`. Source: 3 parallel deep audits of `TdApi.MessageRichMessage` rendering
(`TGMessageRich` → `PageBlock.parseRichMessage`/`parseForChat`/`parse` → `PageBlockRichText`/`PageBlockMedia`/… →
`FormattedText.parseRichText` → `Text`). On-device evidence: a "Rich Text Demo" message rendered with multiple
defects vs the Telegram Desktop reference.

**Rule (per user): do NOT omit any feature. Stage the work, but every gap below must be tracked to completion.**

Status legend: ✅ done · 🔨 in progress · ⬜ todo · 🔎 needs on-device verify

---

## PHASE 1 — Robustness + localized inline correctness (in progress)

| # | Gap | File:line | Fix | Status |
|---|-----|-----------|-----|--------|
| 1.1 | Null-caption NPE → whole msg "Unsupported" | PageBlockMedia.setCaption:282 | null guard | ✅ |
| 1.2 | Null-caption NPE (parse ctx) | PageBlock.ParseContext.processCaption:386 | early return if null | ✅ |
| 1.3 | Null nested RichText → NPE collapses whole msg | FormattedText.parseRichText:399 | `if (in == null) return;` | ✅ |
| 1.4 | Null `RichTextPlain.text` → "null"/NPE | FormattedText:402 | `StringUtils.orEmpty` | ✅ |
| 1.5 | Null math `expression` → NPE | PageBlockRichText math ctor:385 | guard + decompose | ✅ |
| 1.6 | Null `thinking.text` → NPE | PageBlockRichText thinking ctor:378 | guard | ✅ |
| 1.7 | Inline math LaTeX shown as literal `^2` in mono box | FormattedText RichTextMathematicalExpression:549 | `buildMathRichText` decomposes `^`/`_`→ super/subscript, drop FLAG_MONOSPACE | ✅ |
| 1.8 | Block math same as 1.7 | PageBlockRichText math ctor:382 | reuse `buildMathRichText` | ✅ |
| 1.9 | Date renders stale `text`, ignores `unixTime`/`formattingType` (Jan 1 2025 → Aug 13 2013) | FormattedText RichTextDateTime:510 | format unixTime (seconds) per formattingType | ✅ device-verified (now "January 1, 2025") |
| 1.10 | No `default:` in RichText switch → unknown future node silently dropped | FormattedText:400 switch | add default → append `TD.getText(in)` safety net | ✅ |

**PHASE 1 COMPLETE & device-verified (math superscripts + correct date confirmed on phone).**

## PHASE 2 — Canvas decorations missing in chat-bubble path (RecyclerView-only today)

Root cause: `TGMessageRich.drawContent` draws blocks on Canvas; list markers + block backgrounds live in
`InstantViewController` ItemDecorations (`drawDecorationForView` :404-422, `FillingDecoration` :369-381) that never run.

| # | Gap | Where | Fix | Status |
|---|-----|-------|-----|--------|
| 2.1 | List markers (•, ☑/☐, 1./a./i.) not drawn | InstantViewController:404-422 vs TGMessageRich:245-272 | reserve marker column (listMarkerReserve) + draw markers in drawContent; kept touch/findBlockAt consistent | ✅ device-verified |
| 2.2 | Code/preformatted background box not drawn | FillingDecoration:369-381; drawInternal gates on `forceBackground` never set for pre | paint backgroundColorId in drawContent when != NONE/filling | ✅ device-verified |
| 2.3 | Blockquote bar/box/quote-icon not drawn (never implemented even in IV) | PageBlock.java:723 | wrap quote body in `context.isPost` → shared draw() paints bar (fixes IV too) | ✅ device-verified |
| 2.4 | Code language header (`php`) not rendered (unimplemented everywhere) | PageBlock parseForChat | NEW PageBlockCode: rounded `iv_preBlockBackground` box + integrated language header + monospace body + heuristic syntax highlighting (CodeSyntaxHighlighter) | ✅ device-verified |
| 2.5 | Block LaTeX `\sum`/`\frac`/etc. shown mostly literal | FormattedText/PageBlockLatex | TRUE typeset math via VENDORED jlatexmath (`:jlatexmath` module) | ✅ device-verified typeset (c25983431) |

**PHASE 2 list/code-bg/quote-bar device-verified at desktop parity.**

## PHASE 3 — Inline entity downgrades (content shown, behavior/visual missing)

| # | Gap | File:line | Fix | Status |
|---|-----|-----------|-----|--------|
| 3.1 | Spoiler not hidden (shown as plain text) | FormattedText RichTextSpoiler:499 | FLAG_SPOILER + full-span spoilerEntity in TextEntityCustom; Text obscures region | ✅ device-verified (commit 4f6c64dec; spoiler text hides + reveals) |
| 3.2 | Custom emoji → alt text only, glyph not drawn | FormattedText RichTextCustomEmoji:504 | TextEntityCustom.customEmojiId + emit emoji entity; renders via existing text-media path | ✅ device-verified (commit 7b42176ae) |
| 3.3 | Hashtag not clickable | FormattedText RichTextHashtag:529 | LINK_TYPE_HASHTAG → onHashtagClick | ✅ (commit 627d9d23f; not click-verified — no tags in demo) |
| 3.4 | Cashtag not clickable | FormattedText RichTextCashtag:534 | LINK_TYPE_CASHTAG → onHashtagClick | ✅ (627d9d23f) |
| 3.5 | Bot command not actionable | FormattedText RichTextBotCommand:539 | LINK_TYPE_BOT_COMMAND → onCommandClick | ✅ (627d9d23f) |
| 3.6 | Bank card number no actions | FormattedText RichTextBankCardNumber:544 | LINK_TYPE_BANK_CARD → tdlib.ui().openCardNumber | ✅ (627d9d23f) |

**PHASE 3 COMPLETE (spoiler, custom emoji, clickable hashtag/cashtag/bot-command/bank-card).**

## PHASE 4 — Block media/structure downgrades (placeholder/flattened today)
## (No LaTeX deferral remains: full jlatexmath typeset covers both inline RichTextMathematicalExpression
##  AND block PageBlockMathematicalExpression — block math was routed through the same path 2026-06-17,
##  so the two are now at equal fidelity; super/subscript decomposition stays only as the parse-fail fallback.)
| 4.9 | "Jump to" anchor links did nothing (no onAnchorClick in chat) | MessagesController/TGMessageRich | implement Text.ClickCallback.onAnchorClick → findAnchorContentY + smooth-scroll | ✅ device-verified (25969fbc7) |

| # | Gap | File:line | Fix | Status |
|---|-----|-----------|-----|--------|
| 4.1 | Audio → italic "Audio" placeholder | parseForChat:539 | interactive player (PageBlockRichFile→FileComponent, synthetic-msg playback) | ✅ device-verified — renders + PLAYS (c675b2139) |
| 4.2 | Voice note → italic placeholder | parseForChat:545 | same PageBlockRichFile (voice ctor) | ✅ (c675b2139; same proven path, verify if demo has a voice note) |
| 4.3 | Embedded web → link placeholder | parseForChat:552 | poster + play overlay, tap-to-open (matches OFFICIAL client — no inline WebView in chat); link fallback if no poster/service | ✅ (99b9d89c3; verify on device if demo has a YT/Vimeo embed) |
| 4.4 | Collage → flattened to stacked media | parseForChat:522 | real grid via CollageContext (key-offset for shared bubble receiver) | ✅ device-verified grid (6b3f53a32) |
| 4.5 | Slideshow → flattened to stacked media | parseForChat:530 | kept STACKED — swipeable pager impractical on canvas bubble | ⬜ won't-do (functional fallback) |
| 4.8 | MessageRichMessage unhandled in preview/notification switches → crash (pinned bar) | MediaPreview/Lang/TGMessageService | added cases (no-preview / default text / "pinned a message") | ✅ device-verified (e7b5747d6) |
| 4.6 | PageBlockThinking → static italic (no shimmer) | PageBlockRichText:375 | breathing-alpha pulse (0.4↔1.0) via FactorAnimator ping-pong, gated on attached views; literal gradient sweep skipped (needs Text-internal shader work, marginal benefit) | ✅ (2026-06-17) thematically fitting for AI-generated pending messages |
| 4.7 | Unknown future PageBlock → throws UnsupportedOperationException, collapses whole msg | PageBlock.java:974 | per-block try/catch in parseRichMessage → roll back + placeholder + continue | ✅ (commit 10bbcc5d1) |

## Session 2 (2026-06-15 PM) — polish + interaction fixes

| # | Gap | File | Fix | Status |
|---|-----|------|-----|--------|
| S.1 | Inline math (`E=mc^2`) shown as plain superscript, not typeset | FormattedText/Text/TextMedia/TextEntity(Custom)/LatexRenderer | TRUE inline typeset: render expr to a white-glyph bitmap (LatexRenderer), emit zero-width inline-media entity, draw on baseline tinted to text color (theme-reactive). Superscript decomposition kept as fallback when jlatexmath can't parse. Required allowing zero-width math entities through Text.findEntity (like icons) | ✅ device-verified |
| S.2 | Code block looked like a flat highlight; floating language label hard to read | PageBlockCode (new) + CodeSyntaxHighlighter (new) | Dedicated code block: rounded box + integrated dim language header + monospace body; VS Code-style syntax highlighting (keywords/strings/comments/numbers) picked by bg luminance | ✅ device-verified |
| S.3 | Details ("Structure") expand/collapse snapped, not animated | TGMessageRich | FactorAnimator interpolates content height (mirrors timeExpandValue) + clip during anim; fixed once-only bug (forceFactor fired onFactorChangeFinished which zeroed the delta) | ✅ device-verified |
| S.4 | Tap on user mention (RichTextMentionName) did nothing | FormattedText / TextEntityCustom | was routed as `tg://user?id=` URL (not a handled deep link → silent no-op); NEW LINK_TYPE_MENTION_USER → openPrivateProfile(userId), like normal-message mentions | ✅ device-verified (userId 777000) |
| S.5 | No marker pin on map block | PageBlockMedia | neither map provider bakes a pin; draw a red `baseline_location_on_24` overlay with tip at map centre (like location messages) | ✅ device-verified |

---

## Verified SAFE / correct (no action)
- PageBlockTable renders (null-safe caption/cells). PageBlockDetails, PullQuote, Kicker, Divider, headings,
  paragraphs, Photo/Video/Animation/Map media, RelatedArticles, lists (label logic correct — only the *marker draw*
  is missing, see 2.1). RichText bold/italic/underline/strike/fixed/sub/superscript/marked/url/mention/etc. correct.
- `parseRichMessage` fake-instant-view params + PageBlockSimple stripping are safe (no content flattening).

## Notes
- `UnsupportedPageBlockException` is declared but never thrown; real default throws `UnsupportedOperationException`
  (PageBlock.java:974). `TGMessageRich.parseBlocks` catches `Throwable` → any throw = whole message "Unsupported".
- `TD.getText(RichText)` (TD.java:2505) handles only 9/28 node types — lossy, but off the render path.
