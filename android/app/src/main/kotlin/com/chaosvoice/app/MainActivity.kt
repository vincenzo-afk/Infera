package com.chaosvoice.app

import android.app.Activity
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
 * Sets up:
 *   - MethodChannel  "com.chaosvoice/audio"  (Dart → Kotlin commands)
 *   - EventChannel   "com.chaosvoice/events" (Kotlin → Dart state/error events)
 *
 * All method names and argument shapes match 16_METHOD_CHANNEL_PROTOCOL.md exactly.
 *
 * Threading (13_THREADING_MODEL.md):
 *   MethodChannel handlers run on the Android main thread. Any long-running native
 *   work (starting the audio loop) is offloaded to the service's dedicated thread.
 */
class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG                 = "[ChaosVoice][UI]"
        private const val METHOD_CHANNEL_NAME = "com.chaosvoice/audio"
        private const val EVENT_CHANNEL_NAME  = "com.chaosvoice/events"

        // Activity result codes
        private const val REQ_RECORD_AUDIO    = 101
        private const val REQ_VPN_PERMISSION  = 102
        private const val REQ_POST_NOTIF      = 103

        // Static EventSink — set when Flutter subscribes to the EventChannel.
        // Used by ChaosProjectionService to push events back to Dart.
        @Volatile private var eventSink: EventChannel.EventSink? = null

        // Pending MethodChannel results for permissions that require a callback.
        // Bug 9 fix: each field is set to null immediately before calling success()/error()
        // to prevent IllegalStateException("Reply already submitted") if a callback fires
        // twice (e.g., from a stale result after a crash-and-relaunch mid-dialog).
        private var pendingResultAudio: MethodChannel.Result? = null
        private var pendingResultVpn: MethodChannel.Result? = null
        private var pendingResultNotification: MethodChannel.Result? = null

        // ── Static helpers called by ChaosProjectionService ───────────────

        /** Sends a service-state-changed event to the Flutter layer. */
        @JvmStatic
        fun sendServiceStateEvent(state: String) {
            eventSink?.success(mapOf("type" to "onServiceStateChanged", "state" to state))
        }

        /** Sends an audio-focus-changed event to the Flutter layer. */
        @JvmStatic
        fun sendFocusEvent(focusState: String) {
            eventSink?.success(mapOf("type" to "onAudioFocusChanged", "focusState" to focusState))
        }

        /** Sends an error event to the Flutter layer. */
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

        // ── MethodChannel: Dart → Kotlin ─────────────────────────────────
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                Log.d(TAG, "MethodChannel: ${call.method} args=${call.arguments}")
                handleMethodCall(call.method, call.arguments, result)
            }

        // ── EventChannel: Kotlin → Dart ──────────────────────────────────
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
    // MethodChannel dispatch (16_METHOD_CHANNEL_PROTOCOL.md)
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleMethodCall(
        method: String,
        arguments: Any?,
        result: MethodChannel.Result
    ) {
        @Suppress("UNCHECKED_CAST")
        val args = arguments as? Map<String, Any> ?: emptyMap()

        when (method) {

            // ── requestNotificationPermission ────────────────────────────
            // Step 1 of the corrected permission sequence (ADR-004, 14_PERMISSION_MODEL.md).
            // On Android 13+ (TIRAMISU), POST_NOTIFICATIONS is a runtime permission.
            // On earlier versions it is automatically granted, so we return true immediately.
            "requestNotificationPermission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        result.success(true)
                        Log.i("[ChaosVoice][PERMS]", "POST_NOTIFICATIONS already granted")
                    } else {
                        pendingResultNotification = result
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQ_POST_NOTIF
                        )
                    }
                } else {
                    // Pre-Android 13: notification permission is automatically granted.
                    result.success(true)
                }
            }

            // ── startForegroundServiceOnly ───────────────────────────────
            // Start service in INIT state (no audio) — step 3 of corrected permission
            // sequence (ADR-004). RECORD_AUDIO must already be granted before this point.
            "startForegroundServiceOnly" -> {
                try {
                    startChaosService(ChaosProjectionService.STATE_INIT)
                    result.success(true)
                    Log.i(TAG, "Foreground service started in INIT state")
                } catch (e: Exception) {
                    Log.e(TAG, "startForegroundServiceOnly failed: ${e.message}")
                    result.error("SERVICE_START_FAILED", e.message, null)
                }
            }

            // ── requestRecordAudioPermission ─────────────────────────────
            // Step 2 of the corrected permission sequence (ADR-004, 14_PERMISSION_MODEL.md).
            // Must be requested BEFORE startForegroundServiceOnly on Android 14+ so that
            // RECORD_AUDIO is granted at the moment startForeground() is called on a
            // microphone-typed foreground service.
            "requestRecordAudioPermission" -> {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    result.success(true)
                    Log.i("[ChaosVoice][PERMS]", "RECORD_AUDIO already granted")
                } else {
                    pendingResultAudio = result
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.RECORD_AUDIO),
                        REQ_RECORD_AUDIO
                    )
                }
            }

            // ── startChaosMode ───────────────────────────────────────────
            // Bug 3 fix: send START_CHAOS_MODE broadcast to the already-running service
            // instead of calling startForegroundService(STATE_ACTIVE).
            "startChaosMode" -> {
                val presetObj  = args["preset"]
                val boostLevel = (args["boostLevel"] as? Number)?.toFloat() ?: 150f
                try {
                    val broadcastIntent = Intent("com.chaosvoice.app.START_CHAOS_MODE").apply {
                        `package` = packageName
                        if (presetObj is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val presetMap = presetObj as Map<String, Any>
                            putExtra("presetJson", org.json.JSONObject(presetMap).toString())
                        } else if (presetObj is String) {
                            putExtra(ChaosProjectionService.EXTRA_PRESET, presetObj)
                        } else {
                            putExtra(ChaosProjectionService.EXTRA_PRESET, Preset.DEMON.id)
                        }
                        putExtra(ChaosProjectionService.EXTRA_BOOST, boostLevel)
                    }
                    sendBroadcast(broadcastIntent)
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "startChaosMode broadcast failed: ${e.message}")
                    result.error("AUDIO_INIT_FAILED", e.message, null)
                }
            }

            // ── stopChaosMode ────────────────────────────────────────────
            "stopChaosMode" -> {
                try {
                    val intent = Intent(this, ChaosProjectionService::class.java)
                    stopService(intent)
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "stopChaosMode failed: ${e.message}")
                    result.error("SERVICE_NOT_RUNNING", e.message, null)
                }
            }

            // ── updateParameter ──────────────────────────────────────────
            // Live DSP parameter update from a slider change.
            "updateParameter" -> {
                val stageKey = args["stage"] as? String ?: ""
                val value    = (args["value"] as? Number)?.toFloat() ?: 0f
                val stage    = ChaosDSP.Stage.fromKey(stageKey)
                if (stage == null) {
                    result.error("INVALID_STAGE", "Unknown stage: $stageKey", null)
                } else {
                    sendParameterUpdate(stageKey, value)
                    result.success(true)
                }
            }

            // ── loadPreset ───────────────────────────────────────────────
            "loadPreset" -> {
                val presetObj = args["preset"]
                val presetId = args["presetId"] as? String
                try {
                    val broadcastIntent = Intent("com.chaosvoice.app.LOAD_PRESET").apply {
                        `package` = packageName
                        if (presetObj is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val presetMap = presetObj as Map<String, Any>
                            putExtra("presetJson", org.json.JSONObject(presetMap).toString())
                        } else if (presetObj is String) {
                            putExtra("presetId", presetObj)
                        } else if (presetId != null) {
                            putExtra("presetId", presetId)
                        }
                    }
                    sendBroadcast(broadcastIntent)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("PRESET_NOT_FOUND", e.message, null)
                }
            }

            // ── requestBatteryOptimizationExemption ──────────────────────
            // Step 4 of permission sequence (ADR-004, 14_PERMISSION_MODEL.md)
            "requestBatteryOptimizationExemption" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${packageName}")
                    )
                    startActivity(intent)
                }
                result.success(true)
            }

            // ── requestVpnPermission (ChaosVpnService opt-in) ────────────
            "requestVpnPermission" -> {
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent == null) {
                    // Already granted
                    result.success(true)
                } else {
                    pendingResultVpn = result
                    startActivityForResult(vpnIntent, REQ_VPN_PERMISSION)
                }
            }

            // ── startVpnSurvivalService ───────────────────────────────────
            "startVpnSurvivalService" -> {
                try {
                    val prefs = getSharedPreferences("chaosvoice_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("vpn_survival_enabled", true).apply()

                    val intent = Intent(this, ChaosVpnService::class.java)
                    ContextCompat.startForegroundService(this, intent)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SERVICE_START_FAILED", e.message, null)
                }
            }

            // ── stopVpnSurvivalService ───────────────────────────────────
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
    // Service start helper
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

    private fun sendPresetLoad(presetId: String) {
        val intent = Intent("com.chaosvoice.app.LOAD_PRESET").apply {
            `package` = packageName
            putExtra("presetId", presetId)
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
                Log.i("[ChaosVoice][PERMS]", "RECORD_AUDIO granted=$granted")
                // Bug 9 fix: capture and null-out the pending result before calling success()
                // to prevent IllegalStateException("Reply already submitted") if this callback
                // fires a second time (e.g., process relaunch after FGS crash mid-dialog).
                val pending = pendingResultAudio
                pendingResultAudio = null
                pending?.success(granted)
            }
            REQ_POST_NOTIF -> {
                val granted = grantResults.isNotEmpty() &&
                              grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.i("[ChaosVoice][PERMS]", "POST_NOTIFICATIONS granted=$granted")
                // Bug 9 fix: same null-before-dispatch pattern.
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
                Log.i("[ChaosVoice][PERMS]", "VPN permission granted=$granted")
                // Bug 9 fix: capture and null-out before dispatch.
                val pending = pendingResultVpn
                pendingResultVpn = null
                pending?.success(granted)
            }
        }
    }
}
