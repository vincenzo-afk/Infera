import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../chaos_controller.dart';

/// Standalone volume boost control widget.
/// Displayed inline on HomeScreen and also usable independently.
class VolumeBoostWidget extends StatelessWidget {
  const VolumeBoostWidget({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ChaosController>();
    final boost      = controller.boostLevel;
    final colors     = Theme.of(context).colorScheme;
    final isWarn     = boost >= 300;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Volume Boost',
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(fontWeight: FontWeight.w600),
            ),
            Text(
              '${boost.toStringAsFixed(0)}%',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: isWarn ? const Color(0xFFE65100) : colors.primary,
                    fontWeight: FontWeight.w700,
                  ),
            ),
          ],
        ),
        Slider(
          value: boost.clamp(100, 500),
          min: 100, max: 500, divisions: 80,
          onChanged: controller.setBoost,
        ),
        if (isWarn)
          Text(
            '⚠️ High boost levels may cause feedback or distortion. Limiter is active.',
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: const Color(0xFFE65100)),
          ),
      ],
    );
  }
}
