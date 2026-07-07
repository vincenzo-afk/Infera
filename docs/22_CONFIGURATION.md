# 22 — Configuration Constants

## Audio Format Constants
```
SAMPLE_RATE = 48000
BIT_DEPTH = 16
CHANNEL_COUNT = 1
BUFFER_SIZE_SAMPLES = 1920   // ~40ms at 48kHz
```

## Gain / Boost Constants
```
BOOST_MIN_PERCENT = 100
BOOST_MAX_PERCENT = 500
BOOST_DEFAULT_PERCENT = 150
BOOST_WARNING_THRESHOLD_PERCENT = 300
LIMITER_KNEE_THRESHOLD = 0.8   // normalized amplitude
LIMITER_RATIO = 4.0
```

## Delay / Echo Constants
```
DELAY_TIME_MS_DEFAULT = 25
DELAY_FEEDBACK_DEFAULT = 0.65
ECHO_PREWARM_MS = 500
```

## Pitch / Wobble Constants
```
PITCH_WOBBLE_RATE_HZ_DEFAULT = 4.0
PITCH_WOBBLE_DEPTH_DEFAULT = 0.15
```

## Reverb Constants
```
REVERB_DECAY_MS_DEFAULT = 300   // tight/harsh, not spacious
REVERB_EARLY_REFLECTION_DENSITY = 0.7
```

## Ring Modulation Constants
```
RING_MOD_CARRIER_HZ_DEFAULT = 800
```

## Bit Crusher / Sample Rate Constants
```
BIT_CRUSH_DEPTH_DEFAULT = 4
SAMPLE_RATE_REDUCTION_TARGET_HZ_DEFAULT = 8000
```

## Transient Burst Constants
```
BURST_PROBABILITY_PER_BUFFER_DEFAULT = 0.02   // 2%
BURST_DURATION_MS_RANGE = [50, 150]
```

All constants above are defaults; each is exposed as a tunable parameter per `11_DSP_ENGINE.md` and `21_SETTINGS_AND_PRESETS.md`.
