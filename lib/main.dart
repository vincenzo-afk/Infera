import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'chaos_controller.dart';
import 'screens/splash_screen.dart';

/// ChaosVoice app entry point.
///
/// Sets up the dark "chaos" theme per 18_UI_SPECIFICATION.md and mounts
/// [ChaosController] at the root so all child widgets can read/write state.
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ChaosVoiceApp());
}

class ChaosVoiceApp extends StatelessWidget {
  const ChaosVoiceApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => ChaosController(),
      child: MaterialApp(
        title: 'ChaosVoice',
        debugShowCheckedModeBanner: false,
        theme: _buildTheme(),
        home: const SplashScreen(),
      ),
    );
  }

  /// Dark, high-contrast theme with red/orange accent per 18_UI_SPECIFICATION.md.
  ThemeData _buildTheme() {
    const nearBlack   = Color(0xFF0D0D0D);
    const surfaceCard = Color(0xFF1A1A1A);
    const accent      = Color(0xFFE53935);   // deep red for active states
    const accentOrange = Color(0xFFFF6D00);  // orange accent
    const textPrimary = Color(0xFFF5F5F5);
    const textMuted   = Color(0xFF8A8A8A);

    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: nearBlack,
      colorScheme: const ColorScheme.dark(
        background: nearBlack,
        surface: surfaceCard,
        primary: accent,
        secondary: accentOrange,
        onBackground: textPrimary,
        onSurface: textPrimary,
        onPrimary: Colors.white,
        error: Color(0xFFCF6679),
      ),
      cardTheme: const CardThemeData(
        color: surfaceCard,
        elevation: 2,
        margin: EdgeInsets.zero,
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: accent,
        thumbColor: accent,
        overlayColor: accent.withOpacity(0.12),
        inactiveTrackColor: textMuted.withOpacity(0.3),
        valueIndicatorColor: accent,
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? accent : textMuted,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected)
              ? accent.withOpacity(0.5)
              : textMuted.withOpacity(0.3),
        ),
      ),
      textTheme: const TextTheme(
        displayLarge: TextStyle(
          fontFamily: 'Roboto',
          fontSize: 32,
          fontWeight: FontWeight.w900,
          letterSpacing: -1.0,
          color: textPrimary,
        ),
        titleLarge: TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w700,
          color: textPrimary,
          letterSpacing: 0.5,
        ),
        bodyMedium: TextStyle(fontSize: 14, color: textPrimary),
        bodySmall: TextStyle(
          fontSize: 11,
          color: textMuted,
          fontFeatures: [FontFeature.tabularFigures()],
        ),
      ),
      useMaterial3: true,
    );
  }
}
