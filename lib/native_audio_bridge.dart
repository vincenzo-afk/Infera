import 'package:flutter/services.dart';

/// Dart wrapper around the [MethodChannel] "com.chaosvoice/audio".
///
/// Method names, argument shapes, and return types match
/// 16_METHOD_CHANNEL_PROTOCOL.md exactly.
///
/// The corresponding [EventChannel] "com.chaosvoice/events" delivers
/// Kotlin → Dart state/focus/error events (see [ChaosController]).
class NativeAudioBridge {
  static const MethodChannel _channel =
      MethodChannel('com.chaosvoice/audio');

  static const EventChannel _eventChannel =
      EventChannel('com.chaosvoice/events');

  // ── EventChannel stream ────────────────────────────────────────────────────

  /// Broadcast stream of native events (service-state, focus, errors).
  /// Maps from raw platform Map to a typed [Map<String, dynamic>].
  static Stream<Map<String, dynamic>> get nativeEvents =>
      _eventChannel.receiveBroadcastStream().map(
        (e) => Map<String, dynamic>.from(e as Map),
      );

  // ── MethodChannel calls ────────────────────────────────────────────────────

  /// Requests [POST_NOTIFICATIONS] permission from the OS (Android 13+).
  /// Step 1 of the corrected permission sequence (ADR-004).
  ///
  /// Returns `true` if granted or if the OS version is below Android 13
  /// (where notification permission is automatically granted).
  static Future<bool> requestNotificationPermission() async {
    final result =
        await _channel.invokeMethod<bool>('requestNotificationPermission');
    return result ?? false;
  }

  /// Starts the foreground service in INIT state (no audio yet).
  /// Step 3 of the corrected permission sequence (ADR-004).
  /// RECORD_AUDIO must already be granted before calling this.
  ///
  /// Throws [PlatformException] with code "SERVICE_START_FAILED" on failure.
  static Future<bool> startForegroundServiceOnly() async {
    final result =
        await _channel.invokeMethod<bool>('startForegroundServiceOnly');
    return result ?? false;
  }

  /// Requests [RECORD_AUDIO] permission from the OS.
  /// Step 2 of the corrected permission sequence (ADR-004).
  /// Must be requested BEFORE [startForegroundServiceOnly] on Android 14+.
  ///
  /// Returns `true` if granted.
  static Future<bool> requestRecordAudioPermission() async {
    final result =
        await _channel.invokeMethod<bool>('requestRecordAudioPermission');
    return result ?? false;
  }

  /// Transitions the service to ACTIVE — begins full capture → DSP → playback.
  ///
  /// @param preset  Built-in preset ID (String) or serialized Preset Map (Map)
  /// @param boostLevel  Gain percentage 100–500
  ///
  /// Throws [PlatformException] with codes:
  ///   "PERMISSION_DENIED", "AUDIO_INIT_FAILED", "FOCUS_DENIED"
  static Future<bool> startChaosMode(dynamic preset, double boostLevel) async {
    final result = await _channel.invokeMethod<bool>('startChaosMode', {
      'preset': preset,
      'boostLevel': boostLevel,
    });
    return result ?? false;
  }

  /// Stops the audio pipeline and clears the foreground notification.
  ///
  /// Throws [PlatformException] with code "SERVICE_NOT_RUNNING" if not active.
  static Future<bool> stopChaosMode() async {
    final result = await _channel.invokeMethod<bool>('stopChaosMode');
    return result ?? false;
  }

  /// Updates a single DSP stage parameter in real time.
  ///
  /// @param stage  Stage key string (e.g. "ringModHz", "boostPercent")
  /// @param value  New parameter value (clamped on native side)
  static Future<bool> updateParameter(String stage, double value) async {
    final result = await _channel.invokeMethod<bool>('updateParameter', {
      'stage': stage,
      'value': value,
    });
    return result ?? false;
  }

  /// Loads a complete preset by ID or Map on the native side.
  ///
  /// Throws [PlatformException] with code "PRESET_NOT_FOUND" for unknown IDs.
  static Future<bool> loadPreset(dynamic preset) async {
    final result = await _channel.invokeMethod<bool>('loadPreset', {
      'preset': preset,
    });
    return result ?? false;
  }

  /// Opens the system dialog to request battery optimization exemption.
  /// Step 4 of the permission sequence (ADR-004).
  static Future<bool> requestBatteryOptimizationExemption() async {
    final result =
        await _channel.invokeMethod<bool>('requestBatteryOptimizationExemption');
    return result ?? false;
  }

  /// Requests the system VPN permission dialog for [ChaosVpnService].
  /// Only called if the user has enabled the opt-in survival toggle.
  static Future<bool> requestVpnPermission() async {
    final result = await _channel.invokeMethod<bool>('requestVpnPermission');
    return result ?? false;
  }

  /// Starts [ChaosVpnService] (user must have granted VPN permission first).
  static Future<bool> startVpnSurvivalService() async {
    final result =
        await _channel.invokeMethod<bool>('startVpnSurvivalService');
    return result ?? false;
  }

  /// Stops [ChaosVpnService].
  static Future<bool> stopVpnSurvivalService() async {
    final result =
        await _channel.invokeMethod<bool>('stopVpnSurvivalService');
    return result ?? false;
  }
}
