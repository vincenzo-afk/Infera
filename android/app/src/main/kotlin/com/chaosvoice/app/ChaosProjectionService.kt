package com.chaosvoice.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ChaosProjectionService — the foreground service owning the live audio pipeline.
 *
 * Lifecycle (15_BACKGROUND_SERVICE.md):
 *   INIT   — foreground notification shown, no audio yet (brief, during transition)
 *   ACTIVE — full AudioRecord → ChaosDSP → AudioTrack loop running
 *   STOPPED — pipeline torn down, notification cleared
 *
 * Audio routing (ADR-002):
 *   AudioTrack uses USAGE_MEDIA to route output to the loudspeaker, enabling
 *   acoustic loopback into the call app's microphone. Intentional architecture.
 *
 * Startup flow (fixed):
 *   MainActivity sends one startForegroundService intent with STATE_ACTIVE + preset + boost.
 *   onStartCommand: startForeground() (INIT notification) → immediately transitionToActive().
 *   No separate START_CHAOS_MODE broadcast needed — eliminates the broadcast race.
 */
class ChaosProjectionService : Service() {

    companion object {
        private const val TAG = "[ChaosVoice][SERVICE]"

        const val CHANNEL_ID        = "chaosvoice_active"
        const val NOTIFICATION_ID   = 1001

        const val EXTRA_TARGET_STATE = "target_state"
        const val EXTRA_PRESET       = "preset"
        const val EXTRA_PRESET_JSON  = "presetJson"
        const val EXTRA_BOOST        = "boost"

        const val STATE_INIT    = "INIT"
        const val STATE_ACTIVE  = "ACTIVE"
        const val STATE_STOPPED = "STOPPED"

        // Audio config from 22_CONFIGURATION.md
        private const val SAMPLE_RATE         = 48_000
        private const val BUFFER_SIZE_SAMPLES = 1_920   // ~40 ms at 48 kHz

        // Wake lock renewal interval (~10 min at 25 buffers/sec)
        private const val WAKE_LOCK_RENEW_INTERVAL_BUFFERS = 15_000
    }

    enum class ServiceState { INIT, ACTIVE, STOPPED }

    private var serviceState = ServiceState.STOPPED
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val dsp = ChaosDSP(SAMPLE_RATE)
    private lateinit var focusManager: AudioFocusManager
    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // routingReceiver: registered ONLY after ACTIVE state is set to avoid
    // the sticky ACTION_HEADSET_PLUG false-trigger on registration.
    private var routingReceiverRegistered = false
    private val routingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Guard: only act when fully ACTIVE to prevent false-triggers
            if (serviceState != ServiceState.ACTIVE) return
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val hasHeadphones = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            } else {
                @Suppress("DEPRECATION")
                am.isWiredHeadsetOn || am.isBluetoothA2dpOn
            }

            if (hasHeadphones) {
                Log.w(TAG, "Headphones/BT detected mid-session — terminating")
                broadcastError("ROUTING_UNSUPPORTED",
                    "Headphones or Bluetooth detected. Chaos Mode requires speakerphone.")
                cleanupAndStop()
            }
        }
    }

    // commandReceiver: handles live parameter/preset updates during active session.
    // START_CHAOS_MODE is no longer needed here since we use the intent approach,
    // but kept for backward compatibility with any external callers.
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.chaosvoice.app.UPDATE_PARAMETER" -> {
                    val stageKey = intent.getStringExtra("stage") ?: return
                    val value    = intent.getFloatExtra("value", 0f)
                    val stage    = ChaosDSP.Stage.fromKey(stageKey)
                    if (stage != null) {
                        dsp.setParameter(stage, value)
                        Log.d(TAG, "Parameter updated: $stageKey = $value")
                    } else {
                        Log.w(TAG, "Unknown stage key: $stageKey")
                    }
                }
                "com.chaosvoice.app.LOAD_PRESET" -> {
                    val presetJson = intent.getStringExtra("presetJson")
                    val presetId   = intent.getStringExtra("presetId")
                    val preset = when {
                        presetJson != null -> {
                            try {
                                Preset.fromJson(presetJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse presetJson: ${e.message}")
                                null
                            }
                        }
                        presetId != null -> Preset.builtInById(presetId)
                        else -> null
                    }
                    if (preset != null) {
                        dsp.loadPreset(preset)
                        Log.i(TAG, "Preset loaded: ${preset.name}")
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        focusManager = AudioFocusManager(this) { focusChange -> onFocusChanged(focusChange) }

        // Register command receiver for live parameter/preset updates
        val filter = IntentFilter().apply {
            addAction("com.chaosvoice.app.UPDATE_PARAMETER")
            addAction("com.chaosvoice.app.LOAD_PRESET")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        // NOTE: routingReceiver is registered in transitionToActive(), NOT here,
        // to avoid the sticky ACTION_HEADSET_PLUG false-trigger on registration.

        Log.i(TAG, "ChaosProjectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle null intent (sticky restart) — clear state and stop
        if (intent == null) {
            Log.w(TAG, "onStartCommand: null intent (sticky restart) — stopping")
            try {
                startForegroundCompat(buildNotification("ChaosVoice — stopping"))
            } catch (e: Exception) {
                Log.w(TAG, "startForeground on null-intent path threw: ${e.message}")
            }
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val targetState = intent.getStringExtra(EXTRA_TARGET_STATE) ?: STATE_ACTIVE

        Log.i(TAG, "onStartCommand targetState=$targetState")

        when (targetState) {
            STATE_STOPPED -> {
                // Satisfy startForegroundService() OS contract before stopping
                try {
                    startForegroundCompat(buildNotification("Stopping ChaosVoice…"))
                } catch (e: Exception) {
                    Log.w(TAG, "startForeground on STOPPED path threw (ignored): ${e.message}")
                }
                cleanupAndStop()
                return START_NOT_STICKY
            }
            else -> {
                // Both STATE_INIT and STATE_ACTIVE: show FGS notification immediately,
                // then transition to ACTIVE with the provided preset/boost.
                //
                // This design eliminates the broadcast race:
                //   Old flow: start service → wait for onCreate → send broadcast → ACTIVE
                //   New flow: start service → onStartCommand → INIT notification → ACTIVE immediately
                //
                // The preset and boost are passed directly in the intent extras.
                val presetJson = intent.getStringExtra(EXTRA_PRESET_JSON)
                val presetId   = intent.getStringExtra(EXTRA_PRESET) ?: Preset.DEMON.id
                val boost      = intent.getFloatExtra(EXTRA_BOOST, Preset.DEMON.boostPercent)

                val preset = when {
                    presetJson != null -> {
                        try {
                            Preset.fromJson(presetJson)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse presetJson in intent: ${e.message}")
                            Preset.builtInById(presetId)
                        }
                    }
                    else -> Preset.builtInById(presetId)
                }

                // Step 1: Show foreground notification immediately (satisfies startForegroundService contract)
                try {
                    startForegroundCompat(buildNotification("ChaosVoice — starting…"))
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed: ${e.message}", e)
                    broadcastError("FGS_START_FAILED",
                        "ChaosVoice couldn't start the background service. " +
                        "Please ensure microphone permission is granted and try again.")
                    cleanupAndStop()
                    return START_NOT_STICKY
                }

                serviceState = ServiceState.INIT
                broadcastStateChange(STATE_INIT)

                // Step 2: Immediately transition to ACTIVE (no broadcast needed)
                transitionToActive(preset, boost)
            }
        }

        return START_STICKY
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State transitions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls the correct startForeground overload for the current API level.
     * Both the manifest foregroundServiceType="microphone" attribute AND this
     * runtime flag are required on API 29+ (independent enforcement points).
     */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * ACTIVE: load preset, acquire focus, initialise audio devices, start processing loop.
     * Registers the routing receiver AFTER setting serviceState = ACTIVE to avoid the
     * sticky ACTION_HEADSET_PLUG false-trigger that would immediately tear down the session.
     */
    private fun transitionToActive(preset: Preset, boost: Float) {
        dsp.loadPreset(preset)
        dsp.setParameter(ChaosDSP.Stage.BOOST_PERCENT, boost)

        // Request audio focus
        if (!focusManager.requestFocus()) {
            Log.w(TAG, "Audio focus denied — cannot start")
            broadcastError("FOCUS_DENIED", "Another app is using audio right now.")
            cleanupAndStop()
            return
        }

        // ── AudioRecord ──────────────────────────────────────────────────────
        val bytesBufSize = BUFFER_SIZE_SAMPLES * 2   // 16-bit = 2 bytes/sample
        val minRecordBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recordBufSize = maxOf(bytesBufSize, minRecordBuf)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recordBufSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed: ${e.message}")
            broadcastError("AUDIO_INIT_FAILED", "Couldn't access the microphone. Try restarting the app.")
            cleanupAndStop(); return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not STATE_INITIALIZED")
            broadcastError("AUDIO_INIT_FAILED", "Couldn't access the microphone. Try restarting the app.")
            cleanupAndStop(); return
        }

        // ── AudioTrack ───────────────────────────────────────────────────────
        val minTrackBuf  = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val trackBufSize = maxOf(bytesBufSize * 2, minTrackBuf)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(trackBufSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed: ${e.message}")
            broadcastError("AUDIO_INIT_FAILED", "Couldn't start audio playback. Please try again.")
            cleanupAndStop(); return
        }

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not STATE_INITIALIZED")
            broadcastError("AUDIO_INIT_FAILED", "Couldn't start audio playback. Please try again.")
            cleanupAndStop(); return
        }

        // ── Loudspeaker routing ──────────────────────────────────────────────
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val speakerDevice = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            speakerDevice?.let { audioTrack?.preferredDevice = it }
        }

        // ── Wake lock ────────────────────────────────────────────────────────
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChaosVoice:AudioSession")
        @Suppress("WakelockTimeout")
        wakeLock?.acquire()

        // Set ACTIVE state BEFORE registering routing receiver to prevent false-trigger
        serviceState = ServiceState.ACTIVE
        updateNotification("ChaosVoice is active — voice effects running")
        broadcastStateChange(STATE_ACTIVE)

        // Register routing receiver NOW (after serviceState = ACTIVE) so the guard
        // in routingReceiver.onReceive() correctly filters the sticky broadcast re-delivery.
        if (!routingReceiverRegistered) {
            val routingFilter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(routingReceiver, routingFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(routingReceiver, routingFilter)
            }
            routingReceiverRegistered = true
        }

        startProcessingLoop()
        Log.i(TAG, "→ ACTIVE (preset=${preset.name}, boost=${boost}%)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio processing loop
    // ─────────────────────────────────────────────────────────────────────────

    private fun startProcessingLoop() {
        isRunning.set(true)
        audioThread = Thread {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    Log.i(TAG, "Audio thread URGENT_AUDIO priority set (tid=${Process.myTid()})")
                } catch (e: Exception) {
                    try { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) } catch (_: Exception) {}
                    Log.w(TAG, "Could not set URGENT_AUDIO, degraded: ${e.message}")
                }

                val captureBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
                audioRecord?.startRecording()
                audioTrack?.play()

                Log.i(TAG, "Audio loop started")
                var bufferCount = 0

                while (isRunning.get()) {
                    try {
                        // Re-assert speakerphone every second (~25 buffers at 40ms each)
                        if (bufferCount % 25 == 0) {
                            @Suppress("DEPRECATION")
                            if (!am.isSpeakerphoneOn) {
                                am.mode = AudioManager.MODE_NORMAL
                                @Suppress("DEPRECATION")
                                am.isSpeakerphoneOn = true
                            }
                        }

                        val read = audioRecord?.read(captureBuffer, 0, BUFFER_SIZE_SAMPLES) ?: -1
                        if (read <= 0) {
                            Log.d(TAG, "AudioRecord read=$read (underrun), skipping")
                            continue
                        }

                        // DSP chain (all native Kotlin — ADR-001)
                        val processed = dsp.process(captureBuffer)

                        // Write to loudspeaker
                        audioTrack?.write(processed, 0, read)

                        // Renew wake lock every ~10 minutes
                        bufferCount++
                        if (bufferCount >= WAKE_LOCK_RENEW_INTERVAL_BUFFERS) {
                            bufferCount = 0
                            val wl = wakeLock
                            if (wl != null && wl.isHeld) {
                                wl.release()
                                @Suppress("WakelockTimeout")
                                wl.acquire()
                                Log.d(TAG, "Wake lock renewed")
                            }
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Audio loop error: ${e.message}", e)
                        Handler(Looper.getMainLooper()).post {
                            broadcastError("AUDIO_INIT_FAILED", "Audio pipeline error. Please restart.")
                            cleanupAndStop()
                        }
                        break
                    }
                }
            } finally {
                val wl = wakeLock
                if (wl != null && wl.isHeld) {
                    wl.release()
                    Log.i(TAG, "Wake lock released in thread finally")
                }
                Log.i(TAG, "Audio thread exited")
            }
        }.also {
            it.name = "ChaosVoice-Audio"
            it.start()
        }
    }

    fun stopProcessingLoop() {
        Log.i(TAG, "Stopping processing loop")
        isRunning.set(false)
        audioThread?.join(2000L)
        audioThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        focusManager.abandonFocus()
        val wl = wakeLock
        if (wl != null && wl.isHeld) wl.release()
        wakeLock = null

        dsp.reset()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Focus change handling
    // ─────────────────────────────────────────────────────────────────────────

    private fun onFocusChanged(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Focus GAINED — resuming")
                if (serviceState == ServiceState.ACTIVE) audioTrack?.play()
                MainActivity.sendFocusEvent("GAINED")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "Focus LOST_TRANSIENT — pausing")
                audioTrack?.pause()
                MainActivity.sendFocusEvent("LOST_TRANSIENT")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Focus LOST permanently — stopping")
                MainActivity.sendFocusEvent("LOST")
                cleanupAndStop()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parameter update helpers (called from MainActivity via MethodChannel)
    // ─────────────────────────────────────────────────────────────────────────

    fun updateDspParameter(stage: ChaosDSP.Stage, value: Float) =
        dsp.setParameter(stage, value)

    fun loadDspPreset(preset: Preset) = dsp.loadPreset(preset)

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    private fun cleanupAndStop() {
        stopProcessingLoop()
        serviceState = ServiceState.STOPPED
        broadcastStateChange(STATE_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        Log.i(TAG, "→ STOPPED")
    }

    override fun onDestroy() {
        stopProcessingLoop()
        serviceState = ServiceState.STOPPED
        broadcastStateChange(STATE_STOPPED)
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        if (routingReceiverRegistered) {
            try { unregisterReceiver(routingReceiver) } catch (_: Exception) {}
            routingReceiverRegistered = false
        }
        Log.i(TAG, "ChaosProjectionService destroyed")
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChaosVoice Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while ChaosVoice is processing your voice"
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ChaosVoice")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("ChaosVoice")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EventChannel bridge helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun broadcastStateChange(state: String) =
        MainActivity.sendServiceStateEvent(state)

    private fun broadcastError(code: String, message: String) =
        MainActivity.sendErrorEvent(code, message)
}
