package io.github.kvnpetit.betterfrenchtts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler

/** Owns the focus lease; the facade decides whether a loss stops or pauses speech. */
internal class SpeechAudioFocus(
    context: Context,
    private val mode: BetterFrenchTts.AudioFocusMode,
    private val lossBehavior: BetterFrenchTts.FocusLossBehavior,
    private val attributes: AudioAttributes,
    private val handler: Handler,
    access: AudioFocusAccess?,
    private val onLoss: (Int) -> Unit,
) {
    private val manager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusAccess =
        access
            ?: object : AudioFocusAccess {
                override fun request(
                    request: AudioFocusRequest,
                    listener: AudioManager.OnAudioFocusChangeListener,
                ) = manager.requestAudioFocus(request)

                override fun abandon(request: AudioFocusRequest) {
                    manager.abandonAudioFocusRequest(request)
                }
            }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    fun request(): Boolean {
        if (mode == BetterFrenchTts.AudioFocusMode.NONE || hasAudioFocus) return true

        val focusGain =
            when (mode) {
                BetterFrenchTts.AudioFocusMode.DUCK ->
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                BetterFrenchTts.AudioFocusMode.GAIN_TRANSIENT ->
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                BetterFrenchTts.AudioFocusMode.NONE -> return true
            }

        lateinit var request: AudioFocusRequest
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (audioFocusRequest === request && change < 0) {
                onLoss(change)
            }
        }
        request =
            AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(lossBehavior != BetterFrenchTts.FocusLossBehavior.IGNORE)
                .setOnAudioFocusChangeListener(listener, handler)
                .build()

        audioFocusRequest = request
        hasAudioFocus =
            focusAccess.request(request, listener) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) audioFocusRequest = null
        return hasAudioFocus
    }

    fun abandon() {
        if (!hasAudioFocus) return
        audioFocusRequest?.let { focusAccess.abandon(it) }
        audioFocusRequest = null
        hasAudioFocus = false
    }
}
