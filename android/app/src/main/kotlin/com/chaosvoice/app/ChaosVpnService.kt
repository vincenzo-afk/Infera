package com.chaosvoice.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * ChaosVpnService — optional background-survival helper service.
 *
 * This service is entirely independent of the audio pipeline; it exists solely
 * to help the process survive aggressive OEM battery optimisers (MIUI, One UI, etc.)
 * that terminate standard foreground services after a few minutes (29_COMPATIBILITY.md,
 * Known Issue #8).
 *
 * IMPORTANT: This service is OPT-IN only. It is never auto-started.
 * The user enables it explicitly via the "Enhanced Background Mode" toggle in Settings,
 * which defaults to OFF (user-approved implementation constraint).
 *
 * ── Network safety ────────────────────────────────────────────────────────────
 * We intentionally:
 *   1. Use 127.0.0.2/32 — a loopback-range address that is never globally routable.
 *   2. Add NO routes (no addRoute() call) — so the kernel does not redirect any
 *      packets through the VPN interface.
 *   3. Call allowBypass() — lets all apps bypass this interface by default, meaning
 *      DNS, HTTPS, and all other traffic continues through the normal network path
 *      even if future Android versions become more aggressive about VPN routing.
 *
 * On real devices these three guards together ensure zero network disruption.
 * The VPN interface FD is opened purely as a process-lifecycle anchor — the kernel
 * sees it as a "live VPN" and does not aggressively kill the process, but no
 * actual traffic is ever tunnelled through it.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class ChaosVpnService : VpnService() {

    companion object {
        private const val TAG             = "[ChaosVoice][SERVICE]"
        private const val CHANNEL_ID      = "chaosvoice_vpn"
        private const val NOTIFICATION_ID = 1002
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    /**
     * Whether this *instance* has already established the interface.
     * Prevents calling establish() twice on the same instance (e.g., rapid
     * START_STICKY restarts before onDestroy has fired).
     * Note: resets to false on each new instance, so a sticky-restart always
     * calls establish() once on the fresh instance — which is correct.
     */
    private var isEstablished = false

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ChaosVpnService onStartCommand (isEstablished=$isEstablished)")
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Guard: do not call establish() again if this instance already has a live FD
        if (!isEstablished) {
            establish()
        } else {
            Log.d(TAG, "VPN interface already established on this instance — skipping")
        }

        return START_STICKY
    }

    /**
     * Establishes the survival-anchor VPN interface.
     *
     * Address: 127.0.0.2/32 — loopback-range, never globally routable.
     * allowBypass(): all apps route traffic normally; nothing is tunnelled.
     * No addRoute() calls — kernel receives no routing entries for this interface,
     * consistent with the class-level "zero traffic tunnelled" guarantee.
     *
     * Bug 6 fix: removed addRoute("192.0.2.1", 32) which was inconsistent with
     * allowBypass() and the class-level documentation. allowBypass() alone is
     * sufficient for the OS to maintain the interface as a live anchor.
     *
     * ⚠ MANUAL REGRESSION REQUIRED: verify that the survival service still
     * prevents aggressive background kill on MIUI/OneUI target devices after
     * this route removal. If background survival regresses, see KNOWN_ISSUES.md
     * and consider replacing the route with a keep-alive mechanism instead.
     */
    fun establish() {
        if (isEstablished) {
            Log.d(TAG, "establish() called but already established — ignoring")
            return
        }
        try {
            vpnInterface = Builder()
                .setSession("ChaosVoice Survival")
                // Loopback-range address — not globally routable
                .addAddress("127.0.0.2", 32)
                // All apps bypass this VPN by default; no traffic is ever tunnelled.
                // allowBypass() alone keeps the interface alive as a process-lifecycle anchor.
                .allowBypass()
                .establish()
            isEstablished = vpnInterface != null
            Log.i(TAG, "VPN interface established (survival anchor, no routing): isEstablished=$isEstablished")
        } catch (e: Exception) {
            Log.w(TAG, "VPN establish failed: ${e.message} — non-fatal, session may be less stable")
            isEstablished = false
        }
    }

    /**
     * Tears down the VPN tunnel cleanly when the user disables the survival service.
     */
    fun teardown() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "VPN teardown error (ignored): ${e.message}")
        } finally {
            vpnInterface = null
            isEstablished = false
        }
        Log.i(TAG, "VPN interface torn down")
    }

    override fun onRevoke() {
        // System revoked the VPN permission — tear down cleanly
        Log.w(TAG, "VPN permission revoked by system")
        teardown()
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        Log.i(TAG, "ChaosVpnService destroyed")
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification (required for foreground service on Android 8+)
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChaosVoice Background Mode",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps ChaosVoice alive in the background on this device"
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ChaosVoice")
                .setContentText("Enhanced background mode active")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("ChaosVoice")
                .setContentText("Enhanced background mode active")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .setOngoing(true)
                .build()
        }
    }
}
