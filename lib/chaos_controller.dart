import 'dart:async';
import 'dart:convert';
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

  // ── Limiter ───────────────────────────────────────────────────────────────
  bool _limiterEnabled = true;
  bool get limiterEnabled => _limiterEnabled;

  // ── VPN survival service (opt-in, default off) ────────────────────────────
  bool _vpnSurvivalEnabled = false;
  bool get vpnSurvivalEnabled => _vpnSurvivalEnabled;

  // ── Error state ───────────────────────────────────────────────────────────
  String? _errorMessage;
  String? get errorMessage => _errorMessage;

  // ── Custom presets ────────────────────────────────────────────────────────
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

  /// Watchdog timer: if the service doesn't reach ACTIVE within this window
  /// after startChaosMode is called, we abort and show an error.
  Timer? _startupWatchdog;
  static const _watchdogSeconds = 12;

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
    _liveParams = _activePreset.copyWith(boostPercent: savedBoost);

    notifyListeners();
  }

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
        },
        cancelOnError: false,
      );
    } catch (e) {
      debugPrint('[ChaosController] EventChannel setup error (non-fatal): $e');
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Native event handlers
  // ─────────────────────────────────────────────────────────────────────────

  void _handleServiceStateChanged(String nativeState) {
    switch (nativeState) {
      case 'INIT':
        // Brief transient state — keep UI in initializing
        _setAppState(AppState.initializing);
        break;
      case 'ACTIVE':
        // Service is fully running — cancel the watchdog and mark as running
        _cancelWatchdog();
        _setAppState(AppState.running);
        _clearError();
        // Request battery exemption AFTER ACTIVE — non-blocking, off critical path
        _requestBatteryExemptionPostActivation();
        break;
      case 'STOPPED':
        _cancelWatchdog();
        _setAppState(AppState.stopped);
        break;
    }
  }

  /// Requests battery optimization exemption after the service is confirmed ACTIVE.
  /// This is intentionally fire-and-forget — it opens a system UI dialog that the
  /// user can dismiss without affecting the running audio session.
  void _requestBatteryExemptionPostActivation() {
    NativeAudioBridge.requestBatteryOptimizationExemption().catchError((e) {
      debugPrint('[ChaosController] Battery exemption request failed (non-fatal): $e');
      return false;
    });
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
        _cancelWatchdog();
        _setAppState(AppState.stopped);
        break;
    }
  }

  void _handleNativeError(String code, String message) {
    debugPrint('[ChaosController] Native error $code: $message');
    _cancelWatchdog();
    _errorMessage = message;
    _setAppState(AppState.failure);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Public API — called from UI widgets
  // ─────────────────────────────────────────────────────────────────────────

  /// Primary toggle: starts or stops Chaos Mode.
  Future<void> toggleChaosMode() async {
    if (isActive || _appState == AppState.paused) {
      await _stopChaosMode();
    } else if (isLoading) {
      // Allow user to cancel a stuck loading state
      await _stopChaosMode();
    } else {
      await _startChaosMode();
    }
  }

  Future<void> _startChaosMode() async {
    _setAppState(AppState.initializing);
    _clearError();

    try {
      // ── Step 1: POST_NOTIFICATIONS (Android 13+, non-blocking if denied)
      await NativeAudioBridge.requestNotificationPermission();

      // ── Step 2: RECORD_AUDIO — must be granted before FGS starts
      final micGranted = await NativeAudioBridge.requestRecordAudioPermission();
      if (!micGranted) {
        _setError('Microphone access is needed to use ChaosVoice. Tap to grant.');
        _setAppState(AppState.failure);
        return;
      }

      // ── Step 3: Start FGS + begin ACTIVE transition in one call.
      // The service does INIT notification + transitionToActive() in onStartCommand,
      // so there is no broadcast race. The service will emit INIT then ACTIVE events.
      // We set a watchdog to fail gracefully if the ACTIVE event never arrives.
      final started = await NativeAudioBridge.startChaosMode(
        _activePreset.isBuiltIn ? _activePreset.id : _activePreset.toJson(),
        boostLevel,
      );

      if (!started) {
        _setError('Failed to start ChaosVoice. Please try again.');
        _setAppState(AppState.failure);
        return;
      }

      // Start watchdog: if ACTIVE event doesn't arrive within _watchdogSeconds,
      // auto-fail with a helpful error message.
      _startWatchdog();

      // State will be updated when native sends onServiceStateChanged(ACTIVE).
      // Battery exemption is requested then, not here.

    } on PlatformException catch (e) {
      debugPrint('[ChaosController] PlatformException: ${e.code}: ${e.message}');
      _cancelWatchdog();
      _setError(_friendlyError(e.code, e.message));
      _setAppState(AppState.failure);
    } catch (e) {
      debugPrint('[ChaosController] Unexpected error: $e');
      _cancelWatchdog();
      _setError('An unexpected error occurred. Please restart the app.');
      _setAppState(AppState.failure);
    }
  }

  Future<void> _stopChaosMode() async {
    _cancelWatchdog();
    try {
      await NativeAudioBridge.stopChaosMode();
      _setAppState(AppState.stopped);
    } on PlatformException catch (e) {
      if (e.code == 'SERVICE_NOT_RUNNING') {
        _setAppState(AppState.stopped);
      } else {
        _setError(_friendlyError(e.code, e.message));
      }
    }
  }

  // ── Watchdog timer ────────────────────────────────────────────────────────

  void _startWatchdog() {
    _cancelWatchdog();
    _startupWatchdog = Timer(const Duration(seconds: _watchdogSeconds), () {
      if (_appState == AppState.initializing) {
        debugPrint('[ChaosController] Startup watchdog fired — service never reached ACTIVE');
        _setError(
          'ChaosVoice took too long to start. '
          'Try stopping and starting again. If the problem persists, '
          'check microphone permission and restart the app.',
        );
        _setAppState(AppState.failure);
        // Attempt to stop the service so it doesn't linger in INIT
        NativeAudioBridge.stopChaosMode().catchError((_) => false);
      }
    });
  }

  void _cancelWatchdog() {
    _startupWatchdog?.cancel();
    _startupWatchdog = null;
  }

  // ── Preset management ─────────────────────────────────────────────────────

  /// Selects a preset — applies immediately if active, otherwise saves for next start.
  Future<void> selectPreset(PresetModel preset) async {
    final previousPreset     = _activePreset;
    final previousLiveParams = _liveParams;

    _activePreset = preset;
    _liveParams   = preset;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('active_preset_id', preset.id);
    await prefs.setDouble('boost_level', preset.boostPercent);

    if (isActive) {
      try {
        await NativeAudioBridge.loadPreset(preset.isBuiltIn ? preset.id : preset.toJson());
        await NativeAudioBridge.updateParameter('boostPercent', preset.boostPercent);
      } on PlatformException catch (e) {
        debugPrint('[ChaosController] loadPreset error: ${e.code}');
        _setError("Couldn't load that preset — reverted to previous.");
        _activePreset = previousPreset;
        _liveParams   = previousLiveParams;
        await prefs.setString('active_preset_id', previousPreset.id);
        await prefs.setDouble('boost_level', previousLiveParams.boostPercent);
      } catch (e) {
        debugPrint('[ChaosController] loadPreset unexpected error: $e');
        _setError("Couldn't load that preset — reverted to previous.");
        _activePreset = previousPreset;
        _liveParams   = previousLiveParams;
        await prefs.setString('active_preset_id', previousPreset.id);
        await prefs.setDouble('boost_level', previousLiveParams.boostPercent);
      }
    }
    // No error shown if not active — preset is simply saved for next activation

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
    }
    notifyListeners();
  }

  /// Updates a single DSP parameter from an [EffectSlider] widget.
  Future<void> updateParameter(String stageKey, double value) async {
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
      _setError("Couldn't save preset. Please try again.");
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
    if (isActive) {
      NativeAudioBridge.loadPreset(_activePreset.isBuiltIn ? _activePreset.id : _activePreset.toJson());
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

  // ── VPN survival service ──────────────────────────────────────────────────

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

  String _friendlyError(String code, String? raw) {
    return switch (code) {
      'PERMISSION_DENIED'    => 'Microphone access is needed to use ChaosVoice. Tap to grant.',
      'AUDIO_INIT_FAILED'    => "Couldn't access the microphone. Try restarting the app.",
      'FOCUS_DENIED'         => 'Another app is using audio right now.',
      'PRESET_NOT_FOUND'     => "Couldn't load that preset — reverted to default.",
      'SERVICE_NOT_RUNNING'  => 'ChaosVoice is not currently active.',
      'SERVICE_START_FAILED' => 'Notification permission is required for ChaosVoice to run in the background.',
      'FGS_START_FAILED'     => "ChaosVoice couldn't start the background service. "
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
    _cancelWatchdog();
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
