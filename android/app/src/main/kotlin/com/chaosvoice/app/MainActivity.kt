package com.chaosvoice.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * ChaosVoice main activity — bridges Flutter (Dart) and native Android.
 *
 * MethodChannel  "com.chaosvoice/audio"  — Dart → Kotlin commands
 * EventChannel   "com.chaosvoice/events" — Kotlin → Dart state/error events
 *
 * Startup sequence (fixed, eliminates broadcast race):
 *   1. requestNotificationPermission
 *   2. requestRecordAudioPermission
 *   3. startChaosMode (starts FGS with preset + boost in the intent — no second broadcast)
 *   Battery exemption is requested AFTER confirming ACTIVE via event, not on the critical path.
 */
class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG                 = "[ChaosVoice][UI]"
        private const val METHOD_CHANNEL_NAME = "com.chaosvoice/audio"
        private const val EVENT_CHANNEL_NAME  = "com.chaosvoice/events"

        private const val REQ_RECORD_AUDIO    = 101
        private const val REQ_VPN_PERMISSION  = 102
        private const val REQ_POST_NOTIF      = 103

        // Static EventSink — set when Flutter subscribes to the EventChannel.
        @Volatile private var eventSink: EventChannel.EventSink? = null

        // Pending MethodChannel results for permissions requiring a callback.
        // Null-before-dispatch pattern prevents IllegalStateException on double-dispatch.
        private var pendingResultAudio: MethodChannel.Result? = null
        private var pendingResultVpn: MethodChannel.Result? = null
        private var pendingResultNotification: MethodChannel.Result? = null

        @JvmStatic
        fun sendServiceStateEvent(state: String) {
            eventSink?.success(mapOf("type" to "onServiceStateChanged", "state" to state))
        }

        @JvmStatic
        fun sendFocusEvent(focusState: String) {
            eventSink?.success(mapOf("type" to "onAudioFocusChanged", "focusState" to focusState))
        }

        @JvmStatic
        fun sendErrorEvent(code: String, message: String) {
            eventSink?.success(mapOf("type" to "onError", "code" to code, "message" to message))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FlutterEngine setup
    // ─────────────────────────────────────────────────────────────────────────

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                Log.d(TAG, "MethodChannel: ${call.method} args=${call.arguments}")
                handleMethodCall(call.method, call.arguments, result)
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL_NAME)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    Log.d(TAG, "EventChannel: Flutter subscribed")
                    eventSink = sink
                }
                override fun onCancel(arguments: Any?) {
                    Log.d(TAG, "EventChannel: Flutter unsubscribed")
                    eventSink = null
                }
            })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MethodChannel dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleMethodCall(
        method: String,
        arguments: Any?,
        result: MethodChannel.Result
    ) {
        @Suppress("UNCHECKED_CAST")
        val args = arguments as? Map<String, Any> ?: emptyMap()

        when (method) {

            // ── requestNotificationPermission ────────────────────────────────
            "requestNotificationPermission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        result.success(true)
                    } else {
                        pendingResultNotification = result
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQ_POST_NOTIF
                        )
                    }
                } else {
                    result.success(true)
                }
            }

            // ── requestRecordAudioPermission ─────────────────────────────────
            "requestRecordAudioPermission" -> {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    result.success(true)
                } else {
                    pendingResultAudio = result
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.RECORD_AUDIO),
                        REQ_RECORD_AUDIO
                    )
                }
            }

            // ── startForegroundServiceOnly ───────────────────────────────────
            // Kept for backward compatibility. Now unused by the main startup flow.
            "startForegroundServiceOnly" -> {
                try {
                    startChaosService(ChaosProjectionService.STATE_INIT)
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "startForegroundServiceOnly failed: ${e.message}")
                    result.error("SERVICE_START_FAILED", e.message, null)
                }
            }

            // ── startChaosMode ───────────────────────────────────────────────
            // Starts the FGS with full preset + boost information in the intent.
            // The service does INIT notification + ACTIVE transition in onStartCommand,
            // eliminating the previous START_CHAOS_MODE broadcast race condition.
            "startChaosMode" -> {
                val presetObj  = args["preset"]
                val boostLevel = (args["boostLevel"] as? Number)?.toFloat() ?: 150f
                try {
                    val intent = Intent(this, ChaosProjectionService::class.java).apply {
                        putExtra(ChaosProjectionService.EXTRA_TARGET_STATE, ChaosProjectionService.STATE_ACTIVE)
                        putExtra(ChaosProjectionService.EXTRA_BOOST, boostLevel)
                        when {
                            presetObj is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                val presetMap = presetObj as Map<String, Any>
                                putExtra(
                                    ChaosProjectionService.EXTRA_PRESET_JSON,
                                    org.json.JSONObject(presetMap).toString()
                                )
                            }
                            presetObj is String -> {
                                putExtra(ChaosProjectionService.EXTRA_PRESET, presetObj)
                            }
                            else -> {
                                putExtra(ChaosProjectionService.EXTRA_PRESET, Preset.DEMON.id)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    Log.i(TAG, "startChaosMode: service launched with preset=$presetObj boost=$boostLevel")
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "startChaosMode failed: ${e.message}")
                    result.error("AUDIO_INIT_FAILED", e.message, null)
                }
            }

            // ── stopChaosMode ────────────────────────────────────────────────
            "stopChaosMode" -> {
                try {
                    stopService(Intent(this, ChaosProjectionService::class.java))
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "stopChaosMode failed: ${e.message}")
                    result.error("SERVICE_NOT_RUNNING", e.message, null)
                }
            }

            // ── updateParameter ──────────────────────────────────────────────
            "updateParameter" -> {
                val stageKey = args["stage"] as? String ?: ""
                val value    = (args["value"] as? Number)?.toFloat() ?: 0f
                val stage    = ChaosDSP.Stage.fromKey(stageKey)
                if (stage == null) {
                    Log.w(TAG, "updateParameter: unknown stage '$stageKey'")
                    result.error("INVALID_STAGE", "Unknown stage: $stageKey", null)
                } else {
                    sendParameterUpdate(stageKey, value)
                    result.success(true)
                }
            }

            // ── loadPreset ───────────────────────────────────────────────────
            "loadPreset" -> {
                val presetObj = args["preset"]
                try {
                    val broadcastIntent = Intent("com.chaosvoice.app.LOAD_PRESET").apply {
                        `package` = packageName
                        when {
                            presetObj is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                val presetMap = presetObj as Map<String, Any>
                                putExtra("presetJson", org.json.JSONObject(presetMap).toString())
                            }
                            presetObj is String -> {
                                putExtra("presetId", presetObj)
                            }
                        }
                    }
                    sendBroadcast(broadcastIntent)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("PRESET_NOT_FOUND", e.message, null)
                }
            }

            // ── requestBatteryOptimizationExemption ──────────────────────────
            // Called AFTER ACTIVE state is confirmed — no longer on the critical startup path.
            "requestBatteryOptimizationExemption" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${packageName}")
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Battery exemption intent failed (non-fatal): ${e.message}")
                    }
                }
                result.success(true)
            }

            // ── requestVpnPermission ─────────────────────────────────────────
            "requestVpnPermission" -> {
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent == null) {
                    result.success(true)
                } else {
                    pendingResultVpn = result
                    startActivityForResult(vpnIntent, REQ_VPN_PERMISSION)
                }
            }

            // ── startVpnSurvivalService ──────────────────────────────────────
            "startVpnSurvivalService" -> {
                try {
                    val prefs = getSharedPreferences("chaosvoice_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("vpn_survival_enabled", true).apply()
                    ContextCompat.startForegroundService(this, Intent(this, ChaosVpnService::class.java))
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SERVICE_START_FAILED", e.message, null)
                }
            }

            // ── stopVpnSurvivalService ───────────────────────────────────────
            "stopVpnSurvivalService" -> {
                try {
                    val prefs = getSharedPreferences("chaosvoice_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("vpn_survival_enabled", false).apply()
                    stopService(Intent(this, ChaosVpnService::class.java))
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SERVICE_STOP_FAILED", e.message, null)
                }
            }

            else -> result.notImplemented()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service start helper (used for legacy INIT-only path)
    // ─────────────────────────────────────────────────────────────────────────

    private fun startChaosService(
        targetState: String,
        preset: String = Preset.DEMON.id,
        boost: Float = 150f
    ) {
        val intent = Intent(this, ChaosProjectionService::class.java).apply {
            putExtra(ChaosProjectionService.EXTRA_TARGET_STATE, targetState)
            putExtra(ChaosProjectionService.EXTRA_PRESET, preset)
            putExtra(ChaosProjectionService.EXTRA_BOOST, boost)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendParameterUpdate(stageKey: String, value: Float) {
        val intent = Intent("com.chaosvoice.app.UPDATE_PARAMETER").apply {
            `package` = packageName
            putExtra("stage", stageKey)
            putExtra("value", value)
        }
        sendBroadcast(intent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission result callbacks
    // ─────────────────────────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_RECORD_AUDIO -> {
                val granted = grantResults.isNotEmpty() &&
                              grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.i(TAG, "RECORD_AUDIO granted=$granted")
                val pending = pendingResultAudio
                pendingResultAudio = null
                pending?.success(granted)
            }
            REQ_POST_NOTIF -> {
                val granted = grantResults.isNotEmpty() &&
                              grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
                val pending = pendingResultNotification
                pendingResultNotification = null
                pending?.success(granted)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_VPN_PERMISSION -> {
                val granted = resultCode == Activity.RESULT_OK
                Log.i(TAG, "VPN permission granted=$granted")
                val pending = pendingResultVpn
                pendingResultVpn = null
                pending?.success(granted)
            }
        }
    }
}
