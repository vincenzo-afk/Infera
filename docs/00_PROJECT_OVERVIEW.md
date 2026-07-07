# 00 — Project Overview

## Project Summary
ChaosVoice is a mobile real-time voice-effects application (Android primary, iOS secondary) that transforms the user's own microphone input into distorted, "demonic," glitch-styled audio using a fully local digital signal processing (DSP) chain — no cloud processing, no root access, no ML voice conversion required.

## Purpose
To give users an entertaining, expressive way to change how their voice sounds in real time, both for standalone use (recording, playback, streaming) and during VoIP calls (WhatsApp, Discord, Telegram, game voice chat), using an acoustic loopback technique that requires no special system permissions.

## Objectives
- Deliver real-time (<150ms) voice transformation with a rich, tunable effects chain
- Work on stock, non-rooted Android devices (API 29+)
- Provide clear, honest UI so the user always knows when their voice is being altered
- Offer presets (Demon, Robot, Crushed Radio, Glitch) plus full manual control
- Remain stable across background/foreground transitions during calls

## High-Level Workflow
1. User opens the app and grants microphone + notification permissions
2. User selects a preset or configures custom effect parameters
3. User activates "Chaos Mode"
4. The app captures mic audio, processes it through the DSP chain, and plays it back through the device loudspeaker
5. If the user is on a VoIP call, the phone's own microphone naturally picks up this processed loudspeaker output and relays it into the call (acoustic loopback)
6. User deactivates Chaos Mode to return to normal voice

## Supported Platforms
- **Primary:** Android 10+ (API 29+), Flutter + Kotlin native
- **Secondary:** iOS, via Swift/AVAudioEngine (future/parallel development)
