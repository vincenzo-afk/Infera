import 'dart:math';
import 'models/preset_model.dart';

/// Dart-side mirror of [ChaosDSP.kt] — the full 14-stage effect chain.
///
/// Used for:
///   - iOS audio processing (via Swift translation in AudioEngineManager.swift)
///   - Non-realtime preview / waveform visualisation rendering
///
/// IMPORTANT (ADR-001): On Android, this class is NOT used for live audio.
/// [ChaosProjectionService] calls [ChaosDSP.kt] on the native audio thread.
/// Using this class on Android for the live path would violate ADR-001 and
/// reintroduce Known Issue #3 (double-processing).
class EffectEngine {
  static const int sampleRate = 48000;
  static const int bufferSize = 1920;

  // ── Parameters ────────────────────────────────────────────────────────────
  double eqFrequency      = 2600;
  double eqQ              = 5.0;
  double noiseMix         = 0.35;
  double saturation1Gain  = 12.0;
  double delayTimeMs      = 25;
  double delayFeedback    = 0.65;
  double saturation2Gain  = 4.0;
  double ringModHz        = 800;
  double bitCrushDepth    = 4;
  double sampleRateTarget = 8000;
  double pitchWobbleRate  = 4.0;
  double pitchWobbleDepth = 0.15;
  double vocoderMix       = 0.5;
  double burstProbability = 0.02;
  double hardClipThreshold = 0.30;
  double boostPercent     = 150;
  bool limiterEnabled     = true;

  // ── Stage state ───────────────────────────────────────────────────────────
  // Stage 1 — Biquad
  double _bqX1 = 0, _bqX2 = 0, _bqY1 = 0, _bqY2 = 0;
  double _bqB0 = 0, _bqB1 = 0, _bqB2 = 0, _bqA1 = 0, _bqA2 = 0;
  double _lastEqFreq = -1, _lastEqQ = -1;

  // Stage 4 — Delay
  final List<double> _delayBuf = List.filled(sampleRate * 2, 0.0);
  int _delayWriteIdx = 0;

  // Stage 6 — Ring mod
  double _ringPhase = 0;

  // Stage 8 — Sample rate reduction
  double _srHeld = 0; int _srCounter = 0;

  // Stage 9 — Pitch wobble
  double _wobPhase = 0;

  // Stage 11 — Burst
  int _burstRemaining = 0;
  final Random _rng = Random();

  // ─────────────────────────────────────────────────────────────────────────
  // Public API
  // ─────────────────────────────────────────────────────────────────────────

  /// Loads all parameters from a [PresetModel].
  void loadPreset(PresetModel preset) {
    eqFrequency      = preset.eqFrequency;
    eqQ              = preset.eqQ;
    noiseMix         = preset.noiseMix;
    saturation1Gain  = preset.saturation1Gain;
    delayTimeMs      = preset.delayTimeMs;
    delayFeedback    = preset.delayFeedback;
    saturation2Gain  = preset.saturation2Gain;
    ringModHz        = preset.ringModHz;
    bitCrushDepth    = preset.bitCrushDepth;
    sampleRateTarget = preset.sampleRateTargetHz;
    pitchWobbleRate  = preset.pitchWobbleRateHz;
    pitchWobbleDepth = preset.pitchWobbleDepth;
    vocoderMix       = preset.vocoderMix;
    burstProbability = preset.burstProbability;
    hardClipThreshold= preset.hardClipThreshold;
    boostPercent     = preset.boostPercent;
  }

  /// Updates a single parameter by stage key.
  void setParameter(String stage, double value) {
    switch (stage) {
      case 'eqFrequency':       eqFrequency      = value;
      case 'eqQ':               eqQ              = value;
      case 'noiseMix':          noiseMix         = value;
      case 'saturation1Gain':   saturation1Gain  = value;
      case 'delayTimeMs':       delayTimeMs      = value;
      case 'delayFeedback':     delayFeedback    = value;
      case 'saturation2Gain':   saturation2Gain  = value;
      case 'ringModHz':         ringModHz        = value;
      case 'bitCrushDepth':     bitCrushDepth    = value;
      case 'sampleRateTargetHz':sampleRateTarget = value;
      case 'pitchWobbleRateHz': pitchWobbleRate  = value;
      case 'pitchWobbleDepth':  pitchWobbleDepth = value;
      case 'vocoderMix':        vocoderMix       = value;
      case 'burstProbability':  burstProbability = value;
      case 'hardClipThreshold': hardClipThreshold= value;
      case 'boostPercent':      boostPercent     = value;
      case 'limiterEnabled':    limiterEnabled   = value != 0;
    }
  }

  /// Processes [buffer] through all 14 DSP stages.
  /// Returns a new [List<double>] of the same length.
  List<double> process(List<int> buffer) {
    final len = buffer.length;
    final work = List<double>.generate(len, (i) => buffer[i] / 32768.0);

    // Recompute biquad if EQ changed
    if (eqFrequency != _lastEqFreq || eqQ != _lastEqQ) _recomputeBiquad();

    // ── Stage 1: Resonant EQ ──────────────────────────────────────────────
    for (var i = 0; i < len; i++) {
      final x = work[i];
      final y = _bqB0*x + _bqB1*_bqX1 + _bqB2*_bqX2 - _bqA1*_bqY1 - _bqA2*_bqY2;
      _bqX2 = _bqX1; _bqX1 = x; _bqY2 = _bqY1; _bqY1 = y;
      work[i] = y;
    }

    // ── Stage 2: Noise Injection ─────────────────────────────────────────
    final mix = noiseMix.clamp(0, 1);
    if (mix > 0) {
      for (var i = 0; i < len; i++) {
        final noise = _rng.nextDouble() * 2 - 1;
        work[i] = (1 - mix) * work[i] + mix * noise;
      }
    }

    // ── Stage 3: Saturation 1 ────────────────────────────────────────────
    final g1 = saturation1Gain.clamp(0.1, 50);
    final t1 = _tanh(g1).abs().clamp(1e-9, double.infinity);
    for (var i = 0; i < len; i++) work[i] = _tanh(g1 * work[i]) / t1;

    // ── Stage 4: Feedback Delay ──────────────────────────────────────────
    final ds = (delayTimeMs * sampleRate / 1000).toInt().clamp(1, sampleRate * 2 - 1);
    final fb = delayFeedback.clamp(0.0, 0.95);
    for (var i = 0; i < len; i++) {
      final ri = ((_delayWriteIdx - ds) + _delayBuf.length) % _delayBuf.length;
      final delayed = _delayBuf[ri];
      final out = work[i] + fb * delayed;
      _delayBuf[_delayWriteIdx] = (work[i] + fb * delayed).clamp(-1.0, 1.0);
      _delayWriteIdx = (_delayWriteIdx + 1) % _delayBuf.length;
      work[i] = out;
    }

    // ── Stage 5: Saturation 2 ────────────────────────────────────────────
    final g2 = saturation2Gain.clamp(0.1, 20);
    final t2 = _tanh(g2).abs().clamp(1e-9, double.infinity);
    for (var i = 0; i < len; i++) work[i] = _tanh(g2 * work[i]) / t2;

    // ── Stage 6: Ring Modulation ─────────────────────────────────────────
    final rInc = 2 * pi * ringModHz / sampleRate;
    for (var i = 0; i < len; i++) {
      work[i] = work[i] * sin(_ringPhase);
      _ringPhase += rInc;
      if (_ringPhase > 2 * pi) _ringPhase -= 2 * pi;
    }

    // ── Stage 7: Bit Crusher ─────────────────────────────────────────────
    final steps = (1 << bitCrushDepth.toInt()).toDouble();
    for (var i = 0; i < len; i++) {
      work[i] = (work[i] * steps).roundToDouble() / steps;
    }

    // ── Stage 8: Sample Rate Reduction ───────────────────────────────────
    final hf = (sampleRate / sampleRateTarget.clamp(1000, sampleRate.toDouble())).toInt().clamp(1, 48);
    if (hf > 1) {
      for (var i = 0; i < len; i++) {
        if (_srCounter == 0) _srHeld = work[i];
        work[i] = _srHeld;
        _srCounter = (_srCounter + 1) % hf;
      }
    }

    // ── Stage 9: Pitch Wobble ────────────────────────────────────────────
    if (pitchWobbleDepth > 0 && pitchWobbleRate > 0) {
      final tmp = List<double>.from(work);
      final wInc = 2 * pi * pitchWobbleRate / sampleRate;
      var readPos = 0.0;
      for (var i = 0; i < len; i++) {
        final pf = 1.0 + pitchWobbleDepth * sin(_wobPhase);
        _wobPhase += wInc;
        if (_wobPhase > 2 * pi) _wobPhase -= 2 * pi;
        final ri = readPos.toInt().clamp(0, len - 1);
        final frac = readPos - ri;
        final s0 = tmp[ri];
        final s1 = ri + 1 < len ? tmp[ri + 1] : tmp[len - 1];
        work[i] = s0 + frac * (s1 - s0);
        readPos = (readPos + pf).clamp(0, (len - 1).toDouble());
      }
    }

    // ── Stage 10: Vocoder (simplified 3-band) ────────────────────────────
    // Envelope follower per band, carrier re-synthesis
    if (vocoderMix > 0) {
      // Simple single-band vocoder approximation for Dart preview
      final env = work.map((s) => s.abs()).reduce(max) * vocoderMix;
      final cInc = 2 * pi * ringModHz / sampleRate;
      var cPhase = 0.0;
      for (var i = 0; i < len; i++) {
        final carrier = sin(cPhase) * env;
        cPhase = (cPhase + cInc) % (2 * pi);
        work[i] = (1 - vocoderMix) * work[i] + vocoderMix * carrier;
      }
    }

    // ── Stage 11: Transient Burst ────────────────────────────────────────
    for (var i = 0; i < len; i++) {
      if (_burstRemaining > 0) {
        final noise = (_rng.nextDouble() * 2 - 1) * 2;
        work[i] = _tanh((work[i] + noise) * 6);
        _burstRemaining--;
      } else if (_rng.nextDouble() < burstProbability / len) {
        _burstRemaining = (50 + _rng.nextInt(100)) * sampleRate ~/ 1000;
      }
    }

    // ── Stage 12: Hard Clip ──────────────────────────────────────────────
    final thresh = hardClipThreshold.clamp(0.05, 1.0);
    for (var i = 0; i < len; i++) work[i] = work[i].clamp(-thresh, thresh);

    // ── Stage 13: Output Gain ────────────────────────────────────────────
    final gain = boostPercent.clamp(100, 500) / 100;
    for (var i = 0; i < len; i++) work[i] *= gain;

    // ── Stage 14: Limiter (soft-knee) ────────────────────────────────────
    if (limiterEnabled) {
      const knee  = 0.8;
      const ratio = 4.0;
      for (var i = 0; i < len; i++) {
        final s = work[i], a = s.abs();
        if (a > knee) work[i] = s.sign * (knee + (a - knee) / ratio);
      }
    }

    return work;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  void _recomputeBiquad() {
    final f0  = eqFrequency; final q = eqQ;
    final w0  = 2 * pi * f0 / sampleRate;
    final alpha = sin(w0) / (2 * q);
    final cosW0 = cos(w0);
    final a0  = 1 + alpha;
    _bqB0 = alpha / a0; _bqB1 = 0; _bqB2 = -alpha / a0;
    _bqA1 = -2 * cosW0 / a0; _bqA2 = (1 - alpha) / a0;
    _lastEqFreq = eqFrequency; _lastEqQ = eqQ;
  }

  static double _tanh(double x) {
    if (x > 20)  return 1.0;
    if (x < -20) return -1.0;
    final e2x = exp(2 * x);
    return (e2x - 1) / (e2x + 1);
  }

  void reset() {
    _bqX1 = _bqX2 = _bqY1 = _bqY2 = 0;
    _delayBuf.fillRange(0, _delayBuf.length, 0);
    _delayWriteIdx = 0;
    _ringPhase = 0; _wobPhase = 0;
    _srHeld = 0; _srCounter = 0;
    _burstRemaining = 0;
  }
}
