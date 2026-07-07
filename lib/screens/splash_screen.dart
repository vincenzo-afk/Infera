import 'package:flutter/material.dart';
import 'home_screen.dart';
import 'permissions_screen.dart';
import '../chaos_controller.dart';
import 'package:provider/provider.dart';

/// Brief branding splash shown while the app loads settings and saved presets.
/// Transitions to either [PermissionsScreen] (first run) or [HomeScreen].
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _anim;
  late Animation<double> _fadeAnim;
  late Animation<double> _scaleAnim;

  @override
  void initState() {
    super.initState();
    _anim = AnimationController(
      duration: const Duration(milliseconds: 900),
      vsync: this,
    );
    _fadeAnim  = CurvedAnimation(parent: _anim, curve: Curves.easeIn);
    _scaleAnim = Tween<double>(begin: 0.85, end: 1.0).animate(
      CurvedAnimation(parent: _anim, curve: Curves.easeOut),
    );
    _anim.forward();

    // Navigate after splash delay
    Future.delayed(const Duration(milliseconds: 1600), _navigate);
  }

  void _navigate() {
    if (!mounted) return;
    final controller = context.read<ChaosController>();
    final next = controller.onboardingDone
        ? const HomeScreen()
        : const PermissionsScreen();
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (_, __, ___) => next,
        transitionsBuilder: (_, anim, __, child) =>
            FadeTransition(opacity: anim, child: child),
        transitionDuration: const Duration(milliseconds: 400),
      ),
    );
  }

  @override
  void dispose() {
    _anim.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: colors.background,
      body: Center(
        child: FadeTransition(
          opacity: _fadeAnim,
          child: ScaleTransition(
            scale: _scaleAnim,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Chaos icon / logo
                Container(
                  width: 100, height: 100,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: colors.primary.withOpacity(0.15),
                    border: Border.all(color: colors.primary, width: 2),
                  ),
                  child: Icon(
                    Icons.graphic_eq_rounded,
                    size: 52, color: colors.primary,
                  ),
                ),
                const SizedBox(height: 24),
                Text(
                  'CHAOSVOICE',
                  style: Theme.of(context).textTheme.displayLarge?.copyWith(
                        fontSize: 36,
                        letterSpacing: 6,
                        color: colors.primary,
                      ),
                ),
                const SizedBox(height: 8),
                Text(
                  'voice effects · acoustic loopback',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        letterSpacing: 2,
                      ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
