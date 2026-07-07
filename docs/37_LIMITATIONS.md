# 37 — Limitations

## Android Restrictions
- No app can override another app's live microphone stream without root — this is why ChaosVoice uses the acoustic loopback method rather than direct injection
- Foreground service and mic-related permission requirements grow stricter with each Android version; the app must be updated as new API levels introduce new constraints

## Known Hardware Issues
- Acoustic coupling quality (loudspeaker → mic) varies significantly across devices; some phones will produce a much clearer/louder effect than others at the same boost setting
- Devices with aggressive built-in echo cancellation on the call app's mic input may partially suppress the looped-back processed audio, requiring higher boost to compensate

## Performance Limits
- Extremely high boost settings (near 500%) combined with an aggressive preset (e.g., Glitch) may approach the CPU budget on lower-end devices; performance should be validated on minimum-spec target hardware
- Does not work on regular GSM/cellular phone calls — VoIP apps only, due to how cellular call audio hardware paths are isolated from app-level audio output

## Explicit Non-Capabilities
- Does not perform true system-wide virtual microphone replacement without root
- Does not use machine-learning voice conversion/cloning
- Does not process or alter another person's voice on their own device — it only affects the voice of the person actively running the app
