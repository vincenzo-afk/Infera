import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../chaos_controller.dart';

/// Full parameter control surface for every DSP stage.
/// Displayed in the Advanced Effects screen (reached from HomeScreen AppBar).
///
/// Each [_EffectSlider] sends parameter updates through [ChaosController.updateParameter]
/// which forwards to native via [NativeAudioBridge.updateParameter] (live) or persists
/// for the next session activation.
class EffectSlidersWidget extends StatelessWidget {
  const EffectSlidersWidget({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ChaosController>();
    final p          = controller.liveParams;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        _SectionHeader('STAGE 1 — Resonant EQ'),
        _EffectSlider(
          label: 'EQ Frequency', unit: 'Hz',
          value: p.eqFrequency, min: 100, max: 8000,
          stageKey: 'eqFrequency', controller: controller,
        ),
        _EffectSlider(
          label: 'EQ Q (Resonance)', unit: '',
          value: p.eqQ, min: 0.5, max: 20,
          stageKey: 'eqQ', controller: controller,
        ),

        _SectionHeader('STAGE 2 — Noise Injection'),
        _EffectSlider(
          label: 'Noise Mix', unit: '%',
          value: p.noiseMix * 100, min: 0, max: 100,
          stageKey: 'noiseMix', controller: controller,
          sendTransform: (v) => v / 100,
          displayTransform: (raw) => raw * 100,
        ),

        _SectionHeader('STAGE 3 — Saturation 1'),
        _EffectSlider(
          label: 'Drive (Gain)', unit: 'x',
          value: p.saturation1Gain, min: 0.1, max: 50,
          stageKey: 'saturation1Gain', controller: controller,
        ),

        _SectionHeader('STAGE 4 — Feedback Delay'),
        _EffectSlider(
          label: 'Delay Time', unit: 'ms',
          value: p.delayTimeMs, min: 1, max: 500,
          stageKey: 'delayTimeMs', controller: controller,
        ),
        _EffectSlider(
          label: 'Feedback', unit: '%',
          value: p.delayFeedback * 100, min: 0, max: 95,
          stageKey: 'delayFeedback', controller: controller,
          sendTransform: (v) => v / 100,
          displayTransform: (raw) => raw * 100,
        ),

        _SectionHeader('STAGE 5 — Saturation 2'),
        _EffectSlider(
          label: 'Drive 2 (Gain)', unit: 'x',
          value: p.saturation2Gain, min: 0.1, max: 20,
          stageKey: 'saturation2Gain', controller: controller,
        ),

        _SectionHeader('STAGE 6 — Ring Modulation'),
        _EffectSlider(
          label: 'Carrier Frequency', unit: 'Hz',
          value: p.ringModHz, min: 10, max: 4000,
          stageKey: 'ringModHz', controller: controller,
        ),

        _SectionHeader('STAGE 7 — Bit Crusher'),
        _EffectSlider(
          label: 'Bit Depth', unit: '-bit',
          value: p.bitCrushDepth, min: 1, max: 16,
          divisions: 15,
          stageKey: 'bitCrushDepth', controller: controller,
          displayFormat: (v) => v.toInt().toString(),
        ),

        _SectionHeader('STAGE 8 — Sample Rate Reduction'),
        _EffectSlider(
          label: 'Target Sample Rate', unit: ' Hz',
          value: p.sampleRateTargetHz, min: 1000, max: 48000,
          stageKey: 'sampleRateTargetHz', controller: controller,
        ),

        _SectionHeader('STAGE 9 — Pitch Wobble'),
        _EffectSlider(
          label: 'Wobble Rate', unit: ' Hz',
          value: p.pitchWobbleRateHz, min: 0, max: 20,
          stageKey: 'pitchWobbleRateHz', controller: controller,
        ),
        _EffectSlider(
          label: 'Wobble Depth', unit: '',
          value: p.pitchWobbleDepth, min: 0, max: 0.5,
          stageKey: 'pitchWobbleDepth', controller: controller,
        ),

        _SectionHeader('STAGE 10 — Vocoder'),
        _EffectSlider(
          label: 'Vocoder Mix', unit: '%',
          value: p.vocoderMix * 100, min: 0, max: 100,
          stageKey: 'vocoderMix', controller: controller,
          sendTransform: (v) => v / 100,
          displayTransform: (raw) => raw * 100,
        ),

        _SectionHeader('STAGE 11 — Transient Burst'),
        _EffectSlider(
          label: 'Burst Probability', unit: '%',
          value: p.burstProbability * 100, min: 0, max: 30,
          stageKey: 'burstProbability', controller: controller,
          sendTransform: (v) => v / 100,
          displayTransform: (raw) => raw * 100,
        ),

        _SectionHeader('STAGE 12 — Hard Clip'),
        _EffectSlider(
          label: 'Clip Threshold', unit: '',
          value: p.hardClipThreshold, min: 0.05, max: 1.0,
          stageKey: 'hardClipThreshold', controller: controller,
        ),

        _SectionHeader('STAGE 13 — Output Boost'),
        _EffectSlider(
          label: 'Boost Level', unit: '%',
          value: p.boostPercent, min: 100, max: 500,
          stageKey: 'boostPercent', controller: controller,
          warn: p.boostPercent >= 300,
        ),

        const SizedBox(height: 16),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: OutlinedButton(
            onPressed: () {
              context.read<ChaosController>().resetToPresetDefaults();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Reset to preset defaults')),
              );
            },
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: Theme.of(context).colorScheme.primary),
            ),
            child: const Text('Reset to Preset Defaults'),
          ),
        ),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 20, 4, 6),
      child: Text(
        title,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              letterSpacing: 1.2,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// EffectSlider — single DSP parameter row
// ─────────────────────────────────────────────────────────────────────────────

class _EffectSlider extends StatelessWidget {
  final String label;
  final String unit;
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final String stageKey;
  final ChaosController controller;
  final bool warn;

  /// If the displayed value differs from the raw preset value (e.g. % vs 0–1),
  /// provide transform functions.
  final double Function(double displayValue)? sendTransform;
  final double Function(double rawValue)? displayTransform;
  final String Function(double v)? displayFormat;

  const _EffectSlider({
    required this.label,
    required this.unit,
    required this.value,
    required this.min,
    required this.max,
    required this.stageKey,
    required this.controller,
    this.divisions,
    this.warn = false,
    this.sendTransform,
    this.displayTransform,
    this.displayFormat,
  });

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final text   = Theme.of(context).textTheme;

    // The value shown on the slider is in display units (e.g. 0–100 for %)
    final displayValue = (displayTransform != null)
        ? displayTransform!(value).clamp(min, max)
        : value.clamp(min, max);

    final labelStr = displayFormat != null
        ? displayFormat!(displayValue)
        : displayValue.toStringAsFixed(
            displayValue < 10 ? 2 : (displayValue < 100 ? 1 : 0),
          );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Container(
        padding: const EdgeInsets.fromLTRB(14, 10, 14, 4),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: warn ? const Color(0xFFE65100) : Colors.transparent,
            width: 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(label, style: text.bodyMedium?.copyWith(fontSize: 13)),
                Text(
                  '$labelStr$unit',
                  style: text.bodySmall?.copyWith(
                    color: warn ? const Color(0xFFE65100) : colors.primary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
            SliderTheme(
              data: SliderTheme.of(context).copyWith(
                activeTrackColor: warn ? const Color(0xFFE65100) : null,
                thumbColor: warn ? const Color(0xFFE65100) : null,
              ),
              child: Slider(
                value: displayValue,
                min: min, max: max,
                divisions: divisions,
                onChanged: (v) {
                  final sendValue = sendTransform != null ? sendTransform!(v) : v;
                  controller.updateParameter(stageKey, sendValue);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
