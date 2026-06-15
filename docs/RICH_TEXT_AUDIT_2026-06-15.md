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
| 2.4 | Code language header (`php`) not rendered (unimplemented everywhere) | PageBlockRichText preformatted ctor:216 | render `PageBlockPreformatted.language` as small caption above code | ⬜ |
| 2.5 | Block LaTeX `\sum`/`\frac`/etc. shown mostly literal (only `^`/`_` decomposed) | FormattedText.buildMathRichText | full LaTeX needs a math renderer; current is best-effort super/subscript only | ⬜ (large — known limitation) |

**PHASE 2 list/code-bg/quote-bar device-verified at desktop parity.**

## PHASE 3 — Inline entity downgrades (content shown, behavior/visual missing)

| # | Gap | File:line | Fix | Status |
|---|-----|-----------|-----|--------|
| 3.1 | Spoiler not hidden (shown as plain text) | FormattedText RichTextSpoiler:499 | spoiler effect via TextEntityCustom spoiler support | ⬜ |
| 3.2 | Custom emoji → alt text only, glyph not drawn | FormattedText RichTextCustomEmoji:504 | render customEmojiId via emoji/text-media receiver | ⬜ |
| 3.3 | Hashtag not clickable | FormattedText RichTextHashtag:529 | clickable + hashtag search | ⬜ |
| 3.4 | Cashtag not clickable | FormattedText RichTextCashtag:534 | clickable + cashtag search | ⬜ |
| 3.5 | Bot command not actionable | FormattedText RichTextBotCommand:539 | clickable → send command | ⬜ |
| 3.6 | Bank card number no actions | FormattedText RichTextBankCardNumber:544 | clickable → card actions | ⬜ |

## PHASE 4 — Block media/structure downgrades (placeholder/flattened today)

| # | Gap | File:line | Fix | Status |
|---|-----|-----------|-----|--------|
| 4.1 | Audio → italic "Audio" placeholder | parseForChat:539 | inline audio player view (TYPE_CUSTOM_INLINE) | ⬜ |
| 4.2 | Voice note → italic placeholder | parseForChat:545 | inline voice view | ⬜ |
| 4.3 | Embedded web → link placeholder | parseForChat:552 | WebView (PageBlockWrapView) | ⬜ |
| 4.4 | Collage → flattened to stacked media | parseForChat:522 | proper collage grid | ⬜ |
| 4.5 | Slideshow → flattened to stacked media | parseForChat:530 | slideshow pager | ⬜ |
| 4.6 | PageBlockThinking → static italic (no shimmer) | PageBlockRichText:375 | shimmer animation | ⬜ |
| 4.7 | Unknown future PageBlock → throws UnsupportedOperationException, collapses whole msg | PageBlock.java:974 | render single placeholder block instead of failing whole message | ⬜ |

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
