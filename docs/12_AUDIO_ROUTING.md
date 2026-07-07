# 12 — Audio Routing

## AudioManager Configuration
- Audio mode kept at `MODE_NORMAL` (not `MODE_IN_COMMUNICATION`) so output routes to the standard loudspeaker path rather than being captured into any call-specific routing
- `setSpeakerphoneOn(true)` invoked when processing starts, to ensure output is audible and positioned to couple acoustically with the microphone

## AudioTrack Routing
- `USAGE_MEDIA` content usage (not `USAGE_VOICE_COMMUNICATION`) — this is a deliberate architectural choice; see `decisions/ADR-002-Audio-Routing.md`
- Output device explicitly set to the built-in loudspeaker where the API allows (`setPreferredDevice()`)

## AudioRecord Routing
- Input source: `MediaRecorder.AudioSource.MIC`, or `VOICE_COMMUNICATION` where it yields better echo/noise handling on a given device
- No special routing needed on the input side; standard mic capture

## Routing Changes (Device Events)
- On wired headset plug/unplug: re-evaluate output routing (loudspeaker vs. headset) and warn the user if headset output would break the acoustic loopback design
- On Bluetooth connect: similarly warn, since Bluetooth audio paths introduce latency and may not route to the loudspeaker
- On screen off: processing should continue as long as the foreground service is alive

## Audio Focus Handling
- Focus requested with `AUDIOFOCUS_GAIN` at session start
- On `AUDIOFOCUS_LOSS_TRANSIENT` (e.g., a call ringtone): pause processing/output, resume automatically on focus regain
- On `AUDIOFOCUS_LOSS` (permanent, e.g., a call directly answered without app intervention): stop processing cleanly, update UI to reflect the stopped state
