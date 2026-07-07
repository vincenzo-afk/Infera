# 36 — API Reference

## Kotlin Public API

### `ChaosDSP`
```kotlin
class ChaosDSP {
    fun process(buffer: ShortArray): ShortArray
    fun setParameter(stage: Stage, value: Float)
    fun loadPreset(preset: Preset)
}

enum class Stage {
    EQ, NOISE, SATURATION_1, DELAY, SATURATION_2, RING_MOD,
    BIT_CRUSH, SAMPLE_RATE_REDUCE, PITCH_WOBBLE, VOCODER,
    TRANSIENT_BURST, HARD_CLIP, BOOST, LIMITER
}

data class Preset(
    val id: String,
    val name: String,
    val parameters: Map<Stage, Float>
)
```

### `ChaosProjectionService`
```kotlin
class ChaosProjectionService : Service() {
    enum class ServiceState { INIT, ACTIVE, STOPPED }
    fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
}
```

### `AudioFocusManager`
```kotlin
class AudioFocusManager {
    fun requestFocus(): Boolean
    fun abandonFocus()
    fun onAudioFocusChange(focusChange: Int)
}
```

## Dart Public API

### `ChaosController`
```dart
class ChaosController extends ChangeNotifier {
  bool isActive;
  Preset activePreset;
  double boostLevel;

  Future<void> toggleChaosMode();
  Future<void> selectPreset(Preset preset);
  Future<void> setBoost(double value);
}
```

### `NativeAudioBridge`
```dart
class NativeAudioBridge {
  Future<bool> startChaosMode(String presetId, double boostLevel);
  Future<bool> stopChaosMode();
  Future<bool> updateParameter(String stage, double value);
  Future<bool> loadPreset(String presetId);
}
```

### `Preset` (Dart model)
```dart
class Preset {
  final String id;
  final String name;
  final Map<String, double> parameters;
}
```

## MethodChannel Reference
See `16_METHOD_CHANNEL_PROTOCOL.md` for the complete method/argument/return/error reference.
