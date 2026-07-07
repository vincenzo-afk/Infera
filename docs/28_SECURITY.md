# 28 — Security

## Permissions Philosophy
Only the minimum permissions required for local, on-device audio processing are requested (see `14_PERMISSION_MODEL.md`). No permissions related to contacts, location, storage-wide access, or other unrelated data are requested.

## Privacy
- No audio is ever transmitted off-device
- No analytics or telemetry capturing audio content is implemented
- Custom presets (parameter values only, no audio) are stored locally and never uploaded unless a future explicit "share preset" feature is added and clearly disclosed

## No Cloud Processing
All DSP processing described in `11_DSP_ENGINE.md` runs entirely on-device using local CPU. There is no server-side component in this architecture.

## No Data Collection
- No user audio recordings are persisted to disk during normal operation
- No account/login system, and therefore no personally identifiable information is collected by the app itself

## Threat Model
- **In scope:** ensuring the app cannot be used to silently/covertly alter a call without the device owner's active, visible engagement (the persistent notification and active-state banner are the primary mitigations)
- **Out of scope:** protecting against a malicious actor who has already compromised the device at the OS/root level — this app operates entirely within Android's standard permission model and does not attempt to defend against a fully compromised device
- **Design mitigation:** the transparency-first requirements (always-visible active indicator, mandatory foreground notification) are treated as security/trust features, not just UX polish — they prevent the app's core mechanism from being repurposed for covert/non-consensual use
