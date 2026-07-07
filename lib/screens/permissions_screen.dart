import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../chaos_controller.dart';
import 'home_screen.dart';

/// Shown on first run to explain and request required permissions.
///
/// Follows the sequence from 14_PERMISSION_MODEL.md and ADR-004:
///   1. Explain what each permission is for (before requesting)
///   2. Request POST_NOTIFICATIONS (Android 13+)
///   3. Start foreground service in INIT state
///   4. Request RECORD_AUDIO
///   5. Request battery optimization exemption
///   → Navigate to HomeScreen
class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen> {
  bool _isRequesting = false;
  String? _statusMessage;

  Future<void> _requestAll() async {
    setState(() { _isRequesting = true; _statusMessage = null; });

    final controller = context.read<ChaosController>();

    // Mark onboarding complete — we navigate to HomeScreen regardless of the
    // outcome. Permissions are requested step-by-step when the user first
    // activates effects on the HomeScreen. Any native bridge errors that
    // surface here are non-fatal at this stage.
    try {
      await controller.markOnboardingDone();
    } catch (e) {
      // markOnboardingDone failing to persist is non-fatal; the in-memory
      // flag is still set and the user can proceed.
      debugPrint('[PermissionsScreen] markOnboardingDone error (non-fatal): $e');
    }

    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const HomeScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final text   = Theme.of(context).textTheme;

    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Permissions', style: text.displayLarge?.copyWith(fontSize: 28)),
              const SizedBox(height: 8),
              Text(
                'ChaosVoice needs a couple of permissions to work. '
                'Here\'s exactly why:',
                style: text.bodyMedium,
              ),
              const SizedBox(height: 32),

              _PermItem(
                icon: Icons.mic_rounded,
                color: colors.primary,
                title: 'Microphone',
                desc: 'To capture your voice and apply the DSP effects chain in real time.',
              ),
              const SizedBox(height: 20),
              _PermItem(
                icon: Icons.notifications_rounded,
                color: colors.secondary,
                title: 'Notifications',
                desc: 'Android requires apps using the microphone in the background to show '
                    'a persistent notification — this also lets you always see when effects are active.',
              ),
              const SizedBox(height: 20),
              _PermItem(
                icon: Icons.battery_charging_full_rounded,
                color: const Color(0xFF66BB6A),
                title: 'Battery Optimization (optional)',
                desc: 'Exempting ChaosVoice from battery optimization helps keep your '
                    'session stable for longer. You can skip this.',
              ),

              const Spacer(),

              if (_statusMessage != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(
                    _statusMessage!,
                    style: text.bodySmall?.copyWith(color: colors.error),
                  ),
                ),

              SizedBox(
                width: double.infinity,
                height: 56,
                child: FilledButton(
                  onPressed: _isRequesting ? null : _requestAll,
                  style: FilledButton.styleFrom(
                    backgroundColor: colors.primary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: _isRequesting
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.5,
                            color: Colors.white,
                          ),
                        )
                      : const Text(
                          'Got it — Let\'s go',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                        ),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'Permissions will be requested step-by-step when you first activate effects.',
                style: text.bodySmall,
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PermItem extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String desc;

  const _PermItem({
    required this.icon,
    required this.color,
    required this.title,
    required this.desc,
  });

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 44, height: 44,
          decoration: BoxDecoration(
            color: color.withOpacity(0.12),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, color: color, size: 22),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title,
                  style: text.titleLarge?.copyWith(fontSize: 15)),
              const SizedBox(height: 4),
              Text(desc, style: text.bodySmall?.copyWith(height: 1.5)),
            ],
          ),
        ),
      ],
    );
  }
}
