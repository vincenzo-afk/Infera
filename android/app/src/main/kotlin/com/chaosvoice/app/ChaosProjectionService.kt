package com.chaosvoice.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
 * Lifecycle states (15_BACKGROUND_SERVICE.md):
 *   INIT   — foreground started, notification shown, no audio yet (awaiting permissions)
 *   ACTIVE — full AudioRecord → ChaosDSP → AudioTrack loop running
 *   STOPPED — pipeline torn down, notification cleared
 *
 * Audio routing (ADR-002, 12_AUDIO_ROUTING.md):
 *   AudioTrack uses USAGE_MEDIA (NOT USAGE_VOICE_COMMUNICATION) to route output
 *   to the loudspeaker, enabling acoustic loopback into the call app's microphone.
 *   This is intentional architecture, not a bug.
 *
 * DSP (ADR-001): all processing in [ChaosDSP] on the audio thread — Dart layer
 * is NOT invoked per-buffer.
 */
class ChaosProjectionService : Service() {

    // ─────────────────────────────────────────────────────────────────────────
    // Companion — extras, channel id, state strings
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "[ChaosVoice][SERVICE]"

        const val CHANNEL_ID        = "chaosvoice_active"
        const val NOTIFICATION_ID   = 1001

        const val EXTRA_TARGET_STATE = "target_state"
        const val EXTRA_PRESET       = "preset"
        const val EXTRA_BOOST        = "boost"

        const val STATE_INIT    = "INIT"
        const val STATE_ACTIVE  = "ACTIVE"
        const val STATE_STOPPED = "STOPPED"

        // Audio config from 22_CONFIGURATION.md
        private const val SAMPLE_RATE         = 48_000
        private const val BUFFER_SIZE_SAMPLES = 1_920   // ~40 ms at 48 kHz

        // Renew the wake lock every 10 minutes worth of buffers to survive long sessions.
        // At 40 ms/buffer → 25 buffers/sec → 15 000 buffers / 10 min.
        private const val WAKE_LOCK_RENEW_INTERVAL_BUFFERS = 15_000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service state
    // ─────────────────────────────────────────────────────────────────────────
    enum class ServiceState { INIT, ACTIVE, STOPPED }

    private var serviceState = ServiceState.STOPPED
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val dsp = ChaosDSP(SAMPLE_RATE)
    private lateinit var focusManager: AudioFocusManager
    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val routingReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            Log.i(TAG, "routingReceiver action=$action")
            
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

            if (hasHeadphones && serviceState == ServiceState.ACTIVE) {
                Log.w(TAG, "Unsupported routing detected (headphones/BT active) — terminating Chaos Mode")
                broadcastError("ROUTING_UNSUPPORTED", 
                    "Headphones or Bluetooth device connected. Chaos Mode requires the speakerphone to be active.")
                cleanupAndStop()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null  // Not a bound service

    private val commandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.chaosvoice.app.UPDATE_PARAMETER" -> {
                    val stageKey = intent.getStringExtra("stage") ?: return
                    val value    = intent.getFloatExtra("value", 0f)
                    val stage    = ChaosDSP.Stage.fromKey(stageKey)
                    if (stage != null) dsp.setParameter(stage, value)
                }
                "com.chaosvoice.app.LOAD_PRESET" -> {
                    val presetJson = intent.getStringExtra("presetJson")
                    val presetId   = intent.getStringExtra("presetId")
                    val preset = when {
                        presetJson != null -> {
                            try {
                                Preset.fromJson(presetJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse presetJson on LOAD_PRESET: ${e.message}")
                                null
                            }
                        }
                        presetId != null -> Preset.builtInById(presetId)
                        else -> null
                    }
                    if (preset != null) {
                        dsp.loadPreset(preset)
                    }
                }
                "com.chaosvoice.app.START_CHAOS_MODE" -> {
                    val presetJson = intent.getStringExtra("presetJson")
                    val presetId   = intent.getStringExtra(EXTRA_PRESET)
                    val boost      = intent.getFloatExtra(EXTRA_BOOST, Preset.DEMON.boostPercent)
                    val preset = when {
                        presetJson != null -> {
                            try {
                                Preset.fromJson(presetJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse presetJson on START_CHAOS_MODE: ${e.message}")
                                Preset.DEMON
                            }
                        }
                        presetId != null -> Preset.builtInById(presetId)
                        else -> Preset.DEMON
                    }
                    transitionToActive(preset, boost)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        focusManager = AudioFocusManager(this) { focusChange -> onFocusChanged(focusChange) }
        
        // Register local command receiver to avoid startService background restrictions.
        // Bug 3 fix: START_CHAOS_MODE is added so MainActivity can trigger the ACTIVE
        // transition via broadcast instead of a second startForegroundService() call.
        val filter = android.content.IntentFilter().apply {
            addAction("com.chaosvoice.app.UPDATE_PARAMETER")
            addAction("com.chaosvoice.app.LOAD_PRESET")
            addAction("com.chaosvoice.app.START_CHAOS_MODE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        val routingFilter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(routingReceiver, routingFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(routingReceiver, routingFilter)
        }
        Log.i(TAG, "ChaosProjectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.w(TAG, "onStartCommand received null intent (sticky restart) — returning to STOPPED state")
            cleanupAndStop()
            return START_NOT_STICKY
        }
        val targetState = intent.getStringExtra(EXTRA_TARGET_STATE) ?: STATE_INIT

        // If targetState is STOPPED, satisfy the startForegroundService() OS contract
        // by calling startForeground() immediately before shutting down.
        // Bug 2 fix: wrapped in try-catch so any FGS-type violation or OEM restriction
        // fails down cleanly instead of crashing the process (25_ERROR_HANDLING.md).
        if (targetState == STATE_STOPPED) {
            try {
                startForegroundCompat(buildNotification("Stopping ChaosVoice…"))
            } catch (e: Exception) {
                Log.w(TAG, "startForeground on STOPPED path threw (ignored): ${e.message}")
            }
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val presetId = intent.getStringExtra(EXTRA_PRESET) ?: Preset.DEMON.id
        val boost = intent.getFloatExtra(EXTRA_BOOST, Preset.DEMON.boostPercent)

        Log.i(TAG, "onStartCommand targetState=$targetState presetId=$presetId boost=$boost")

        // Bug 3 fix: only INIT arrives via onStartCommand now.
        // ACTIVE transition is sent via START_CHAOS_MODE broadcast (see commandReceiver)
        // so we never call startForegroundService() a second time on an already-running service.
        when (targetState) {
            STATE_INIT -> transitionToInit()
            // STATE_ACTIVE path is dead from MainActivity, kept only for legacy deep-links / tests.
            STATE_ACTIVE -> {
                val preset = Preset.builtInById(presetId)
                transitionToActive(preset, boost)
            }
        }

        return START_STICKY
    }


    // ─────────────────────────────────────────────────────────────────────────
    // State transitions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * INIT: show foreground notification, no audio yet — used while permissions are being granted.
     *
     * Bug 1 fix: on API 34, startForeground() on a foreground service typed 'microphone'
     * throws SecurityException if RECORD_AUDIO is not already granted at this moment.
     * The Dart sequence now requests RECORD_AUDIO *before* calling startForegroundServiceOnly(),
     * so RECORD_AUDIO is guaranteed granted here. See ADR-004 and 14_PERMISSION_MODEL.md.
     *
     * Bug 2 fix: startForeground() is wrapped in try-catch. On any throw (OEM restriction,
     * FGS-type violation, etc.) we broadcast FGS_START_FAILED and call cleanupAndStop()
     * instead of letting the exception propagate and kill the process (25_ERROR_HANDLING.md).
     */
    private fun transitionToInit() {
        serviceState = ServiceState.INIT
        try {
            startForegroundCompat(buildNotification("ChaosVoice — ready"))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground in INIT failed: ${e.message}", e)
            broadcastError("FGS_START_FAILED",
                "ChaosVoice couldn't start the background service. " +
                "Please ensure microphone permission is granted and try again.")
            cleanupAndStop()
            return
        }
        broadcastStateChange(STATE_INIT)
        Log.i(TAG, "→ INIT state")
    }

    /**
     * Calls the correct [startForeground] overload for the current API level.
     *
     * On API 29+ (Android 10+), the overload that accepts [ServiceInfo] service type flags
     * must be used for microphone-typed foreground services. Both the manifest
     * `foregroundServiceType="microphone"` attribute AND this runtime flag are required —
     * they are independent enforcement points.
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
     * All audio-device errors produce a clean stop + user-visible error (25_ERROR_HANDLING.md).
     */
    private fun transitionToActive(preset: Preset, boost: Float) {
        dsp.loadPreset(preset)
        dsp.setParameter(ChaosDSP.Stage.BOOST_PERCENT, boost)

        // Request audio focus (12_AUDIO_ROUTING.md; Known Issue #2 fix)
        if (!focusManager.requestFocus()) {
            Log.w("[ChaosVoice][FOCUS]", "Audio focus denied, cannot start")
            broadcastError("FOCUS_DENIED", "Another app is using audio right now.")
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
            Log.e(TAG, "AudioRecord not in STATE_INITIALIZED")
            broadcastError("AUDIO_INIT_FAILED", "Couldn't access the microphone. Try restarting the app.")
            cleanupAndStop(); return
        }

        // ── AudioTrack ───────────────────────────────────────────────────────
        // USAGE_MEDIA routes output to the LOUDSPEAKER, not a call-specific path.
        // This is the acoustic loopback mechanism — intentional per ADR-002.
        // Known Issue #1 fix: switched from USAGE_VOICE_COMMUNICATION to USAGE_MEDIA.
        val minTrackBuf = AudioTrack.getMinBufferSize(
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
            Log.e(TAG, "AudioTrack not in STATE_INITIALIZED")
            broadcastError("AUDIO_INIT_FAILED", "Couldn't start audio playback. Please try again.")
            cleanupAndStop(); return
        }

        // ── Loudspeaker routing (12_AUDIO_ROUTING.md) ──────────────────────
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Keep MODE_NORMAL so output goes to standard loudspeaker path (not call path)
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = true

        // Prefer built-in speaker on API 28+ where setPreferredDevice is available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val speakerDevice = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            speakerDevice?.let { audioTrack?.preferredDevice = it }
        }

        // ── Wake lock ────────────────────────────────────────────────────────
        // Acquired WITHOUT a timeout so sessions longer than any fixed ceiling work.
        // The audio loop renews it every WAKE_LOCK_RENEW_INTERVAL_BUFFERS buffers.
        // Released explicitly in stopProcessingLoop().
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChaosVoice:AudioSession")
        @Suppress("WakelockTimeout")
        wakeLock?.acquire()

        serviceState = ServiceState.ACTIVE
        updateNotification("ChaosVoice is active — voice effects running")
        broadcastStateChange(STATE_ACTIVE)
        startProcessingLoop()
        Log.i(TAG, "→ ACTIVE state (preset=${preset.name}, boost=$boost%)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio processing loop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts the audio capture → DSP → playback loop on a dedicated high-priority thread.
     * Thread priority: THREAD_PRIORITY_URGENT_AUDIO (13_THREADING_MODEL.md).
     */

    private fun startProcessingLoop() {
        isRunning.set(true)
        audioThread = Thread {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                // Bug fix: wrap setThreadPriority — some OEMs restrict URGENT_AUDIO for
                // third-party apps and throw a SecurityException instead of silently failing.
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    Log.i(TAG, "Audio thread priority set to URGENT_AUDIO (tid=${Process.myTid()})")
                } catch (e: Exception) {
                    // Degrade gracefully to AUDIO (still higher than default)
                    try { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) } catch (_: Exception) {}
                    Log.w(TAG, "Could not set URGENT_AUDIO priority; degraded to AUDIO: ${e.message}")
                }

                val captureBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
                audioRecord?.startRecording()
                audioTrack?.play()

                var bufferCount = 0

                while (isRunning.get()) {
                    try {
                        // ── Re-assert speakerphone route (Bug 7) ─────────────
                        if (bufferCount % 25 == 0) {
                            @Suppress("DEPRECATION")
                            if (!am.isSpeakerphoneOn) {
                                am.mode = AudioManager.MODE_NORMAL
                                @Suppress("DEPRECATION")
                                am.isSpeakerphoneOn = true
                                Log.d(TAG, "Re-asserted speakerphone route mid-session")
                            }
                        }

                        val read = audioRecord?.read(captureBuffer, 0, BUFFER_SIZE_SAMPLES) ?: -1
                        if (read <= 0) {
                            // Underrun — skip gracefully, do not desync timing (10_AUDIO_PIPELINE.md)
                            Log.d(TAG, "AudioRecord read returned $read (underrun/overrun), skipping")
                            continue
                        }

                        // ── DSP chain runs in Kotlin only (ADR-001) ─────────────
                        val processed = dsp.process(captureBuffer)

                        // Write to loudspeaker via AudioTrack
                        audioTrack?.write(processed, 0, read)

                        // ── Wake lock renewal (bug fix: prevent silent expiry on long sessions)
                        // Renew every WAKE_LOCK_RENEW_INTERVAL_BUFFERS (~10 min) so we never
                        // rely on a timed lease that expires mid-session.
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
                        // Fail down cleanly — never crash the app (25_ERROR_HANDLING.md)
                        Handler(Looper.getMainLooper()).post {
                            broadcastError("AUDIO_INIT_FAILED", "Audio pipeline error. Please restart.")
                            cleanupAndStop()
                        }
                        break
                    }
                }
            } finally {
                // Bug fix: ensure wake lock is always released on thread termination to prevent leaks
                val wl = wakeLock
                if (wl != null && wl.isHeld) {
                    wl.release()
                    Log.i(TAG, "Wake lock released inside thread finally block")
                }
                Log.i(TAG, "Audio thread stopped")
            }
        }.also {
            it.name = "ChaosVoice-Audio"
            it.start()
        }
    }

    /**
     * Stops the audio loop, releases all resources, and resets DSP state.
     */
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
        // Bug fix: safely release wake lock checking isHeld to avoid under-locked exception
        val wl = wakeLock
        if (wl != null && wl.isHeld) {
            wl.release()
        }
        wakeLock = null

        dsp.reset()   // clear stateful buffers for next session
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Focus change handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles [AudioManager.OnAudioFocusChangeListener] callbacks.
     * Per 12_AUDIO_ROUTING.md and 25_ERROR_HANDLING.md.
     */
    private fun onFocusChanged(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i("[ChaosVoice][FOCUS]", "Focus GAINED — resuming playback")
                if (serviceState == ServiceState.ACTIVE) audioTrack?.play()
                MainActivity.sendFocusEvent("GAINED")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w("[ChaosVoice][FOCUS]", "Focus LOST_TRANSIENT — pausing playback")
                audioTrack?.pause()
                MainActivity.sendFocusEvent("LOST_TRANSIENT")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w("[ChaosVoice][FOCUS]", "Focus LOST permanently — stopping session")
                MainActivity.sendFocusEvent("LOST")
                cleanupAndStop()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parameter update (called from MainActivity → MethodChannel)
    // ─────────────────────────────────────────────────────────────────────────

    /** Thread-safe DSP parameter update, forwarded from the MethodChannel handler. */
    fun updateDspParameter(stage: ChaosDSP.Stage, value: Float) =
        dsp.setParameter(stage, value)

    /** Loads a complete preset during an active session. */
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
        Log.i(TAG, "→ STOPPED state — service torn down")
    }

    override fun onDestroy() {
        stopProcessingLoop()
        // Bug fix: ensure STATE_STOPPED is broadcast even when stopped via stopService()
        serviceState = ServiceState.STOPPED
        broadcastStateChange(STATE_STOPPED)
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            unregisterReceiver(routingReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        Log.i(TAG, "ChaosProjectionService destroyed")
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers (15_BACKGROUND_SERVICE.md)
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
