package io.github.kvnpetit.betterfrenchtts

/**
 * Named prosody preset with rate, pitch and volume settings for SSML `<prosody>` generation.
 *
 * The library ships with 12 built-in presets accessible via the companion object. Custom presets
 * can be created freely:
 * ```kotlin
 * val robot = SpeechPreset("Robot", rate = "fast", pitch = "+6st", volume = "loud")
 * tts.speak("Bip boup", preset = robot)
 * ```
 *
 * @property name Human-readable label (used for display / debugging).
 * @property rate SSML rate value: `"x-slow"`, `"slow"`, `"medium"`, `"fast"`, `"x-fast"`, or a
 *   percentage (`"85%"`).
 * @property pitch SSML pitch value in semitones relative to default (e.g. `"+2st"`, `"-1st"`,
 *   `"+0st"`).
 * @property volume SSML volume value: `"x-soft"`, `"soft"`, `"medium"`, `"loud"`, or `"x-loud"`.
 * @see BetterFrenchTts.speak
 * @see io.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder.withPreset
 */
data class SpeechPreset(val name: String, val rate: String, val pitch: String, val volume: String) {
    companion object {
        /** Default balanced preset — normal speech. */
        val NEUTRAL = SpeechPreset("Neutral", rate = "medium", pitch = "+0st", volume = "medium")
        /** Slow, soft, slightly lower pitch — relaxing tone. */
        val CALM = SpeechPreset("Calm", rate = "slow", pitch = "-1st", volume = "soft")
        /** Fast, high pitch, loud — energetic tone. */
        val EXCITED = SpeechPreset("Excited", rate = "fast", pitch = "+4st", volume = "loud")
        /** Slightly slower (85%), gentle pitch raise — clear explanations. */
        val TEACHING = SpeechPreset("Teaching", rate = "85%", pitch = "+1st", volume = "medium")
        /** Slightly slower (90%), warm pitch — narrative tone. */
        val STORYTELLING =
            SpeechPreset("Storytelling", rate = "90%", pitch = "+2st", volume = "medium")
        /** Normal speed, loud — broadcast style. */
        val NEWS = SpeechPreset("News", rate = "medium", pitch = "+0st", volume = "loud")
        /** Slow, very soft, lower pitch — whispering. */
        val WHISPER = SpeechPreset("Whisper", rate = "slow", pitch = "-2st", volume = "x-soft")
        /** Slow, very loud, slightly lower pitch — public announcement. */
        val ANNOUNCEMENT =
            SpeechPreset("Announcement", rate = "slow", pitch = "-1st", volume = "x-loud")
        /** Nearly normal speed (95%), neutral — comfortable reading. */
        val READING = SpeechPreset("Reading", rate = "95%", pitch = "+0st", volume = "medium")
        /** Very slow (70%), loud — word-by-word dictation. */
        val DICTATION = SpeechPreset("Dictation", rate = "70%", pitch = "+0st", volume = "loud")
        /** Fast, slightly higher pitch, loud — short alert. */
        val NOTIFICATION =
            SpeechPreset("Notification", rate = "fast", pitch = "+1st", volume = "loud")
        /** Very slow, soft, deep pitch — guided meditation. */
        val MEDITATION =
            SpeechPreset("Meditation", rate = "x-slow", pitch = "-3st", volume = "soft")

        /** All 12 built-in presets, useful for building a preset picker UI. */
        val builtIn: List<SpeechPreset> =
            listOf(
                NEUTRAL,
                CALM,
                EXCITED,
                TEACHING,
                STORYTELLING,
                NEWS,
                WHISPER,
                ANNOUNCEMENT,
                READING,
                DICTATION,
                NOTIFICATION,
                MEDITATION,
            )
    }
}
