# Bug Index

| Note | Mantis | Area | Status |
|---|---|---|---|
| [[B1 - Channel with giveaway post cannot be opened]] | — | Interface | fixed, device-tested OK 2026-09-06 |
| [[B2 - Long press on quote text crashes]] | #951 | Interface | root-caused from crash logs 2026-09-17 (media-caption long-press recursion), fixed c313d6886, device-tested OK 2026-09-17 |
| [[B3 - Topic with many messages shows no messages]] | — | Topics | fixed, device-tested OK 2026-09-06 |
| [[B4 - Forum caching feels wrong]] | — | Topics | fixed on `fix/giveaway-typing-topics`, untested (list refresh + cache freshness); topic history latency remains |
| [[B5 - Typing status not shown to the other side]] | — | General | fixed, device-tested OK 2026-09-06 |
| [[B6 - Comment threads lag about 30 seconds]] | — | General | fixed, device-tested OK 2026-09-06 (non-member thread polling) |
| [[B7 - Replies chat opens empty from notification]] | — | Interface | mitigated (general empty-load fallback), not yet triggered on device; missing Replies messages are server-side |
| [[B8 - Forwarded channel media shows no channel name]] | #950 | Interface | fixed 5ad39af9d (name gets priority, counters move to time pill), device-tested OK 2026-09-17 |
| [[B9 - Crash when a muted-topic notification is edited]] | #952 | Topics | fixed c979604d6, awaiting device test |
| [[B10 - Mini App from link crashes on open]] | #953 | Interface | fixed 009c7ffef, awaiting device test |

Reported by the user on 2026-09-06 as long-standing ("several months"). B1–B7 have no Mantis ids yet (create them on the Windows box); B8–B10 were filed 2026-09-17. Open items already known from the audits are in [[Known Issues & Open Items]]; promote one to a bug note when you start on it.
