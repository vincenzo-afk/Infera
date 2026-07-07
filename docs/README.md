# ChaosVoice — Real-Time Demonic Voice Changer

ChaosVoice is a mobile voice-changer app (Android, Flutter + Kotlin native) that transforms the user's own voice in real time into a demonic, distorted, chaotic sound during VoIP calls (WhatsApp, Discord, Telegram, game voice chat) or standalone recording/playback.

This is a **consensual, user-facing effects tool** — the user activates it on their own device to change how their own voice sounds. It is not designed to intercept, alter, or affect any other party's audio without their device also running the app.

## Project Docs Index

- `ARCHITECTURE.md` — system design, audio pipeline, service architecture
- `REQUIREMENTS.md` — functional & non-functional requirements
- `DSP_EFFECTS_SPEC.md` — full effects chain, parameters, processing order
- `PERMISSIONS.md` — required Android permissions and rationale
- `UI_SPEC.md` — screens, controls, interaction flow
- `ROADMAP.md` — build phases and priorities
- `KNOWN_ISSUES.md` — current bugs/gaps to fix
- `BUILD_GUIDE.md` — build/run instructions, SDK versions

## One-Line Pitch

Tap a button, talk, and your voice comes out sounding like a distorted demon — in real time, during calls or standalone use, no root required.

## Core User Flow

1. Open app, grant mic + notification permissions
2. Pick or tune a voice preset (Demon, Robot, Crushed Radio, Glitch, custom)
3. Toggle "Chaos Mode" ON
4. Speak into the mic — processed audio plays back through the device speaker in real time
5. If on a call, the phone's own mic picks up the processed sound and sends it to the call (acoustic loopback), so the other caller hears the effect
6. Toggle OFF to return to normal voice
