# Bug Index

| Note | Mantis | Area | Status |
|---|---|---|---|
| [[B1 - Channel with giveaway post cannot be opened]] | — | Interface | fixed, device-tested OK 2026-09-06 |
| [[B2 - Long press on quote text crashes]] | — | Interface | not reproducible on 0.28.11.1785 (device-tested 2026-09-06) |
| [[B3 - Topic with many messages shows no messages]] | — | Topics | fixed, device-tested OK 2026-09-06 |
| [[B4 - Forum caching feels wrong]] | — | Topics | fixed on `fix/giveaway-typing-topics`, untested (list refresh + cache freshness); topic history latency remains |
| [[B5 - Typing status not shown to the other side]] | — | General | fixed, device-tested OK 2026-09-06 |
| [[B6 - Comment threads lag about 30 seconds]] | — | General | fixed, device-tested OK 2026-09-06 (non-member thread polling) |
| [[B7 - Replies chat opens empty from notification]] | — | Interface | mitigated (general empty-load fallback), not yet triggered on device; missing Replies messages are server-side |

Reported by the user on 2026-09-06 as long-standing ("several months"). None have Mantis ids yet (create them on the Windows box). Open items already known from the audits are in [[Known Issues & Open Items]]; promote one to a bug note when you start on it.
