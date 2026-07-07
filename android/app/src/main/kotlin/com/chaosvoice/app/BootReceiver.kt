package com.chaosvoice.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * Re-arms the [ChaosVpnService] after a device reboot, but ONLY if the user had
 * it explicitly enabled (opt-in toggle in Settings, default OFF).
 *
 * Per 08_CLASS_SPECIFICATION.md and the user-approved implementation note:
 * ChaosVpnService is never auto-started; the user must have consciously enabled it.
 *
 * Audio processing is NOT resumed on boot — that would violate the transparency
 * principle (01_VISION_AND_GOALS.md). The user must manually reactivate Chaos Mode.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "[ChaosVoice][SERVICE]"
        const val PREFS_NAME       = "chaosvoice_prefs"
        const val KEY_VPN_ENABLED  = "vpn_survival_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "BOOT_COMPLETED received")

        // Bug fix: Flutter shared_preferences plugin uses "FlutterSharedPreferences" and prefixes all keys with "flutter."
        val prefs: SharedPreferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val vpnEnabled = prefs.getBoolean("flutter.$KEY_VPN_ENABLED", false)

        if (vpnEnabled) {
            Log.i(TAG, "VPN survival service was enabled by user — re-arming after boot")
            try {
                val vpnIntent = Intent(context, ChaosVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not restart ChaosVpnService after boot: ${e.message}")
            }
        } else {
            Log.d(TAG, "VPN survival service disabled by user — not re-arming")
        }
        // NOTE: ChaosProjectionService (audio) is NOT started here.
        // The user must manually reactivate Chaos Mode for transparency (15_BACKGROUND_SERVICE.md).
    }
}
