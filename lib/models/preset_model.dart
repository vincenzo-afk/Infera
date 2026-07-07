/// Dart-side Preset model — mirrors the Kotlin [Preset] data class.
///
/// Used for UI display, local storage (23_STORAGE_MODEL.md), and the
/// iOS/preview DSP path via [EffectEngine].
class PresetModel {
  final String id;
  final String name;
  final bool isBuiltIn;

  // Stage 1 — Resonant EQ
  final double eqFrequency;
  final double eqQ;

  // Stage 2 — Noise Injection
  final double noiseMix;

  // Stage 3 — Saturation 1
  final double saturation1Gain;

  // Stage 4 — Feedback Delay
  final double delayTimeMs;
  final double delayFeedback;

  // Stage 5 — Saturation 2
  final double saturation2Gain;

  // Stage 6 — Ring Modulation
  final double ringModHz;

  // Stage 7 — Bit Crusher
  final double bitCrushDepth;

  // Stage 8 — Sample Rate Reduction
  final double sampleRateTargetHz;

  // Stage 9 — Pitch Wobble
  final double pitchWobbleRateHz;
  final double pitchWobbleDepth;

  // Stage 10 — Vocoder
  final double vocoderMix;

  // Stage 11 — Transient Burst
  final double burstProbability;

  // Stage 12 — Hard Clip
  final double hardClipThreshold;

  // Stage 13 — Output Boost
  final double boostPercent;

  const PresetModel({
    required this.id,
    required this.name,
    this.isBuiltIn = true,
    this.eqFrequency      = 2600,
    this.eqQ              = 5.0,
    this.noiseMix         = 0.35,
    this.saturation1Gain  = 12.0,
    this.delayTimeMs      = 25,
    this.delayFeedback    = 0.65,
    this.saturation2Gain  = 4.0,
    this.ringModHz        = 800,
    this.bitCrushDepth    = 4,
    this.sampleRateTargetHz = 8000,
    this.pitchWobbleRateHz  = 4.0,
    this.pitchWobbleDepth   = 0.15,
    this.vocoderMix       = 0.5,
    this.burstProbability = 0.02,
    this.hardClipThreshold = 0.30,
    this.boostPercent     = 150,
  });

  // ── Built-in presets (21_SETTINGS_AND_PRESETS.md, 11_DSP_ENGINE.md) ──────

  static const demon = PresetModel(
    id: 'demon', name: 'Demon',
    eqFrequency: 1500, eqQ: 6.0,
    noiseMix: 0.20, saturation1Gain: 20.0,
    delayTimeMs: 25, delayFeedback: 0.75,
    saturation2Gain: 8.0, ringModHz: 500,
    bitCrushDepth: 4, sampleRateTargetHz: 8000,
    pitchWobbleRateHz: 1.5, pitchWobbleDepth: 0.30,
    vocoderMix: 0.30, burstProbability: 0.02,
    hardClipThreshold: 0.30, boostPercent: 200,
  );

  static const robot = PresetModel(
    id: 'robot', name: 'Robot',
    eqFrequency: 2600, eqQ: 5.0,
    noiseMix: 0.10, saturation1Gain: 3.0,
    delayTimeMs: 10, delayFeedback: 0.30,
    saturation2Gain: 1.5, ringModHz: 900,
    bitCrushDepth: 8, sampleRateTargetHz: 16000,
    pitchWobbleRateHz: 0, pitchWobbleDepth: 0,
    vocoderMix: 0.80, burstProbability: 0.01,
    hardClipThreshold: 0.50, boostPercent: 150,
  );

  static const crushedRadio = PresetModel(
    id: 'crushed_radio', name: 'Crushed Radio',
    eqFrequency: 2000, eqQ: 3.0,
    noiseMix: 0.50, saturation1Gain: 2.0,
    delayTimeMs: 15, delayFeedback: 0.40,
    saturation2Gain: 1.5, ringModHz: 400,
    bitCrushDepth: 3, sampleRateTargetHz: 6000,
    pitchWobbleRateHz: 0, pitchWobbleDepth: 0,
    vocoderMix: 0.10, burstProbability: 0.01,
    hardClipThreshold: 0.40, boostPercent: 150,
  );

  static const glitch = PresetModel(
    id: 'glitch', name: 'Glitch',
    eqFrequency: 3000, eqQ: 4.0,
    noiseMix: 0.40, saturation1Gain: 8.0,
    delayTimeMs: 20, delayFeedback: 0.50,
    saturation2Gain: 4.0, ringModHz: 600,
    bitCrushDepth: 3, sampleRateTargetHz: 8000,
    pitchWobbleRateHz: 8.0, pitchWobbleDepth: 0.20,
    vocoderMix: 0.20, burstProbability: 0.10,
    hardClipThreshold: 0.25, boostPercent: 175,
  );

  /// All built-in presets in display order.
  static const List<PresetModel> allBuiltIn = [demon, robot, crushedRadio, glitch];

  /// Fallback to [demon] on unknown/corrupted id (25_ERROR_HANDLING.md).
  static PresetModel builtInById(String id) =>
      allBuiltIn.firstWhere((p) => p.id == id, orElse: () => demon);

  // ── Serialisation (23_STORAGE_MODEL.md JSON format) ──────────────────────

  factory PresetModel.fromJson(Map<String, dynamic> json) {
    return PresetModel(
      id:                  json['id']                   as String,
      name:                json['name']                 as String,
      isBuiltIn:           (json['isBuiltIn'] as bool?) ?? false,
      eqFrequency:         (json['eqFrequency']         as num?)?.toDouble() ?? 2600,
      eqQ:                 (json['eqQ']                 as num?)?.toDouble() ?? 5.0,
      noiseMix:            (json['noiseMix']             as num?)?.toDouble() ?? 0.35,
      saturation1Gain:     (json['saturation1Gain']     as num?)?.toDouble() ?? 12.0,
      delayTimeMs:         (json['delayTimeMs']         as num?)?.toDouble() ?? 25,
      delayFeedback:       (json['delayFeedback']       as num?)?.toDouble() ?? 0.65,
      saturation2Gain:     (json['saturation2Gain']     as num?)?.toDouble() ?? 4.0,
      ringModHz:           (json['ringModHz']           as num?)?.toDouble() ?? 800,
      bitCrushDepth:       (json['bitCrushDepth']       as num?)?.toDouble() ?? 4,
      sampleRateTargetHz:  (json['sampleRateTargetHz']  as num?)?.toDouble() ?? 8000,
      pitchWobbleRateHz:   (json['pitchWobbleRateHz']   as num?)?.toDouble() ?? 4.0,
      pitchWobbleDepth:    (json['pitchWobbleDepth']    as num?)?.toDouble() ?? 0.15,
      vocoderMix:          (json['vocoderMix']          as num?)?.toDouble() ?? 0.5,
      burstProbability:    (json['burstProbability']    as num?)?.toDouble() ?? 0.02,
      hardClipThreshold:   (json['hardClipThreshold']   as num?)?.toDouble() ?? 0.30,
      boostPercent:        (json['boostPercent']        as num?)?.toDouble() ?? 150,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id, 'name': name, 'isBuiltIn': isBuiltIn,
    'eqFrequency': eqFrequency, 'eqQ': eqQ,
    'noiseMix': noiseMix, 'saturation1Gain': saturation1Gain,
    'delayTimeMs': delayTimeMs, 'delayFeedback': delayFeedback,
    'saturation2Gain': saturation2Gain, 'ringModHz': ringModHz,
    'bitCrushDepth': bitCrushDepth, 'sampleRateTargetHz': sampleRateTargetHz,
    'pitchWobbleRateHz': pitchWobbleRateHz, 'pitchWobbleDepth': pitchWobbleDepth,
    'vocoderMix': vocoderMix, 'burstProbability': burstProbability,
    'hardClipThreshold': hardClipThreshold, 'boostPercent': boostPercent,
  };

  PresetModel copyWith({
    String? id, String? name, bool? isBuiltIn,
    double? eqFrequency, double? eqQ, double? noiseMix,
    double? saturation1Gain, double? delayTimeMs, double? delayFeedback,
    double? saturation2Gain, double? ringModHz, double? bitCrushDepth,
    double? sampleRateTargetHz, double? pitchWobbleRateHz, double? pitchWobbleDepth,
    double? vocoderMix, double? burstProbability, double? hardClipThreshold,
    double? boostPercent,
  }) {
    return PresetModel(
      id: id ?? this.id,
      name: name ?? this.name,
      isBuiltIn: isBuiltIn ?? this.isBuiltIn,
      eqFrequency: eqFrequency ?? this.eqFrequency,
      eqQ: eqQ ?? this.eqQ,
      noiseMix: noiseMix ?? this.noiseMix,
      saturation1Gain: saturation1Gain ?? this.saturation1Gain,
      delayTimeMs: delayTimeMs ?? this.delayTimeMs,
      delayFeedback: delayFeedback ?? this.delayFeedback,
      saturation2Gain: saturation2Gain ?? this.saturation2Gain,
      ringModHz: ringModHz ?? this.ringModHz,
      bitCrushDepth: bitCrushDepth ?? this.bitCrushDepth,
      sampleRateTargetHz: sampleRateTargetHz ?? this.sampleRateTargetHz,
      pitchWobbleRateHz: pitchWobbleRateHz ?? this.pitchWobbleRateHz,
      pitchWobbleDepth: pitchWobbleDepth ?? this.pitchWobbleDepth,
      vocoderMix: vocoderMix ?? this.vocoderMix,
      burstProbability: burstProbability ?? this.burstProbability,
      hardClipThreshold: hardClipThreshold ?? this.hardClipThreshold,
      boostPercent: boostPercent ?? this.boostPercent,
    );
  }
}
