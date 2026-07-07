# ADR-001 — Kotlin as the DSP Implementation Language for Android

## Status
Accepted

## Context
The real-time audio processing chain requires low latency and consistent performance. The app is built with Flutter, which could theoretically run DSP in Dart, but Dart's execution model and the overhead of crossing the Flutter/native boundary per-buffer make it unsuitable for the live audio-thread hot path.

## Decision
The live Android audio processing path (`ChaosDSP.kt`) is implemented natively in Kotlin, running entirely within the native `ChaosProjectionService`, with no per-buffer MethodChannel round-trip. The Dart implementation (`effect_engine.dart`) is retained as a mirror for iOS (via a native Swift translation, `AudioEngineManager.swift`) and for any non-realtime preview/rendering use cases.

## Consequences
- Requires maintaining effect-chain logic in two languages (Kotlin and Dart/Swift), with a documented obligation to keep them behaviorally equivalent (see `11_DSP_ENGINE.md`)
- Achieves the necessary low-latency, allocation-free real-time performance on Android
