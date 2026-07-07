# 40 — Glossary

**DSP (Digital Signal Processing)** — Mathematical manipulation of a digital audio signal to alter its characteristics (pitch, tone, distortion, etc.).

**PCM (Pulse-Code Modulation)** — The raw digital audio sample format used throughout this app's pipeline (16-bit, 48kHz, mono).

**AEC (Acoustic Echo Cancellation)** — A system/hardware feature that suppresses echo picked up by a microphone; can interact with (and sometimes partially suppress) the acoustic loopback method this app relies on.

**NS (Noise Suppression)** — A system/hardware feature that reduces background noise in mic input; may interact with the DSP-processed sound picked up during loopback.

**AGC (Automatic Gain Control)** — A system feature that automatically adjusts microphone input levels; can affect how loud the looped-back processed audio appears to the call app.

**MethodChannel** — Flutter's mechanism for calling native platform code (Kotlin/Swift) from Dart and receiving results back.

**Foreground Service** — An Android service type that must display a persistent notification, used here to keep real-time audio processing alive.

**Limiter** — A DSP stage that prevents a signal from exceeding a set level, used to avoid destructive clipping at high volume boost.

**Ring Modulation** — A DSP effect that multiplies the input signal by a carrier tone, producing a metallic/robotic timbre.

**Bit Crusher** — A DSP effect that reduces the bit depth of a signal, producing a lo-fi, crunchy digital artifact sound.

**Latency** — The time delay between audio input (speaking) and audio output (hearing the processed result).

**Sample Rate** — The number of audio samples captured per second (48000 Hz / 48kHz in this app).

**Acoustic Loopback** — This app's core technique: playing processed audio through the device loudspeaker so the device's own microphone picks it back up for a call, without directly injecting into another app's audio stream.

**Audio Focus** — Android's system for coordinating which app has the right to play/record audio at a given moment.
