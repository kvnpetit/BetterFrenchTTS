package com.github.kvnpetit.betterfrenchtts

/**
 * Named prosody preset with rate, pitch and volume settings.
 *
 * Use the built-in constants (e.g. [CALM], [STORYTELLING]) or create your own:
 * ```
 * val custom = SpeechPreset("Robot", rate = "fast", pitch = "+6st", volume = "loud")
 * ```
 */
data class SpeechPreset(
    val name: String,
    val rate: String,
    val pitch: String,
    val volume: String
) {
    companion object {
        val NEUTRAL = SpeechPreset("Neutral", rate = "medium", pitch = "+0st", volume = "medium")
        val CALM = SpeechPreset("Calm", rate = "slow", pitch = "-1st", volume = "soft")
        val EXCITED = SpeechPreset("Excited", rate = "fast", pitch = "+4st", volume = "loud")
        val TEACHING = SpeechPreset("Teaching", rate = "85%", pitch = "+1st", volume = "medium")
        val STORYTELLING = SpeechPreset("Storytelling", rate = "90%", pitch = "+2st", volume = "medium")
        val NEWS = SpeechPreset("News", rate = "medium", pitch = "+0st", volume = "loud")
        val WHISPER = SpeechPreset("Whisper", rate = "slow", pitch = "-2st", volume = "x-soft")
        val ANNOUNCEMENT = SpeechPreset("Announcement", rate = "slow", pitch = "-1st", volume = "x-loud")
        val READING = SpeechPreset("Reading", rate = "95%", pitch = "+0st", volume = "medium")
        val DICTATION = SpeechPreset("Dictation", rate = "70%", pitch = "+0st", volume = "loud")
        val NOTIFICATION = SpeechPreset("Notification", rate = "fast", pitch = "+1st", volume = "loud")
        val MEDITATION = SpeechPreset("Meditation", rate = "x-slow", pitch = "-3st", volume = "soft")

        val builtIn: List<SpeechPreset> = listOf(
            NEUTRAL, CALM, EXCITED, TEACHING, STORYTELLING, NEWS,
            WHISPER, ANNOUNCEMENT, READING, DICTATION, NOTIFICATION, MEDITATION
        )
    }
}
