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
 * ChaosProjectionService — foreground service owning the live audio pipeline.
 *
 * FIX: All event broadcasts are now posted to the main Looper so that
 * EventChannel.EventSink.success() is always called on the platform thread,
 * preventing silent event drops.
 *
 * FIX: AudioRecord and AudioTrack are initialized INSIDE the audio thread,
 * not on the main thread during onStartCommand. This avoids:
 *   - Blocking the main thread during hardware init (~50–200ms)
 *   - Some OEM restrictions on AudioRecord construction off the audio thread
 *   - SecurityExceptions being thrown synchronously on the main thread before
 *     the FGS microphone type is fully registered by the OS.
 *
 * FIX: Added explicit null-check of EventSink with a retry loop so startup
 * events (INIT, ACTIVE) are never silently dropped if the Dart stream
 * subscription hasn't completed its platform handshake yet.
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

        // ── Audio configuration (22_CONFIGURATION.md) ─────────────────────────
        // 48 kHz, mono, 16-bit. Buffer = 1920 samples = ~40 ms.
        private const val SAMPLE_RATE         = 48_000
        private const val BUFFER_SIZE_SAMPLES = 1_920

        // Wake lock renewal: every ~10 min at 25 buffers/sec
        private const val WAKE_LOCK_RENEW_INTERVAL_BUFFERS = 15_000
    }

    enum class ServiceState { INIT, ACTIVE, STOPPED }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceState = ServiceState.STOPPED
    private val dsp = ChaosDSP(SAMPLE_RATE)
    private lateinit var focusManager: AudioFocusManager
    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Preset/boost extracted from intent; read by audio thread at startup.
    @Volatile private var pendingPreset: Preset = Preset.DEMON
    @Volatile private var pendingBoost: Float = Preset.DEMON.boostPercent

    // Routing receiver: registered ONLY after ACTIVE state is confirmed.
    private var routingReceiverRegistered = false
    private val routingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
                Log.w(TAG, "Headphones/BT detected — terminating session")
                postError("ROUTING_UNSUPPORTED",
                    "Headphones/Bluetooth detected. Chaos Mode requires speakerphone.")
                mainHandler.post { cleanupAndStop() }
            }
        }
    }

    // Command receiver: live parameter/preset updates from MainActivity.
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.chaosvoice.app.UPDATE_PARAMETER" -> {
                    val stageKey = intent.getStringExtra("stage") ?: return
                    val value    = intent.getFloatExtra("value", 0f)
                    val stage    = ChaosDSP.Stage.fromKey(stageKey)
                    if (stage != null) {
                        dsp.setParameter(stage, value)
                        Log.d(TAG, "UPDATE_PARAMETER $stageKey=$value")
                    } else {
                        Log.w(TAG, "UPDATE_PARAMETER: unknown stage '$stageKey'")
                    }
                }
                "com.chaosvoice.app.LOAD_PRESET" -> {
                    val presetJson = intent.getStringExtra("presetJson")
                    val presetId   = intent.getStringExtra("presetId")
                    val preset = when {
                        presetJson != null -> Preset.fromJson(presetJson)
                        presetId   != null -> Preset.builtInById(presetId)
                        else               -> null
                    }
                    if (preset != null) {
                        dsp.loadPreset(preset)
                        Log.i(TAG, "LOAD_PRESET: ${preset.name}")
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
        Log.i(TAG, "onCreate")
        createNotificationChannel()
        focusManager = AudioFocusManager(this) { focusChange -> onFocusChanged(focusChange) }

        val filter = IntentFilter().apply {
            addAction("com.chaosvoice.app.UPDATE_PARAMETER")
            addAction("com.chaosvoice.app.LOAD_PRESET")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand flags=$flags startId=$startId intent=${intent?.action}")

        if (intent == null) {
            // Sticky restart with null intent — satisfy FGS contract then stop.
            Log.w(TAG, "null intent restart — stopping")
            try { startForegroundCompat(buildNotification("ChaosVoice")) } catch (_: Exception) {}
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val targetState = intent.getStringExtra(EXTRA_TARGET_STATE) ?: STATE_ACTIVE
        Log.i(TAG, "onStartCommand targetState=$targetState")

        if (targetState == STATE_STOPPED) {
            try { startForegroundCompat(buildNotification("Stopping ChaosVoice…")) } catch (_: Exception) {}
            cleanupAndStop()
            return START_NOT_STICKY
        }

        // ── Parse preset and boost from intent ───────────────────────────────
        val presetJson = intent.getStringExtra(EXTRA_PRESET_JSON)
        val presetId   = intent.getStringExtra(EXTRA_PRESET) ?: Preset.DEMON.id
        val boost      = intent.getFloatExtra(EXTRA_BOOST, Preset.DEMON.boostPercent)

        pendingPreset = when {
            presetJson != null -> {
                try { Preset.fromJson(presetJson) }
                catch (e: Exception) {
                    Log.w(TAG, "Failed to parse presetJson: ${e.message}")
                    Preset.builtInById(presetId)
                }
            }
            else -> Preset.builtInById(presetId)
        }
        pendingBoost = boost
        Log.i(TAG, "Pending preset=${pendingPreset.name} boost=$pendingBoost")

        // ── Step 1: Call startForeground immediately (OS contract) ───────────
        // This MUST happen within 5 seconds of startForegroundService().
        // We do it first, before any other work.
        try {
            startForegroundCompat(buildNotification("ChaosVoice — starting…"))
            Log.i(TAG, "startForeground OK")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground FAILED: ${e.message}", e)
            postError("FGS_START_FAILED",
                "ChaosVoice couldn't start. Ensure mic permission is granted and try again.")
            stopSelf()
            return START_NOT_STICKY
        }

        // ── Step 2: Broadcast INIT state ─────────────────────────────────────
        // Post to main handler so EventSink.success() is always on platform thread.
        // The 50ms delay gives Flutter's EventChannel subscription time to complete
        // its platform handshake — the Dart receiveBroadcastStream() call is
        // asynchronous; the native onListen() callback may not have fired yet
        // if startChaosMode was called immediately after app launch.
        serviceState = ServiceState.INIT
        mainHandler.postDelayed({
            Log.i(TAG, "Posting INIT event to Flutter")
            MainActivity.sendServiceStateEvent(STATE_INIT)
        }, 50)

        // ── Step 3: Launch audio thread — all AudioRecord/AudioTrack init ─────
        // happens inside the thread, NOT on the main thread, so:
        //   a) We don't block the UI thread during hardware initialization
        //   b) AudioRecord is constructed on the thread that will call read()
        //      (some OEM audio HALs require this)
        //   c) SecurityException from mic access is handled inside the thread
        //      and reported via postError() → mainHandler
        startAudioThread()

        return START_STICKY
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio thread — init + loop
    // ─────────────────────────────────────────────────────────────────────────

    private fun startAudioThread() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "startAudioThread: already running, ignoring")
            return
        }

        // Snapshot preset/boost before the thread starts
        val preset = pendingPreset
        val boost  = pendingBoost

        audioThread = Thread({
            Log.i(TAG, "Audio thread started (tid=${Process.myTid()})")

            // ── Set thread priority ───────────────────────────────────────────
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                Log.i(TAG, "Thread priority: URGENT_AUDIO")
            } catch (e: Exception) {
                Log.w(TAG, "URGENT_AUDIO failed, trying AUDIO: ${e.message}")
                try { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
                catch (_: Exception) {}
            }

            // ── Request audio focus ───────────────────────────────────────────
            // AudioFocusManager is main-thread safe; requestFocus() is synchronous.
            // We call it from the audio thread but AudioManager operations are
            // binder calls to the system server, safe from any thread.
            val focusGranted = try {
                focusManager.requestFocus()
            } catch (e: Exception) {
                Log.e(TAG, "requestFocus threw: ${e.message}", e)
                false
            }
            if (!focusGranted) {
                Log.w(TAG, "Audio focus denied")
                postError("FOCUS_DENIED", "Another app is using audio. Close it and try again.")
                mainHandler.post { cleanupAndStop() }
                return@Thread
            }
            Log.i(TAG, "Audio focus granted")

            // ── Load preset into DSP ──────────────────────────────────────────
            dsp.loadPreset(preset)
            dsp.setParameter(ChaosDSP.Stage.BOOST_PERCENT, boost)
            Log.i(TAG, "DSP preset loaded: ${preset.name}")

            // ── Initialize AudioRecord ────────────────────────────────────────
            val byteBufSize  = BUFFER_SIZE_SAMPLES * 2 // 16-bit = 2 bytes/sample
            val minRecordBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            Log.i(TAG, "AudioRecord minBufSize=$minRecordBuf byteBufSize=$byteBufSize")

            if (minRecordBuf == AudioRecord.ERROR_BAD_VALUE ||
                minRecordBuf == AudioRecord.ERROR) {
                Log.e(TAG, "AudioRecord.getMinBufferSize returned error: $minRecordBuf")
                postError("AUDIO_INIT_FAILED",
                    "Microphone is unavailable. Check mic permission and try again.")
                mainHandler.post { cleanupAndStop() }
                return@Thread
            }
            val recordBufSize = maxOf(byteBufSize, minRecordBuf)

            val record: AudioRecord
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufSize
                )
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord constructor threw: ${e.message}", e)
                postError("AUDIO_INIT_FAILED",
                    "Couldn't access microphone: ${e.message}")
                mainHandler.post { cleanupAndStop() }
                return@Thread
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized (state=${record.state})")
                record.release()
                postError("AUDIO_INIT_FAILED",
                    "Microphone couldn't be initialized. Restart app and try again.")
                mainHandler.post { cleanupAndStop() }
                return@Thread
            }
            Log.i(TAG, "AudioRecord initialized (state=${record.state})")

            // ── Initialize AudioTrack ─────────────────────────────────────────
            val minTrackBuf  = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBufSize = maxOf(byteBufSize * 2, minTrackBuf)
            Log.i(TAG, "AudioTrack minBufSize=$minTrackBuf trackBufSize=$trackBufSize")

            val track: AudioTrack
            try {
                track = AudioTrack.Builder()
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
                Log.e(TAG, "AudioTrack constructor threw: ${e.message}", e)
                record.release()
                postError("AUDIO_INIT_FAILED",
                    "Couldn't start audio output: ${e.message}")
                mainHandler.post { cleanupAndStop() }
                return@Thread
            }

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized (state=${track.state})")
                track.release(); record.release()
                postError("AUDIO_INIT_FAILED",
                    "Audio output couldn't be initialized. Restart app and try again.")
                mainHandler.post { cleanupAndStop() }
                return@Thread
            }
            Log.i(TAG, "AudioTrack initialized (state=${track.state})")

            // ── Route to loudspeaker ──────────────────────────────────────────
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val speakerDevice = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speakerDevice != null) {
                    track.preferredDevice = speakerDevice
                    Log.i(TAG, "AudioTrack preferred device → BUILTIN_SPEAKER")
                }
            }

            // ── Acquire wake lock ─────────────────────────────────────────────
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChaosVoice:AudioSession")
            @Suppress("WakelockTimeout")
            wl.acquire()
            wakeLock = wl
            Log.i(TAG, "Wake lock acquired")

            // ── Transition to ACTIVE — post on main thread ────────────────────
            // EventSink.success() MUST be called on the platform thread.
            // We also register the routing receiver here on the main thread.
            mainHandler.post {
                serviceState = ServiceState.ACTIVE
                updateNotification("ChaosVoice is active — voice effects running")
                Log.i(TAG, "Posting ACTIVE event to Flutter")
                MainActivity.sendServiceStateEvent(STATE_ACTIVE)

                // Register routing receiver (post-ACTIVE to avoid sticky broadcast trigger)
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
                    Log.i(TAG, "Routing receiver registered")
                }
            }

            // ── Start recording and playback ─────────────────────────────────
            record.startRecording()
            track.play()
            Log.i(TAG, "AudioRecord started, AudioTrack playing → audio loop running")

            val captureBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
            var bufferCount   = 0

            // ── Main audio processing loop ───────────────────────────────────
            try {
                while (isRunning.get()) {
                    // Re-assert speakerphone routing every ~1 second
                    if (bufferCount % 25 == 0) {
                        @Suppress("DEPRECATION")
                        if (!am.isSpeakerphoneOn) {
                            am.mode = AudioManager.MODE_NORMAL
                            @Suppress("DEPRECATION")
                            am.isSpeakerphoneOn = true
                        }
                    }

                    val read = record.read(captureBuffer, 0, BUFFER_SIZE_SAMPLES)
                    if (read <= 0) {
                        if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                            read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord.read fatal error: $read")
                            postError("AUDIO_INIT_FAILED", "Microphone read error. Restarting.")
                            break
                        }
                        Log.d(TAG, "AudioRecord.read=$read (underrun), skipping frame")
                        continue
                    }

                    val processed = dsp.process(captureBuffer)
                    track.write(processed, 0, read)

                    bufferCount++
                    if (bufferCount >= WAKE_LOCK_RENEW_INTERVAL_BUFFERS) {
                        bufferCount = 0
                        if (wl.isHeld) { wl.release(); @Suppress("WakelockTimeout") wl.acquire() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio loop exception: ${e.message}", e)
                postError("AUDIO_INIT_FAILED", "Audio error: ${e.message}. Please restart.")
            } finally {
                Log.i(TAG, "Audio loop ended — cleaning up hardware")
                record.stop(); record.release()
                track.stop();  track.release()
                focusManager.abandonFocus()
                if (wl.isHeld) wl.release()
                wakeLock = null
                Log.i(TAG, "Audio thread finished cleanup")
                // Notify Flutter on main thread
                mainHandler.post {
                    if (serviceState != ServiceState.STOPPED) {
                        serviceState = ServiceState.STOPPED
                        MainActivity.sendServiceStateEvent(STATE_STOPPED)
                    }
                }
            }
        }, "ChaosVoice-Audio")

        audioThread!!.start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Focus changes
    // ─────────────────────────────────────────────────────────────────────────

    private fun onFocusChanged(focusChange: Int) {
        // AudioFocusManager callback arrives on binder thread; post to main.
        mainHandler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.i(TAG, "Audio focus GAINED")
                    MainActivity.sendFocusEvent("GAINED")
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.w(TAG, "Audio focus LOST_TRANSIENT")
                    MainActivity.sendFocusEvent("LOST_TRANSIENT")
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.w(TAG, "Audio focus LOST permanently — stopping")
                    MainActivity.sendFocusEvent("LOST")
                    cleanupAndStop()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    private fun cleanupAndStop() {
        Log.i(TAG, "cleanupAndStop() serviceState=$serviceState")
        // Signal audio thread to stop
        isRunning.set(false)
        // Give audio thread up to 2s to finish its finally block
        audioThread?.join(2000L)
        audioThread = null

        // Release wake lock if audio thread didn't clean it up
        val wl = wakeLock
        if (wl != null && wl.isHeld) { wl.release(); wakeLock = null }

        // DSP reset
        dsp.reset()

        // Broadcast STOPPED (always on main thread — cleanupAndStop is called
        // from mainHandler.post{} or from onStartCommand/onDestroy)
        if (serviceState != ServiceState.STOPPED) {
            serviceState = ServiceState.STOPPED
            MainActivity.sendServiceStateEvent(STATE_STOPPED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        Log.i(TAG, "→ STOPPED, stopSelf() called")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        isRunning.set(false)
        audioThread?.join(1000L)
        audioThread = null
        val wl = wakeLock
        if (wl != null && wl.isHeld) { wl.release(); wakeLock = null }
        dsp.reset()
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        if (routingReceiverRegistered) {
            try { unregisterReceiver(routingReceiver) } catch (_: Exception) {}
            routingReceiverRegistered = false
        }
        if (serviceState != ServiceState.STOPPED) {
            serviceState = ServiceState.STOPPED
            // Post with slight delay; Flutter engine may be shutting down.
            mainHandler.postDelayed({
                MainActivity.sendServiceStateEvent(STATE_STOPPED)
            }, 100)
        }
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Post an error event to Flutter on the platform thread. */
    private fun postError(code: String, message: String) {
        mainHandler.post {
            Log.e(TAG, "Error event → Flutter: code=$code msg=$message")
            MainActivity.sendErrorEvent(code, message)
        }
    }

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

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

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
}
