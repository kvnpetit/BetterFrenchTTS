package io.github.kvnpetit.betterfrenchtts.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Selects the best available offline French voice from the device's TTS engine.
 *
 * The selection algorithm:
 * 1. Keeps French voices, excluding network-required/uninstalled voices in offline mode.
 * 2. Prefers the requested locale, installed data, quality, latency, then a stable name.
 * 3. Among the highest-quality voices, picks the first match from [preferredVoiceNames].
 * 4. Falls back to the first available voice if no preferred name matches.
 *
 * @property preferredVoiceNames Ordered list of voice names to prefer; defaults to known Google
 *   French names.
 * @see io.github.kvnpetit.betterfrenchtts.BetterFrenchTts.Config.preferredVoiceNames
 */
class FrenchVoiceSelector(
    private val preferredVoiceNames: List<String> = DEFAULT_PREFERRED_VOICES,
    private val locale: Locale = Locale.FRANCE,
    private val offlineOnly: Boolean = true,
    private val requireExactLocale: Boolean = false,
) {
    companion object {
        /**
         * Default preferred voice names (Google TTS high-quality French voices).
         *
         * These are tried in order among the top-quality voices available on the device.
         */
        val DEFAULT_PREFERRED_VOICES =
            listOf("fr-fr-x-frd-local", "fr-fr-x-fra-local", "fr-fr-x-frb-local")
    }

    /**
     * Returns the best offline French voice available on this device, or `null` if none is found.
     *
     * Priority: requested locale → installed data → quality → preferred voice name → latency.
     *
     * @param tts An initialized [TextToSpeech] instance.
     * @return The selected [Voice], or `null` if no offline French voice exists on the device.
     */
    fun selectBestVoice(tts: TextToSpeech): Voice? {
        val voices = listFrenchVoices(tts)
        if (voices.isEmpty()) return null

        // Among top quality voices, prefer known good ones
        val best = voices.first()
        val topVoices = voices.filter { it.locale == best.locale && it.quality == best.quality }

        for (preferred in preferredVoiceNames) {
            val match = topVoices.find { it.name.equals(preferred, ignoreCase = true) }
            if (match != null) return match
        }

        return topVoices.first()
    }

    /**
     * Returns eligible French voices, preferring the requested locale, installed data, quality,
     * latency and a stable name. Network voices require offlineOnly=false.
     *
     * @param tts An initialized [TextToSpeech] instance.
     * @return A list of [Voice] objects, or an empty list if none are available.
     */
    fun listFrenchVoices(tts: TextToSpeech): List<Voice> {
        return tts.voices
            ?.filter {
                isFrenchVoice(it) &&
                    (!requireExactLocale || it.locale == locale) &&
                    (!offlineOnly ||
                        !it.isNetworkConnectionRequired &&
                            !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
            }
            ?.sortedWith(
                compareBy<Voice> { it.locale != locale }
                    .thenBy { it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            ) ?: emptyList()
    }

    private fun isFrenchVoice(voice: Voice): Boolean {
        val locale = voice.locale
        return locale.language == Locale.FRENCH.language
    }
}
