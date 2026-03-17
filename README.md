# BetterFrenchTTS

**An intelligent wrapper around Android's native TTS, optimized for French.**

BetterFrenchTTS turns Android speech synthesis into a high-quality vocal experience for French. No more verbose SSML, robotic default voices, or manual tuning — the library handles everything internally.

---

## Why?

Android's native TTS exposes only 2 parameters (`pitch` and `speechRate`), no contextual modulation, no smart voice selection. Going further requires hand-writing SSML — verbose, fragile, and poorly documented.

BetterFrenchTTS solves this with:

- **A Kotlin DSL** that generates SSML automatically
- **12 expressive presets** + ability to create custom ones
- **Automatic selection** of the best offline French voice
- **Smart spell-out** with 200+ characters mapped to French pronunciation
- **Auto-chunking** of long texts (TTS ~4000 char limit)
- **Coroutines support** with `speakAndAwait()`
- **Zero cloud dependency**, 100% offline, negligible size

---

## Installation

### Local module

The `better-french-tts` module is included directly in the project. Add the dependency in your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":better-french-tts"))
}
```

Verify that `settings.gradle.kts` includes the module:

```kotlin
include(":better-french-tts")
```

---

## Quick Start

### 1. Initialize

```kotlin
val tts = BetterFrenchTts(context)
```

### 2. Speak

```kotlin
tts.speak("Bonjour, bienvenue dans l'application.")
```

### 3. Use a preset

```kotlin
tts.speak("Il était une fois...", preset = SpeechPreset.STORYTELLING)
```

### 4. Use the DSL

```kotlin
tts.speak {
    text("Bonjour.")
    pause(400)
    slow { text("Ceci est important.") }
    pause(300)
    emphasis { text("Très important !") }
}
```

### 5. Release resources

```kotlin
tts.shutdown()
```

---

## Available Presets

| Preset | Rate | Pitch | Volume | Use case |
|---|---|---|---|---|
| `NEUTRAL` | medium | +0st | medium | General purpose |
| `CALM` | slow | -1st | soft | Relaxation, accessibility |
| `EXCITED` | fast | +4st | loud | Enthusiastic notifications |
| `TEACHING` | 85% | +1st | medium | Education, tutorials |
| `STORYTELLING` | 90% | +2st | medium | Story reading |
| `NEWS` | medium | +0st | loud | News bulletins |
| `WHISPER` | slow | -2st | x-soft | Whispering |
| `ANNOUNCEMENT` | slow | -1st | x-loud | Important announcements |
| `READING` | 95% | +0st | medium | Article reading |
| `DICTATION` | 70% | +0st | loud | Dictation, note-taking |
| `NOTIFICATION` | fast | +1st | loud | Short alerts |
| `MEDITATION` | x-slow | -3st | soft | Guided meditation |

### Create a custom preset

```kotlin
val myPreset = SpeechPreset(
    name = "My style",
    rate = "80%",
    pitch = "+3st",
    volume = "loud"
)
tts.speak("Texte avec mon style", preset = myPreset)
```

---

## DSL — Quick Reference

```kotlin
tts.speak {
    // Plain text
    text("Bonjour.")

    // Pauses
    pause(500)  // in milliseconds

    // Rate
    slow { text("Lent") }
    fast { text("Rapide") }
    xSlow { text("Très lent") }
    xFast { text("Très rapide") }
    rate(75) { text("75% of normal rate") }

    // Volume
    soft { text("Doux") }
    loud { text("Fort") }
    xSoft { text("Très doux") }
    xLoud { text("Très fort") }

    // Pitch
    highPitch { text("Aigu") }
    lowPitch { text("Grave") }
    pitch(semitones = 5) { text("+5 semitones") }

    // Emphasis
    emphasis { text("Modéré") }
    strong { text("Fort") }
    reduced { text("Réduit") }

    // Full prosody control
    prosody(rate = "slow", pitch = "+2st", volume = "loud") {
        text("Full control")
    }

    // Presets within the DSL
    withPreset(SpeechPreset.CALM) { text("Calme") }

    // Smart spell-out
    spellOut("café@123")

    // Special interpretation
    telephone("01 23 45 67 89")
    number("1500")
    ordinal("3")
    date("17/03/2025", format = "dmy")

    // Structure
    paragraph {
        sentence { text("Première phrase.") }
        sentence { text("Deuxième phrase.") }
    }
}
```

---

## Advanced Configuration

```kotlin
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    defaultPreset = SpeechPreset.READING,
    preferredVoiceNames = listOf("fr-fr-x-frd-local", "fr-fr-x-fra-local"),
    autoChunkLongText = true,
    onReady = { instance ->
        Log.d("TTS", "Ready, voice: ${instance.currentVoice?.name}")
    },
    onInitError = { code ->
        Log.e("TTS", "Init error: $code")
    }
))
```

---

## Compatibility

- **Android**: minSdk 30+
- **TTS engine**: Google TTS (pre-installed on most devices)
- **Languages**: French (FR, CA, BE, CH)
- **Mode**: 100% offline
- **Dependencies**: `androidx.core.ktx`, `kotlinx-coroutines-android`

---

## Architecture

```
better-french-tts/
  com.github.kvnpetit.betterfrenchtts/
    BetterFrenchTts.kt       <- Main entry point
    SpeechPreset.kt           <- Expressive presets
    SpeechResult.kt           <- Operation results
    TextChunker.kt            <- Long text chunking
    dsl/
      SpeechBuilder.kt        <- Kotlin DSL
    spelling/
      FrenchCharMap.kt        <- 200+ French character mappings
    ssml/
      SsmlNode.kt             <- SSML tree (sealed class)
      SsmlRenderer.kt         <- SSML XML renderer
    voice/
      FrenchVoiceSelector.kt  <- Smart voice selection
```

---

## License

MIT
