# 10 — Audio Pipeline

## Full Signal Path

```
AudioRecord (mic capture)
      │
      ▼
ChaosDSP (14-stage effect chain)
      │
      ▼
Limiter (final DSP stage)
      │
      ▼
AudioTrack (playback)
      │
      ▼
Device Loudspeaker
      │
      ▼  (acoustic coupling in the room)
Device Microphone (already active, used by the call app)
      │
      ▼
VoIP Call App
```

## Audio Format

| Parameter | Value |
|---|---|
| Sample Rate | 48000 Hz |
| Bit Depth | 16-bit PCM |
| Channel Count | 1 (mono) |
| Buffer Size | 1920 samples (~40ms at 48kHz) |

## Capture Configuration
- `AudioRecord` configured with `MediaRecorder.AudioSource.MIC` (or `VOICE_COMMUNICATION` source where available for better echo/noise characteristics)
- Buffer size chosen to balance latency against underrun risk; tuned per-device if needed

## Playback Configuration
- `AudioTrack` configured with `USAGE_MEDIA` (loudspeaker-targeted, not call-routed)
- `PERFORMANCE_MODE_LOW_LATENCY` requested where supported
- Volume set according to the user's boost level, post-limiter

## Buffer Flow Notes
- Each captured buffer is processed synchronously through all 14 DSP stages before being written to `AudioTrack`
- Buffers are pre-allocated once at session start; no per-buffer heap allocation during the hot loop
- If a buffer is dropped (underrun), the DSP engine should skip gracefully rather than desync timing
