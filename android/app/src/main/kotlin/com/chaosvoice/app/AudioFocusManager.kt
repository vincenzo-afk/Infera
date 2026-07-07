package com.chaosvoice.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.media.AudioFocusRequest
import android.util.Log

/**
 * Manages system audio focus for the ChaosVoice session.
 *
 * Per 12_AUDIO_ROUTING.md and 25_ERROR_HANDLING.md:
 * - AUDIOFOCUS_GAIN requested at session start
 * - AUDIOFOCUS_LOSS_TRANSIENT → pause output (call ringtone, etc.)
 * - AUDIOFOCUS_LOSS → stop session cleanly
 *
 * Known Issue #2 fix: this class replaces the prior missing AudioFocusRequest handling.
 */
class AudioFocusManager(
    context: Context,
    /** Called by the system when audio focus changes; forwarded to [ChaosProjectionService]. */
    private val onFocusChange: (Int) -> Unit
) {
    companion object {
        private const val TAG = "[ChaosVoice][FOCUS]"
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null  // API 26+

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.i(TAG, "Audio focus changed: $focusChange")
        onFocusChange(focusChange)
    }

    /**
     * Requests [AudioManager.AUDIOFOCUS_GAIN] from the system.
     *
     * @return `true` if focus was granted immediately; `false` if denied or delayed
     */
    fun requestFocus(): Boolean {
        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest = req
            result = audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "requestFocus result=$result granted=$granted")
        return granted
    }

    /**
     * Releases audio focus. Called when the session ends cleanly.
     */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                focusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        Log.i(TAG, "Audio focus abandoned")
    }
}
