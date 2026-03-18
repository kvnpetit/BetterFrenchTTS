package com.github.kvnpetit.betterfrenchtts.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Selects the best available offline French voice from the device's TTS engine.
 *
 * The selection algorithm:
 * 1. Filters voices to keep only **offline** French voices (fr-FR, fr-CA, fr-BE, fr-CH).
 * 2. Sorts by [Voice.getQuality] descending, then favours installed voices.
 * 3. Among the highest-quality voices, picks the first match from [preferredVoiceNames].
 * 4. Falls back to the first available voice if no preferred name matches.
 *
 * @property preferredVoiceNames Ordered list of voice names to prefer. Defaults to [DEFAULT_PREFERRED_VOICES].
 * @see com.github.kvnpetit.betterfrenchtts.BetterFrenchTts.Config.preferredVoiceNames
 */
class FrenchVoiceSelector(
    private val preferredVoiceNames: List<String> = DEFAULT_PREFERRED_VOICES
) {
    companion object {
        /** French-speaking country codes considered valid (empty string = language-only locale). */
        private val FRENCH_COUNTRIES = setOf("", "FR", "CA", "BE", "CH")

        /**
         * Default preferred voice names (Google TTS high-quality French voices).
         *
         * These are tried in order among the top-quality voices available on the device.
         */
        val DEFAULT_PREFERRED_VOICES = listOf(
            "fr-fr-x-frd-local",
            "fr-fr-x-fra-local",
            "fr-fr-x-frb-local",
        )
    }

    /**
     * Returns the best offline French voice available on this device, or `null` if none is found.
     *
     * Priority: highest quality → preferred voice name → first available.
     *
     * @param tts An initialized [TextToSpeech] instance.
     * @return The selected [Voice], or `null` if no offline French voice exists on the device.
     */
    fun selectBestVoice(tts: TextToSpeech): Voice? {
        val voices = listFrenchVoices(tts)
        if (voices.isEmpty()) return null

        // Among top quality voices, prefer known good ones
        val topQuality = voices.first().quality
        val topVoices = voices.filter { it.quality == topQuality }

        for (preferred in preferredVoiceNames) {
            val match = topVoices.find { it.name.equals(preferred, ignoreCase = true) }
            if (match != null) return match
        }

        return topVoices.first()
    }

    /**
     * Returns all offline French voices available on the device, sorted by quality (descending)
     * then by installation status (installed first).
     *
     * @param tts An initialized [TextToSpeech] instance.
     * @return A list of [Voice] objects, or an empty list if none are available.
     */
    fun listFrenchVoices(tts: TextToSpeech): List<Voice> {
        return tts.voices
            ?.filter { isFrenchVoice(it) && !it.isNetworkConnectionRequired }
            ?.sortedWith(compareByDescending<Voice> { it.quality }
                .thenBy { it.features.contains("notInstalled") })
            ?: emptyList()
    }

    private fun isFrenchVoice(voice: Voice): Boolean {
        val locale = voice.locale
        return locale.language == Locale.FRENCH.language &&
                locale.country.uppercase() in FRENCH_COUNTRIES
    }
}
