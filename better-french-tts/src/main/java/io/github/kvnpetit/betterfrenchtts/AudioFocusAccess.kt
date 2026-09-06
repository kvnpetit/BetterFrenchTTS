package io.github.kvnpetit.betterfrenchtts

import android.media.AudioFocusRequest
import android.media.AudioManager

/** Platform seam for deterministic focus denial/loss tests. */
internal interface AudioFocusAccess {
    fun request(request: AudioFocusRequest, listener: AudioManager.OnAudioFocusChangeListener): Int
    fun abandon(request: AudioFocusRequest)
}
