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
  static Stream<Map<String, dynamic>> get nativeEvents =>
      _eventChannel.receiveBroadcastStream().map(
        (e) => Map<String, dynamic>.from(e as Map),
      );

  // ── MethodChannel calls ────────────────────────────────────────────────────

  /// Requests [POST_NOTIFICATIONS] permission from the OS (Android 13+).
  /// Returns true if granted or if OS version is below Android 13.
  static Future<bool> requestNotificationPermission() async {
    final result =
        await _channel.invokeMethod<bool>('requestNotificationPermission');
    return result ?? false;
  }

  /// Requests [RECORD_AUDIO] permission from the OS.
  /// Must be granted BEFORE calling [startChaosMode] on Android 14+.
  static Future<bool> requestRecordAudioPermission() async {
    final result =
        await _channel.invokeMethod<bool>('requestRecordAudioPermission');
    return result ?? false;
  }

  /// Starts the foreground service in INIT state only (no audio).
  /// Kept for backward compatibility. The normal startup flow uses
  /// [startChaosMode] directly, which starts FGS + begins ACTIVE in one call.
  static Future<bool> startForegroundServiceOnly() async {
    final result =
        await _channel.invokeMethod<bool>('startForegroundServiceOnly');
    return result ?? false;
  }

  /// Starts the foreground service and immediately begins the ACTIVE transition.
  ///
  /// This is the primary startup method. It passes the full preset and boost
  /// in the service intent, so the service can do INIT notification + ACTIVE
  /// transition in a single onStartCommand call — no broadcast race.
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
  static Future<bool> loadPreset(dynamic preset) async {
    final result = await _channel.invokeMethod<bool>('loadPreset', {
      'preset': preset,
    });
    return result ?? false;
  }

  /// Opens the system battery optimization exemption dialog.
  /// Called AFTER ACTIVE state is confirmed — not on the startup critical path.
  static Future<bool> requestBatteryOptimizationExemption() async {
    final result =
        await _channel.invokeMethod<bool>('requestBatteryOptimizationExemption');
    return result ?? false;
  }

  /// Requests the system VPN permission dialog for [ChaosVpnService].
  static Future<bool> requestVpnPermission() async {
    final result = await _channel.invokeMethod<bool>('requestVpnPermission');
    return result ?? false;
  }

  /// Starts [ChaosVpnService] (VPN permission must be granted first).
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
