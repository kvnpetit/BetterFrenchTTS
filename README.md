# Better French TTS

[![](https://jitpack.io/v/kvnpetit/better-french-tts.svg)](https://jitpack.io/#kvnpetit/better-french-tts)

**An intelligent wrapper around Android's native TTS, optimized for French.**

Better French TTS turns Android speech synthesis into a high-quality vocal experience for French. No more verbose SSML, robotic default voices, or manual tuning — the library handles everything internally.

**[API Documentation (KDoc)](https://kvnpetit.github.io/better-french-tts/)**

> **New library coordinates:** `com.github.kvnpetit:better-french-tts`.
> Migrating from `BetterFrenchTTS` 1.x? See the [migration guide](MIGRATION.md)
> for the new dependency and Kotlin imports. The source migration targets 2.x;
> use a tag containing these changes once published, or its full commit SHA on JitPack.
> Existing 1.x tags still contain the old packages.

---

## Why?

Android's native TTS exposes only 2 parameters (`pitch` and `speechRate`), no contextual modulation, no smart voice selection. Going further requires hand-writing SSML — verbose, fragile, and poorly documented.

Better French TTS solves this with:

- **A Kotlin DSL** that generates SSML automatically
- **12 expressive presets** + ability to create custom ones
- **Automatic selection** of the best offline French voice
- **Smart text preprocessing** — abbreviations, ordinals, time, units, currencies, roman numerals
- **Pronunciation dictionary** with alias and IPA phoneme support
- **Speech queue** with pause, resume, skip, and progress tracking
- **Smart spell-out** with 200+ characters mapped to French pronunciation
- **Auto-chunking** of long texts (TTS ~4000 char limit)
- **Word highlighting** callback for real-time text tracking
- **Coroutines support** with `speakAndAwait()`
- **Automatic audio focus** management (duck or pause other apps)
- **Zero cloud dependency**, 100% offline, negligible size

---

## Installation

### JitPack

Add JitPack to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency in your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.kvnpetit:better-french-tts:<version>")
}
```

### Local module

If you prefer including the source directly:

```kotlin
// settings.gradle.kts
include(":better-french-tts")

// app/build.gradle.kts
dependencies {
    implementation(project(":better-french-tts"))
}
```

---

## Quick Start

### 1. Initialize

```kotlin
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts

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

    // Phoneme (IPA)
    phoneme("Huawei", "wa.wɛj")

    // Substitution
    sub("Huawei", "Oua-ouei")

    // Structure
    paragraph {
        sentence { text("Première phrase.") }
        sentence { text("Deuxième phrase.") }
    }
}
```

---

## Text Preprocessing

Text is automatically normalized before synthesis (enabled by default):

```kotlin
tts.speak("M. Dupont a rdv à 14h30.")
// → "Monsieur Dupont a rendez-vous à 14 heures 30."

tts.speak("Le trajet fait 42 km à 120 km/h. Il fait 22°C.")
// → "Le trajet fait 42 kilomètres à 120 kilomètres par heure. Il fait 22 degrés Celsius."

tts.speak("Ça coûte 15€, soit une hausse de 8%.")
// → "Ça coûte 15 euros, soit une hausse de 8 pourcent."

tts.speak("Louis XIV a vécu au XVIIe siècle.")
// → "Louis quatorze a vécu au dix-septième siècle."
```

Handled patterns: abbreviations (M., Mme, Dr...), ordinals (1er, 3ème), time (14h30), units (km, °C, km/h...), currencies (€, $, £), percentages, roman numerals.

---

## Pronunciation Dictionary

Override how specific words are pronounced:

```kotlin
// Simple alias — TTS reads "Oua-ouei"
tts.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))

// IPA — exact phonetic control
tts.addPronunciation(PronunciationRule.Ipa("Lacoste", "la.kɔst"))

// Speak text containing these words
tts.speak("Mon téléphone Huawei et ma veste Lacoste.")

// Remove a rule
tts.removePronunciation("Huawei")

// Clear all rules
tts.clearPronunciations()
```

---

## Speech Queue

Play multiple items sequentially with full playback control:

```kotlin
// Build the queue
tts.enqueue("Bienvenue dans l'application.")
tts.enqueue("Voici les dernières nouvelles.", preset = SpeechPreset.NEWS)
tts.enqueue {
    slow { text("Point important.") }
    emphasis { text("Très important !") }
}

// Or batch
tts.enqueueAll(listOf("Texte 1", "Texte 2", "Texte 3"))

// Track progress
tts.onQueueProgress { progress ->
    Log.d("TTS", "Playing ${progress.currentIndex + 1}/${progress.totalItems}")
}
tts.onQueueFinished {
    Log.d("TTS", "Queue finished")
}

// Start playback
tts.playQueue()

// Playback control
tts.pauseQueue()    // Pause (stops current utterance)
tts.resumeQueue()   // Resume from current item
tts.skipToNext()    // Skip to next item
tts.clearQueue()    // Clear queue and stop

// State
tts.isQueuePlaying       // true if playing (not paused)
tts.isQueuePaused        // true if paused
tts.queueSize            // number of items
tts.currentQueuePosition // current item index (-1 if inactive)
```

---

## Word Highlighting

Track which word is currently being spoken for real-time UI highlighting:

```kotlin
tts.onWordHighlight { highlight ->
    if (highlight.start >= 0) {
        // Highlight text[highlight.start..highlight.end]
    } else {
        // Speech finished, clear highlighting
    }
}
```

### Compose example

```kotlin
var highlight by remember { mutableStateOf<WordHighlight?>(null) }

tts.onWordHighlight { wh ->
    highlight = if (wh.start >= 0) wh else null
}

val annotated = buildAnnotatedString {
    val h = highlight
    if (h != null && h.start in text.indices && h.end <= text.length) {
        append(text.substring(0, h.start))
        withStyle(SpanStyle(background = Color.Yellow)) {
            append(text.substring(h.start, h.end))
        }
        append(text.substring(h.end))
    } else {
        append(text)
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

- **Android**: minSdk 26+ (Android 8.0 Oreo)
- **Java**: 21
- **TTS engine**: Google TTS (pre-installed on most devices)
- **Languages**: French (FR, CA, BE, CH)
- **Mode**: 100% offline
- **Dependencies**: `androidx.core.ktx`, `kotlinx-coroutines-android`

---

## Architecture

```
better-french-tts/
  io.github.kvnpetit.betterfrenchtts/
    BetterFrenchTts.kt        <- Main entry point
    SpeechPreset.kt            <- Expressive presets
    SpeechResult.kt            <- Operation results
    PronunciationRule.kt       <- Alias & IPA pronunciation rules
    QueueItem.kt               <- Speech queue items
    QueueProgress.kt           <- Queue progress info
    WordHighlight.kt           <- Word-level highlight positions
    TextChunker.kt             <- Long text chunking
    dsl/
      SpeechBuilder.kt         <- Kotlin DSL
    preprocessing/
      FrenchTextPreprocessor.kt <- French text normalization
    spelling/
      FrenchCharMap.kt         <- 200+ French character mappings
    ssml/
      SsmlNode.kt              <- SSML tree (sealed class)
      SsmlRenderer.kt          <- SSML XML renderer
    voice/
      FrenchVoiceSelector.kt   <- Smart voice selection
```

---

## License

MIT
