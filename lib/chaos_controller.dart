import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'models/preset_model.dart';
import 'native_audio_bridge.dart';

/// App-level state owner for ChaosVoice.
///
/// Single source of truth for UI-facing state (ADR-003, 17_STATE_MANAGEMENT.md).
/// Uses [ChangeNotifier] so widgets can listen via [Provider.of] / [Consumer].
///
/// Listens to native events from [NativeAudioBridge.nativeEvents] and maps
/// native service states to [AppState] transitions.
class ChaosController extends ChangeNotifier {
  // ── App state ──────────────────────────────────────────────────────────────

  /// Top-level app state per 17_STATE_MANAGEMENT.md.
  AppState _appState = AppState.idle;
  AppState get appState => _appState;

  bool get isActive    => _appState == AppState.running;
  bool get isLoading   => _appState == AppState.initializing;

  // ── Current preset & parameters ───────────────────────────────────────────

  PresetModel _activePreset = PresetModel.demon;
  PresetModel get activePreset => _activePreset;

  /// Live DSP parameters (may differ from [_activePreset] if user adjusted sliders).
  PresetModel _liveParams = PresetModel.demon;
  PresetModel get liveParams => _liveParams;

  // ── Volume boost ─────────────────────────────────────────────────────────
  double get boostLevel => _liveParams.boostPercent;

  // ── Limiter (settings, 21_SETTINGS_AND_PRESETS.md) ───────────────────────
  bool _limiterEnabled = true;
  bool get limiterEnabled => _limiterEnabled;

  // ── VPN survival service (opt-in, default off) ────────────────────────────
  bool _vpnSurvivalEnabled = false;
  bool get vpnSurvivalEnabled => _vpnSurvivalEnabled;

  // ── Error state ───────────────────────────────────────────────────────────
  String? _errorMessage;
  String? get errorMessage => _errorMessage;

  // ── Custom presets (23_STORAGE_MODEL.md) ──────────────────────────────────
  List<PresetModel> _customPresets = [];
  List<PresetModel> get customPresets => _customPresets;

  List<PresetModel> get allPresets => [
        ...PresetModel.allBuiltIn,
        ..._customPresets,
      ];

  // ── Internal ──────────────────────────────────────────────────────────────
  StreamSubscription<Map<String, dynamic>>? _nativeEventSub;
  bool _onboardingDone = false;
  bool get onboardingDone => _onboardingDone;

  // ─────────────────────────────────────────────────────────────────────────
  // Initialisation
  // ─────────────────────────────────────────────────────────────────────────

  ChaosController() {
    _init();
  }

  Future<void> _init() async {
    await _loadPersistedSettings();
    _subscribeToNativeEvents();
  }

  Future<void> _loadPersistedSettings() async {
    final prefs = await SharedPreferences.getInstance();
    _onboardingDone = prefs.getBool('onboarding_done') ?? false;
    _limiterEnabled = prefs.getBool('limiter_enabled') ?? true;
    _vpnSurvivalEnabled = prefs.getBool('vpn_survival_enabled') ?? false;

    // Load custom presets from JSON storage first to resolve custom active presets
    final rawCustom = prefs.getStringList('custom_presets') ?? [];
    _customPresets = rawCustom.map((s) {
      try {
        return PresetModel.fromJson(jsonDecode(s) as Map<String, dynamic>);
      } catch (_) {
        return null;
      }
    }).whereType<PresetModel>().toList();

    final lastPresetId = prefs.getString('active_preset_id') ?? 'demon';
    _activePreset = allPresets.firstWhere((p) => p.id == lastPresetId, orElse: () => PresetModel.demon);
    
    final savedBoost = prefs.getDouble('boost_level') ?? _activePreset.boostPercent;
    _liveParams   = _activePreset.copyWith(boostPercent: savedBoost);

    notifyListeners();
  }

  /// Subscribes to the native EventChannel for Kotlin → Dart events.
  /// Wrapped in try/catch so a [MissingPluginException] during the first-run
  /// native setup doesn't propagate into unrelated async call stacks.
  void _subscribeToNativeEvents() {
    try {
      _nativeEventSub = NativeAudioBridge.nativeEvents.listen(
        (event) {
          final type = event['type'] as String?;
          switch (type) {
            case 'onServiceStateChanged':
              _handleServiceStateChanged(event['state'] as String? ?? 'STOPPED');
              break;
            case 'onAudioFocusChanged':
              _handleFocusChanged(event['focusState'] as String? ?? 'LOST');
              break;
            case 'onError':
              _handleNativeError(
                event['code'] as String? ?? 'UNKNOWN',
                event['message'] as String? ?? 'An error occurred.',
              );
              break;
          }
        },
        onError: (e) {
          debugPrint('[ChaosController] EventChannel error: $e');
          // Do NOT call _setError here on first-run channel setup failures —
          // the native side may not be ready until the service is started.
        },
        cancelOnError: false,
      );
    } catch (e) {
      // MissingPluginException can be thrown synchronously if the channel
      // is not yet registered (e.g., before MainActivity.onCreate completes).
      debugPrint('[ChaosController] EventChannel setup error (non-fatal): $e');
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Native event handlers
  // ─────────────────────────────────────────────────────────────────────────

  void _handleServiceStateChanged(String nativeState) {
    switch (nativeState) {
      case 'INIT':
        _setAppState(AppState.initializing);
        break;
      case 'ACTIVE':
        _setAppState(AppState.running);
        _clearError();
        break;
      case 'STOPPED':
        _setAppState(AppState.stopped);
        break;
    }
  }

  void _handleFocusChanged(String focusState) {
    switch (focusState) {
      case 'GAINED':
        if (_appState == AppState.paused) _setAppState(AppState.running);
        break;
      case 'LOST_TRANSIENT':
        if (_appState == AppState.running) _setAppState(AppState.paused);
        break;
      case 'LOST':
        _setAppState(AppState.stopped);
        break;
    }
  }

  void _handleNativeError(String code, String message) {
    debugPrint('[ChaosController] Native error $code: $message');
    _errorMessage = message;
    _setAppState(AppState.failure);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Public API — called from UI widgets
  // ─────────────────────────────────────────────────────────────────────────

  /// Primary toggle: starts or stops Chaos Mode.
  /// Implements the corrected permission sequence from ADR-004/14_PERMISSION_MODEL.md.
  Future<void> toggleChaosMode() async {
    if (isLoading) return;  // Prevent double-taps during initialisation

    if (isActive || _appState == AppState.paused) {
      await _stopChaosMode();
    } else {
      await _startChaosMode();
    }
  }

  Future<void> _startChaosMode() async {
    _setAppState(AppState.initializing);
    _clearError();

    try {
      // ── Step 1: Request POST_NOTIFICATIONS (Android 13+)
      // Handled natively — returns true immediately on pre-Android 13 devices.
      // Does NOT block startup if denied; the FGS notification requirement will
      // surface a clearer error downstream if POST_NOTIFICATIONS is required.
      await NativeAudioBridge.requestNotificationPermission();

      // ── Step 2: Request RECORD_AUDIO permission
      // Bug 4 fix: mic permission MUST be granted before starting the foreground
      // service (step 3). On Android 14, startForeground() on a microphone-typed
      // FGS throws SecurityException if RECORD_AUDIO is not yet granted — this was
      // the root cause of the "crashes to home screen" bug. See ADR-004.
      final micGranted = await NativeAudioBridge.requestRecordAudioPermission();
      if (!micGranted) {
        _setError('Microphone access is needed to use ChaosVoice. Tap to grant.');
        _setAppState(AppState.failure);
        return;
      }

      // ── Step 3: Start foreground service in INIT state (ADR-004)
      // RECORD_AUDIO is now guaranteed granted, so startForeground() with
      // foregroundServiceType=microphone will succeed on Android 14.
      final serviceStarted = await NativeAudioBridge.startForegroundServiceOnly();
      if (!serviceStarted) {
        _setError('Notification permission is required for ChaosVoice to run in the background.');
        _setAppState(AppState.failure);
        return;
      }

      // ── Step 4: Battery optimization exemption
      await NativeAudioBridge.requestBatteryOptimizationExemption();

      // ── Step 5: Transition to ACTIVE
      final started = await NativeAudioBridge.startChaosMode(
        _activePreset.isBuiltIn ? _activePreset.id : _activePreset.toJson(),
        boostLevel,
      );
      if (!started) {
        _setError('Failed to start ChaosVoice. Please try again.');
        _setAppState(AppState.failure);
      }
      // State will be updated via onServiceStateChanged event from native side

    } on PlatformException catch (e) {
      debugPrint('[ChaosController] PlatformException: ${e.code}: ${e.message}');
      _setError(_friendlyError(e.code, e.message));
      _setAppState(AppState.failure);
    } catch (e) {
      debugPrint('[ChaosController] Unexpected error: $e');
      _setError('An unexpected error occurred. Please restart the app.');
      _setAppState(AppState.failure);
    }
  }

  Future<void> _stopChaosMode() async {
    try {
      await NativeAudioBridge.stopChaosMode();
      _setAppState(AppState.stopped);
    } on PlatformException catch (e) {
      // SERVICE_NOT_RUNNING is non-fatal
      if (e.code == 'SERVICE_NOT_RUNNING') {
        _setAppState(AppState.stopped);
      } else {
        _setError(_friendlyError(e.code, e.message));
      }
    }
  }

  /// Selects a preset — applies immediately if active, otherwise saves for next start.
  ///
  /// Bug 8 partial fix: on any exception during the live update, BOTH [_activePreset]
  /// and [_liveParams] are reverted to [PresetModel.demon] to keep Dart state consistent
  /// with what the native side will have fallen back to. The two sequential native calls
  /// (loadPreset then updateParameter) have no atomic guarantee, so a mid-sequence
  /// failure could leave Dart and Kotlin in different states — this revert minimises drift.
  Future<void> selectPreset(PresetModel preset) async {
    final previousPreset = _activePreset;
    final previousLiveParams = _liveParams;

    _activePreset = preset;
    _liveParams   = preset;

    // Persist last selection
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('active_preset_id', preset.id);
    await prefs.setDouble('boost_level', preset.boostPercent);

    if (isActive) {
      try {
        await NativeAudioBridge.loadPreset(preset.isBuiltIn ? preset.id : preset.toJson());
        // Also update boost since presets have their own default boost
        await NativeAudioBridge.updateParameter('boostPercent', preset.boostPercent);
      } on PlatformException catch (e) {
        debugPrint('[ChaosController] loadPreset error: ${e.code}');
        _setError('Couldn\'t load that preset — reverted to default.');
        _activePreset = previousPreset;
        _liveParams   = previousLiveParams;
        // Restore persisted values to match the revert
        await prefs.setString('active_preset_id', previousPreset.id);
        await prefs.setDouble('boost_level', previousLiveParams.boostPercent);
      } catch (e) {
        debugPrint('[ChaosController] loadPreset unexpected error: $e');
        _setError('Couldn\'t load that preset — reverted to default.');
        _activePreset = previousPreset;
        _liveParams   = previousLiveParams;
        await prefs.setString('active_preset_id', previousPreset.id);
        await prefs.setDouble('boost_level', previousLiveParams.boostPercent);
      }
    } else {
      _setError('Effects will apply once you activate Chaos Mode.');
    }

    notifyListeners();
  }

  /// Sets the output boost level and forwards to native if active.
  Future<void> setBoost(double value) async {
    final clamped = value.clamp(100.0, 500.0);
    _liveParams = _liveParams.copyWith(boostPercent: clamped);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble('boost_level', clamped);

    if (isActive) {
      await NativeAudioBridge.updateParameter('boostPercent', clamped);
    } else {
      _setError('Effects will apply once you activate Chaos Mode.');
    }
    notifyListeners();
  }

  /// Updates a single DSP parameter from an [EffectSlider] widget.
  Future<void> updateParameter(String stageKey, double value) async {
    // Update live params in state (for UI reflection)
    _liveParams = _applyParam(_liveParams, stageKey, value);
    
    if (stageKey == 'boostPercent') {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setDouble('boost_level', value);
    }

    if (isActive) {
      try {
        await NativeAudioBridge.updateParameter(stageKey, value);
      } on PlatformException catch (e) {
        debugPrint('[ChaosController] updateParameter error: ${e.code}');
      }
    } else {
      _setError('Effects will apply once you activate Chaos Mode.');
    }
    notifyListeners();
  }

  /// Saves current [_liveParams] as a named custom preset.
  Future<void> savePreset(String name) async {
    final newPreset = _liveParams.copyWith(
      id: 'custom-${DateTime.now().millisecondsSinceEpoch}',
      name: name,
      isBuiltIn: false,
    );

    _customPresets.add(newPreset);

    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = _customPresets.map((p) => jsonEncode(p.toJson())).toList();
      await prefs.setStringList('custom_presets', raw);
    } catch (e) {
      // Storage write failure — preserve in-session state but show error
      _setError('Couldn\'t save preset. Please try again.');
      _customPresets.removeLast();
    }
    notifyListeners();
  }

  /// Deletes a custom preset by id.
  Future<void> deletePreset(String id) async {
    _customPresets.removeWhere((p) => p.id == id);
    final prefs = await SharedPreferences.getInstance();
    final raw = _customPresets.map((p) => jsonEncode(p.toJson())).toList();
    await prefs.setStringList('custom_presets', raw);
    notifyListeners();
  }

  /// Resets [_liveParams] back to the active preset defaults.
  void resetToPresetDefaults() {
    _liveParams = _activePreset;
    notifyListeners();
    // If active, reload preset on native side
    if (isActive) {
      NativeAudioBridge.loadPreset(_activePreset.isBuiltIn ? _activePreset.id : _activePreset.toJson());
    } else {
      _setError('Effects will apply once you activate Chaos Mode.');
    }
  }

  // ── Limiter toggle ────────────────────────────────────────────────────────

  Future<void> setLimiterEnabled(bool enabled) async {
    _limiterEnabled = enabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('limiter_enabled', enabled);

    if (isActive) {
      await NativeAudioBridge.updateParameter('limiterEnabled', enabled ? 1.0 : 0.0);
    }
    notifyListeners();
  }

  // ── VPN survival service (opt-in) ─────────────────────────────────────────

  Future<void> setVpnSurvivalEnabled(bool enabled) async {
    if (enabled) {
      final granted = await NativeAudioBridge.requestVpnPermission();
      if (!granted) {
        _setError('VPN permission was not granted — Enhanced Background Mode remains off.');
        return;
      }
      await NativeAudioBridge.startVpnSurvivalService();
    } else {
      await NativeAudioBridge.stopVpnSurvivalService();
    }

    _vpnSurvivalEnabled = enabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('vpn_survival_enabled', enabled);
    notifyListeners();
  }

  // ── Onboarding ────────────────────────────────────────────────────────────

  Future<void> markOnboardingDone() async {
    _onboardingDone = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_done', true);
    notifyListeners();
  }

  // ── Error management ──────────────────────────────────────────────────────

  void clearError() {
    _errorMessage = null;
    if (_appState == AppState.failure) _setAppState(AppState.stopped);
    notifyListeners();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Internal helpers
  // ─────────────────────────────────────────────────────────────────────────

  void _setAppState(AppState newState) {
    if (_appState == newState) return;
    _appState = newState;
    notifyListeners();
  }

  void _setError(String message) {
    _errorMessage = message;
    notifyListeners();
  }

  void _clearError() {
    _errorMessage = null;
  }

  /// Maps error codes (16_METHOD_CHANNEL_PROTOCOL.md) to user-facing strings (25_ERROR_HANDLING.md).
  String _friendlyError(String code, String? raw) {
    return switch (code) {
      'PERMISSION_DENIED'    => 'Microphone access is needed to use ChaosVoice. Tap to grant.',
      'AUDIO_INIT_FAILED'    => 'Couldn\'t access the microphone. Try restarting the app.',
      'FOCUS_DENIED'         => 'Another app is using audio right now.',
      'PRESET_NOT_FOUND'     => 'Couldn\'t load that preset — reverted to default.',
      'SERVICE_NOT_RUNNING'  => 'ChaosVoice is not currently active.',
      'SERVICE_START_FAILED' => 'Notification permission is required for ChaosVoice to run in the background.',
      // Bug 1/2 fix: FGS_START_FAILED is broadcast from transitionToInit() when
      // startForeground() throws (e.g., mic permission not granted, OEM restriction).
      'FGS_START_FAILED'     => 'ChaosVoice couldn\'t start the background service. '
                                'Ensure microphone permission is granted and try again.',
      _                      => raw ?? 'An unexpected error occurred. Please restart the app.',
    };
  }

  /// Applies a named parameter update to a [PresetModel] (UI-side reflection).
  PresetModel _applyParam(PresetModel p, String key, double v) {
    return switch (key) {
      'eqFrequency'       => p.copyWith(eqFrequency: v),
      'eqQ'               => p.copyWith(eqQ: v),
      'noiseMix'          => p.copyWith(noiseMix: v),
      'saturation1Gain'   => p.copyWith(saturation1Gain: v),
      'delayTimeMs'       => p.copyWith(delayTimeMs: v),
      'delayFeedback'     => p.copyWith(delayFeedback: v),
      'saturation2Gain'   => p.copyWith(saturation2Gain: v),
      'ringModHz'         => p.copyWith(ringModHz: v),
      'bitCrushDepth'     => p.copyWith(bitCrushDepth: v),
      'sampleRateTargetHz'=> p.copyWith(sampleRateTargetHz: v),
      'pitchWobbleRateHz' => p.copyWith(pitchWobbleRateHz: v),
      'pitchWobbleDepth'  => p.copyWith(pitchWobbleDepth: v),
      'vocoderMix'        => p.copyWith(vocoderMix: v),
      'burstProbability'  => p.copyWith(burstProbability: v),
      'hardClipThreshold' => p.copyWith(hardClipThreshold: v),
      'boostPercent'      => p.copyWith(boostPercent: v),
      _                   => p,
    };
  }

  @override
  void dispose() {
    _nativeEventSub?.cancel();
    super.dispose();
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// AppState enum (17_STATE_MANAGEMENT.md)
// ─────────────────────────────────────────────────────────────────────────────

enum AppState {
  /// App launched; Chaos Mode never activated this session.
  idle,

  /// Permissions/service startup in progress.
  initializing,

  /// Full audio pipeline active, processed output playing.
  running,

  /// Temporarily suspended due to audio focus loss (transient).
  paused,

  /// User deactivated, or session ended cleanly.
  stopped,

  /// Unrecoverable error — permission revoked, audio init failure, etc.
  failure,
}
