# 33 — Deployment

## APK Distribution
- Debug/test builds distributed as standalone `.apk` files (e.g., via direct download link or internal testing channel)
- Signed with a debug or internal test keystore, clearly labeled as non-production builds

## AAB (Android App Bundle)
- Production releases built as `.aab` for Play Store submission, using the release signing keystore

## GitHub Releases
- Tagged releases (see `35_GIT_WORKFLOW.md` for tag convention) can attach the built APK for direct-download distribution outside the Play Store
- Release notes should summarize user-facing changes and reference relevant `ROADMAP.md` milestones

## Play Store Considerations
- App description and store listing should accurately represent the app as a voice-effects/entertainment tool for the user's own voice, consistent with `28_SECURITY.md` and the transparency-first design principle
- Foreground service usage and microphone permission must be clearly explained in the Play Store data-safety/permissions disclosure
- Ensure compliance with current Play Store policies on foreground services and background microphone use at time of submission (policies may change over time; check current Play Console guidance before each submission)
