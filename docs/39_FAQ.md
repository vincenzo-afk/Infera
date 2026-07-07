# 39 — FAQ

**Q: Does this app require root access?**
No. ChaosVoice is designed to work entirely on stock, non-rooted Android devices.

**Q: How does the effect get into my call if the app can't access the call's audio directly?**
It uses an acoustic loopback: your processed voice plays through your phone's loudspeaker, and your phone's own microphone (already active for the call) picks that sound back up and sends it along.

**Q: Why do I need earphones?**
Earphones let you monitor your own voice without hearing the loud feedback loop directly in your ear while the effect plays through the speaker.

**Q: Does this work on regular phone calls (not WhatsApp/Discord/etc.)?**
No — regular GSM/cellular calls use a hardware-isolated audio path that this method cannot reach. It works on VoIP/internet-based call apps.

**Q: Why does the volume boost distort so much at high settings?**
Above roughly 300–400%, the signal naturally saturates and clips even with the limiter active — this is expected and part of the intended aggressive character at high boost levels.

**Q: Is any of my voice data sent to a server?**
No. All processing happens locally on your device. See `28_SECURITY.md`.

**Q: Why does the app need a persistent notification?**
Android requires foreground services (needed to keep real-time audio processing running) to show a notification, and ChaosVoice also uses it intentionally so you always know when your voice is being altered.

**Q: The effect isn't very audible on my call — why?**
Acoustic coupling quality varies by device. Try increasing the boost level, and ensure you're not in an unusually noisy or acoustically dampened environment.

**Q: Can I create and save my own custom effect?**
Yes — tune parameters on the Advanced Effects screen and save them as a named custom preset.

## Troubleshooting

| Issue | Likely Cause | Fix |
|---|---|---|
| No sound change during a call | Loudspeaker not selected as output | Ensure speakerphone mode is active |
| App stops working after a few minutes backgrounded | OEM battery optimization killing the service | Grant battery optimization exemption; check OEM-specific autostart settings |
| Effect sounds distorted/unclear at low boost | Device-specific acoustic coupling limitation | Increase boost level |
