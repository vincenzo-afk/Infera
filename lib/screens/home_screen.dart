import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../chaos_controller.dart';
import '../models/preset_model.dart';
import '../widgets/effect_sliders_widget.dart';
import '../widgets/volume_boost_widget.dart';

/// Primary interaction surface for ChaosVoice.
///
/// Layout (18_UI_SPECIFICATION.md, 20_SCREEN_FLOW.md):
///   - [ActiveStateBanner] — always visible at top when running/paused
///   - Horizontal [PresetCard] picker
///   - Quick [BoostSlider] (compact)
///   - Large circular [ChaosToggleButton]
///   - Settings gear → Advanced Effects screen
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with TickerProviderStateMixin {
  late AnimationController _pulseCtrl;
  late Animation<double>   _pulseAnim;

  @override
  void initState() {
    super.initState();
    // Subtle pulse animation for the active-state indicator
    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);
    _pulseAnim = Tween<double>(begin: 0.85, end: 1.0).animate(
      CurvedAnimation(parent: _pulseCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseCtrl.dispose();
    super.dispose();
  }

  void _openAdvancedEffects(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const AdvancedEffectsScreen(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ChaosController>();
    final colors     = Theme.of(context).colorScheme;
    final text       = Theme.of(context).textTheme;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        backgroundColor: colors.background,
        elevation: 0,
        title: Text(
          'CHAOSVOICE',
          style: text.titleLarge?.copyWith(
            fontSize: 17,
            letterSpacing: 3,
            color: colors.primary,
          ),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.tune_rounded),
            tooltip: 'Advanced Effects',
            onPressed: () => _openAdvancedEffects(context),
          ),
          IconButton(
            icon: const Icon(Icons.settings_rounded),
            tooltip: 'Settings',
            onPressed: () => _showSettings(context, controller),
          ),
        ],
      ),
      body: Column(
        children: [
          // ── Active-state banner (always above-the-fold when running) ────
          _ActiveStateBanner(
            appState: controller.appState,
            pulseAnim: _pulseAnim,
          ),

          // ── Error message ────────────────────────────────────────────────
          if (controller.errorMessage != null)
            _ErrorBanner(
              message: controller.errorMessage!,
              onDismiss: controller.clearError,
            ),

          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(0, 8, 0, 32),
              children: [
                // ── Preset picker ──────────────────────────────────────────
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
                  child: Text('Preset', style: text.bodySmall?.copyWith(letterSpacing: 1.5)),
                ),
                SizedBox(
                  height: 90,
                  child: ListView.builder(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: controller.allPresets.length,
                    itemBuilder: (_, i) {
                      final p = controller.allPresets[i];
                      return Padding(
                        padding: const EdgeInsets.only(right: 10),
                        child: _PresetCard(
                          preset: p,
                          isSelected: p.id == controller.activePreset.id,
                          onTap: () => controller.selectPreset(p),
                        ),
                      );
                    },
                  ),
                ),

                const SizedBox(height: 16),

                // ── Quick boost slider ────────────────────────────────────
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: _QuickBoost(
                    value: controller.boostLevel,
                    onChanged: controller.setBoost,
                  ),
                ),

                const SizedBox(height: 32),

                // ── Primary toggle button ─────────────────────────────────
                Center(
                  child: _ChaosToggleButton(
                    appState: controller.appState,
                    onTap: controller.toggleChaosMode,
                  ),
                ),

                const SizedBox(height: 32),

                // ── Quick stats ───────────────────────────────────────────
                if (controller.isActive)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: _ActiveStats(controller: controller),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showSettings(BuildContext context, ChaosController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => _SettingsSheet(controller: controller),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActiveStateBanner (19_UI_COMPONENTS.md)
// ─────────────────────────────────────────────────────────────────────────────

class _ActiveStateBanner extends StatelessWidget {
  final AppState appState;
  final Animation<double> pulseAnim;

  const _ActiveStateBanner({required this.appState, required this.pulseAnim});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;

    String label;
    Color bgColor;
    bool showPulse;

    switch (appState) {
      case AppState.running:
        label    = '🔴  CHAOS MODE ACTIVE';
        bgColor  = colors.primary;
        showPulse= true;
      case AppState.paused:
        label    = '⏸  PAUSED — incoming audio priority';
        bgColor  = const Color(0xFFE65100);
        showPulse= false;
      case AppState.initializing:
        label    = '⏳  STARTING…';
        bgColor  = const Color(0xFF424242);
        showPulse= false;
      case AppState.failure:
        label    = '⚠️  ERROR — see below';
        bgColor  = colors.error;
        showPulse= false;
      default:
        return const SizedBox.shrink();  // No banner when idle/stopped
    }

    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      width: double.infinity,
      color: bgColor,
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 20),
      child: showPulse
          ? AnimatedBuilder(
              animation: pulseAnim,
              builder: (_, __) => Opacity(
                opacity: 0.7 + 0.3 * pulseAnim.value,
                child: Text(
                  label,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.5,
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
            )
          : Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.5,
              ),
              textAlign: TextAlign.center,
            ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error banner
// ─────────────────────────────────────────────────────────────────────────────

class _ErrorBanner extends StatelessWidget {
  final String message;
  final VoidCallback onDismiss;

  const _ErrorBanner({required this.message, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Container(
      color: colors.error.withOpacity(0.15),
      padding: const EdgeInsets.fromLTRB(20, 10, 8, 10),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: colors.error, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(message,
                style: TextStyle(color: colors.error, fontSize: 13)),
          ),
          IconButton(
            icon: Icon(Icons.close, size: 18, color: colors.error),
            onPressed: onDismiss,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// PresetCard (19_UI_COMPONENTS.md)
// ─────────────────────────────────────────────────────────────────────────────

class _PresetCard extends StatelessWidget {
  final PresetModel preset;
  final bool isSelected;
  final VoidCallback onTap;

  const _PresetCard({
    required this.preset,
    required this.isSelected,
    required this.onTap,
  });

  static IconData _iconFor(String id) {
    return switch (id) {
      'demon'        => Icons.whatshot_rounded,
      'robot'        => Icons.smart_toy_rounded,
      'crushed_radio'=> Icons.radio_rounded,
      'glitch'       => Icons.bolt_rounded,
      _              => Icons.tune_rounded,
    };
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final text   = Theme.of(context).textTheme;

    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: 80, height: 80,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          color: isSelected
              ? colors.primary.withOpacity(0.2)
              : colors.surface,
          border: Border.all(
            color: isSelected ? colors.primary : colors.surface,
            width: 2,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              _iconFor(preset.id),
              color: isSelected ? colors.primary : colors.onSurface.withOpacity(0.5),
              size: 26,
            ),
            const SizedBox(height: 4),
            Text(
              preset.name,
              style: text.bodySmall?.copyWith(
                fontSize: 10,
                fontWeight: isSelected ? FontWeight.w700 : FontWeight.w400,
                color: isSelected ? colors.primary : colors.onSurface.withOpacity(0.6),
              ),
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Boost slider (compact version for home screen)
// ─────────────────────────────────────────────────────────────────────────────

class _QuickBoost extends StatelessWidget {
  final double value;
  final ValueChanged<double> onChanged;

  const _QuickBoost({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final colors  = Theme.of(context).colorScheme;
    final isWarn  = value >= 300;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isWarn ? const Color(0xFFE65100) : Colors.transparent,
          width: 1.5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Volume Boost',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      )),
              Text(
                '${value.toStringAsFixed(0)}%',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: isWarn ? const Color(0xFFE65100) : colors.primary,
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                    ),
              ),
            ],
          ),
          if (isWarn)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                '⚠️ High boost — limiter active',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: const Color(0xFFE65100),
                      fontSize: 10,
                    ),
              ),
            ),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: isWarn ? const Color(0xFFE65100) : colors.primary,
              thumbColor: isWarn ? const Color(0xFFE65100) : colors.primary,
            ),
            child: Slider(
              value: value.clamp(100, 500),
              min: 100, max: 500,
              divisions: 80,
              onChanged: onChanged,
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChaosToggleButton (19_UI_COMPONENTS.md)
// ─────────────────────────────────────────────────────────────────────────────

class _ChaosToggleButton extends StatefulWidget {
  final AppState appState;
  final VoidCallback onTap;

  const _ChaosToggleButton({required this.appState, required this.onTap});

  @override
  State<_ChaosToggleButton> createState() => _ChaosToggleButtonState();
}

class _ChaosToggleButtonState extends State<_ChaosToggleButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _tapCtrl;
  late Animation<double>   _tapAnim;

  @override
  void initState() {
    super.initState();
    _tapCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    );
    _tapAnim = Tween<double>(begin: 1.0, end: 0.93).animate(
      CurvedAnimation(parent: _tapCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _tapCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final isOn   = widget.appState == AppState.running;
    final isLoading = widget.appState == AppState.initializing;
    final isPaused  = widget.appState == AppState.paused;

    Color btnColor;
    String label;
    IconData icon;

    if (isOn) {
      btnColor = colors.primary;
      label = 'STOP';
      icon = Icons.stop_rounded;
    } else if (isLoading) {
      btnColor = const Color(0xFF424242);
      label = 'STARTING';
      icon = Icons.hourglass_empty_rounded;
    } else if (isPaused) {
      btnColor = const Color(0xFFE65100);
      label = 'PAUSED';
      icon = Icons.pause_rounded;
    } else {
      btnColor = const Color(0xFF2E2E2E);
      label = 'START CHAOS';
      icon = Icons.power_settings_new_rounded;
    }

    return GestureDetector(
      onTapDown: isLoading ? null : (_) => _tapCtrl.forward(),
      onTapUp: isLoading ? null : (_) async {
        await _tapCtrl.reverse();
        widget.onTap();
      },
      onTapCancel: () => _tapCtrl.reverse(),
      child: ScaleTransition(
        scale: _tapAnim,
        child: Container(
          width: 180, height: 180,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: btnColor,
            boxShadow: isOn
                ? [
                    BoxShadow(
                      color: colors.primary.withOpacity(0.4),
                      blurRadius: 32,
                      spreadRadius: 4,
                    ),
                  ]
                : [],
          ),
          child: isLoading
              ? const Center(
                  child: SizedBox(
                    width: 40,
                    height: 40,
                    child: CircularProgressIndicator(
                      strokeWidth: 3,
                      color: Colors.white,
                    ),
                  ),
                )
              : Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(icon, size: 48, color: Colors.white),
                    const SizedBox(height: 8),
                    Text(
                      label,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 2,
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active stats row
// ─────────────────────────────────────────────────────────────────────────────

class _ActiveStats extends StatelessWidget {
  final ChaosController controller;
  const _ActiveStats({required this.controller});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final text   = Theme.of(context).textTheme;
    final p      = controller.activePreset;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _StatCell(label: 'PRESET', value: p.name, text: text),
          _StatCell(label: 'BOOST', value: '${controller.boostLevel.toStringAsFixed(0)}%', text: text),
          _StatCell(label: 'LIMITER', value: controller.limiterEnabled ? 'ON' : 'OFF', text: text),
          _StatCell(label: 'BIT DEPTH', value: '${p.bitCrushDepth.toInt()}-bit', text: text),
        ],
      ),
    );
  }
}

class _StatCell extends StatelessWidget {
  final String label, value;
  final TextTheme text;
  const _StatCell({required this.label, required this.value, required this.text});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(label, style: text.bodySmall?.copyWith(fontSize: 9, letterSpacing: 1)),
        const SizedBox(height: 2),
        Text(value, style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w700)),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings bottom sheet (VPN opt-in, limiter, etc.)
// ─────────────────────────────────────────────────────────────────────────────

class _SettingsSheet extends StatelessWidget {
  final ChaosController controller;
  const _SettingsSheet({required this.controller});

  @override
  Widget build(BuildContext context) {
    final text   = Theme.of(context).textTheme;
    final colors = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 24, 24, 40),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40, height: 4,
              decoration: BoxDecoration(
                color: colors.onSurface.withOpacity(0.3),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Text('Settings', style: text.titleLarge),
          const SizedBox(height: 20),

          // Limiter toggle
          _SettingsTile(
            icon: Icons.compress_rounded,
            title: 'Soft-Knee Limiter',
            subtitle: 'Prevents destructive clipping at high boost levels.',
            trailing: Switch(
              value: controller.limiterEnabled,
              onChanged: controller.setLimiterEnabled,
            ),
          ),

          const Divider(height: 24),

          // VPN survival service (opt-in, default OFF)
          _SettingsTile(
            icon: Icons.shield_rounded,
            title: 'Enhanced Background Mode',
            subtitle:
                'Uses a minimal VPN anchor to resist OEM battery killers '
                '(Samsung, Xiaomi, OnePlus). Opt-in, defaults OFF. '
                'No traffic is routed.',
            trailing: Switch(
              value: controller.vpnSurvivalEnabled,
              onChanged: (v) {
                Navigator.pop(context);
                controller.setVpnSurvivalEnabled(v);
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _SettingsTile extends StatelessWidget {
  final IconData icon;
  final String title, subtitle;
  final Widget trailing;
  const _SettingsTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    final text   = Theme.of(context).textTheme;
    final colors = Theme.of(context).colorScheme;
    return Row(
      children: [
        Icon(icon, color: colors.primary, size: 22),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text(subtitle, style: text.bodySmall?.copyWith(height: 1.4)),
            ],
          ),
        ),
        const SizedBox(width: 8),
        trailing,
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Advanced Effects screen (reached via AppBar gear icon)
// ─────────────────────────────────────────────────────────────────────────────

class AdvancedEffectsScreen extends StatelessWidget {
  const AdvancedEffectsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Advanced Effects'),
        backgroundColor: Theme.of(context).colorScheme.background,
        elevation: 0,
      ),
      backgroundColor: Theme.of(context).colorScheme.background,
      body: const EffectSlidersWidget(),
    );
  }
}
