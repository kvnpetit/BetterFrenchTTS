package com.github.kvnpetit.betterfrenchtts.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Selects the best available offline French voice from the device's TTS engine.
 *
 * Voices are ranked by quality (highest first), then by preference order.
 * Only offline voices are considered by default.
 */
class FrenchVoiceSelector(
    private val preferredVoiceNames: List<String> = DEFAULT_PREFERRED_VOICES
) {
    companion object {
        private val FRENCH_COUNTRIES = setOf("", "FR", "CA", "BE", "CH")

        val DEFAULT_PREFERRED_VOICES = listOf(
            "fr-fr-x-frd-local",
            "fr-fr-x-fra-local",
            "fr-fr-x-frb-local",
        )
    }

    /**
     * Returns the best offline French voice available on this device.
     * Priority: highest quality > preferred voice name > first available.
     */
    fun selectBestVoice(tts: TextToSpeech): Voice? {
        val offlineVoices = getOfflineFrenchVoices(tts)
        if (offlineVoices.isEmpty()) return null

        // Highest quality first, prefer installed over not-installed
        val byQuality = offlineVoices
            .sortedWith(compareByDescending<Voice> { it.quality }
                .thenBy { it.features.contains("notInstalled") })

        // Among top quality voices, prefer known good ones
        val topQuality = byQuality.first().quality
        val topVoices = byQuality.filter { it.quality == topQuality }

        for (preferred in preferredVoiceNames) {
            val match = topVoices.find { it.name.equals(preferred, ignoreCase = true) }
            if (match != null) return match
        }

        return topVoices.first()
    }

    /** Returns all offline French voices sorted by quality (descending). */
    fun listFrenchVoices(tts: TextToSpeech): List<Voice> {
        return getOfflineFrenchVoices(tts)
    }

    private fun getOfflineFrenchVoices(tts: TextToSpeech): List<Voice> {
        return tts.voices
            ?.filter { isFrenchVoice(it) && !it.isNetworkConnectionRequired }
            ?.sortedByDescending { it.quality }
            ?: emptyList()
    }

    private fun isFrenchVoice(voice: Voice): Boolean {
        val locale = voice.locale
        return locale.language == Locale.FRENCH.language &&
                locale.country.uppercase() in FRENCH_COUNTRIES
    }
}
