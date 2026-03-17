# BetterFrenchTTS — Developer Documentation

Complete API guide for developers integrating BetterFrenchTTS into their Android application.

---

## Table of Contents

1. [Initialization](#1-initialization)
2. [Simple API — speak()](#2-simple-api)
3. [DSL API](#3-dsl-api)
4. [Presets](#4-presets)
5. [Smart Spell-Out](#5-smart-spell-out)
6. [Coroutines](#6-coroutines)
7. [Synthesize to File](#7-synthesize-to-file)
8. [Voice Control](#8-voice-control)
9. [Playback Control](#9-playback-control)
10. [Callbacks](#10-callbacks)
11. [Result Handling](#11-result-handling)
12. [SSML Debugging](#12-ssml-debugging)
13. [Configuration](#13-configuration)
14. [Long Texts](#14-long-texts)
15. [Lifecycle](#15-lifecycle)
16. [Compose Integration](#16-compose-integration)
17. [FrenchCharMap — Reference](#17-frenchcharmap)

---

## 1. Initialization

### Basic

```kotlin
val tts = BetterFrenchTts(context)
```

The library initializes the Android TTS engine in the background, automatically selects the best offline French voice, and becomes ready to use. Everything is asynchronous.

### With readiness callback

```kotlin
val tts = BetterFrenchTts(context) { instance ->
    // TTS is ready
    Log.d("TTS", "Selected voice: ${instance.currentVoice?.name}")
}
```

The `onReady` callback receives the initialized `BetterFrenchTts` instance. It runs on the **main thread**.

### With full configuration

```kotlin
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    defaultPreset = SpeechPreset.READING,
    preferredVoiceNames = listOf("fr-fr-x-frd-local"),
    autoChunkLongText = true,
    onReady = { instance -> /* ... */ },
    onInitError = { code -> /* ... */ }
))
```

#### Config Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `defaultPreset` | `SpeechPreset` | `NEUTRAL` | Default preset applied to every `speak()` call |
| `preferredVoiceNames` | `List<String>` | Internal list | Preferred voice names, tested in order |
| `autoChunkLongText` | `Boolean` | `true` | Automatically splits texts > 4000 chars |
| `onReady` | `((BetterFrenchTts) -> Unit)?` | `null` | Callback when TTS is initialized |
| `onInitError` | `((Int) -> Unit)?` | `null` | Callback if init fails (receives error code) |

---

## 2. Simple API

### speak()

```kotlin
fun speak(
    text: String,
    preset: SpeechPreset = config.defaultPreset,
    queueMode: Int = TextToSpeech.QUEUE_FLUSH
): SpeechResult
```

Reads text with an optional preset.

```kotlin
// Uses the default preset
tts.speak("Bonjour")

// With a specific preset
tts.speak("Bienvenue", preset = SpeechPreset.CALM)

// Queue instead of replacing
tts.speak("Suite", queueMode = TextToSpeech.QUEUE_ADD)
```

**`QUEUE_FLUSH`**: interrupts any ongoing speech.
**`QUEUE_ADD`**: appends to the current speech queue.

---

## 3. DSL API

### speak { }

```kotlin
fun speak(
    queueMode: Int = TextToSpeech.QUEUE_FLUSH,
    block: SpeechBuilder.() -> Unit
): SpeechResult
```

Builds structured speech with the Kotlin DSL.

### DSL Functions

#### Text and pauses

```kotlin
tts.speak {
    text("Du texte brut.")       // Add text
    pause(500)                    // Pause in milliseconds
}
```

#### Rate control

```kotlin
tts.speak {
    slow { text("Débit lent") }
    fast { text("Débit rapide") }
    xSlow { text("Très lent") }
    xFast { text("Très rapide") }
    rate(75) { text("75% of normal rate") }  // Percentage value
}
```

Accepted SSML values for `rate`: `x-slow`, `slow`, `medium`, `fast`, `x-fast`, or a percentage (`75%`, `120%`).

#### Volume control

```kotlin
tts.speak {
    soft { text("Volume doux") }
    loud { text("Volume fort") }
    xSoft { text("Très doux") }
    xLoud { text("Très fort") }
}
```

SSML values: `silent`, `x-soft`, `soft`, `medium`, `loud`, `x-loud`.

#### Pitch control

```kotlin
tts.speak {
    highPitch { text("Aigu (+3 semitones)") }
    lowPitch { text("Grave (-3 semitones)") }
    pitch(semitones = 5) { text("Exactly +5 semitones") }
    pitch(semitones = -2) { text("Exactly -2 semitones") }
}
```

#### Full prosody

```kotlin
tts.speak {
    prosody(rate = "85%", pitch = "+2st", volume = "loud") {
        text("Full prosody control")
    }
}
```

All parameters are optional — only specified ones are included in the SSML.

#### Emphasis

```kotlin
tts.speak {
    emphasis { text("Moderate emphasis (default)") }
    emphasis(level = "strong") { text("Strong emphasis") }
    emphasis(level = "reduced") { text("Reduced emphasis") }
    strong { text("Shortcut for strong") }
    reduced { text("Shortcut for reduced") }
}
```

Levels: `reduced`, `moderate` (default), `strong`.

#### Special interpretation (say-as)

```kotlin
tts.speak {
    spellOut("ABCD")                          // Spell letter by letter
    telephone("01 23 45 67 89")               // Read as phone number
    number("1500")                             // "mille cinq cents"
    ordinal("3")                               // "troisième"
    date("17/03/2025", format = "dmy")        // Read as date
    sayAs(interpretAs = "unit", content = "5km") // Generic
}
```

#### Structure (paragraphs and sentences)

```kotlin
tts.speak {
    paragraph {
        sentence { text("Première phrase.") }
        sentence { text("Deuxième phrase.") }
    }
    paragraph {
        sentence { text("Nouveau paragraphe.") }
    }
}
```

The `<p>` and `<s>` tags allow the TTS engine to apply natural prosody between sentences and paragraphs.

#### Presets within the DSL

```kotlin
tts.speak {
    withPreset(SpeechPreset.CALM) {
        text("Cette partie est calme.")
    }
    pause(400)
    withPreset(SpeechPreset.EXCITED) {
        text("Et celle-ci est excitée !")
    }
}
```

### speakWithPreset()

Applies a preset as a wrapper around the DSL:

```kotlin
tts.speakWithPreset(SpeechPreset.STORYTELLING) {
    text("Il était une fois...")
    pause(500)
    emphasis { text("un dragon") }
    text(" qui gardait un trésor.")
}
```

Difference from `withPreset` in the DSL: `speakWithPreset` wraps **all** content, while `withPreset` can be used on **segments**.

---

## 4. Presets

### Built-in presets

```kotlin
SpeechPreset.NEUTRAL       SpeechPreset.CALM
SpeechPreset.EXCITED       SpeechPreset.TEACHING
SpeechPreset.STORYTELLING  SpeechPreset.NEWS
SpeechPreset.WHISPER       SpeechPreset.ANNOUNCEMENT
SpeechPreset.READING       SpeechPreset.DICTATION
SpeechPreset.NOTIFICATION  SpeechPreset.MEDITATION
```

Access the full list:

```kotlin
val all = SpeechPreset.builtIn  // List<SpeechPreset>
```

### Create a custom preset

`SpeechPreset` is an open `data class`:

```kotlin
val myPreset = SpeechPreset(
    name = "Robot",
    rate = "fast",
    pitch = "+6st",
    volume = "loud"
)

tts.speak("Je suis un robot", preset = myPreset)
```

### Default preset

Set the default preset at init to avoid passing it on every call:

```kotlin
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    defaultPreset = SpeechPreset.READING
))

// All speak() calls will use READING by default
tts.speak("Ce texte sera lu avec le preset Reading")
```

---

## 5. Smart Spell-Out

### Usage

```kotlin
tts.speak {
    spellOut("café@2\u20AC!")
}
// Pronounces: "c" "a" "f" "é accent aigu" "arobase" "2" "euro" "point d'exclamation"
```

### Behavior

`spellOut()` decomposes each character and applies the following logic:

1. **Space**: double pause (300ms by default)
2. **Newline / tab**: triple pause
3. **Mapped character** in `FrenchCharMap`: pronounces the French name
4. **Simple letter** (a-z, 0-9): uses `<say-as interpret-as="characters">`
5. **Simple uppercase** (A-Z): pronounces "A majuscule", "B majuscule"...
6. **Unmapped uppercase accent**: "accent name majuscule"
7. **Unknown character**: "caractère unicode [code]"

### Configurable pause

```kotlin
tts.speak {
    spellOut("ABCD", pauseMs = 300)  // 300ms between each letter
}
```

### Characters covered

200+ mapped characters including:

- All French accents (lowercase and uppercase)
- Ligatures (oe, ae)
- Full AZERTY keyboard (direct, Shift, AltGr)
- French typographic punctuation (guillemets, dashes, apostrophes)
- Currencies (20+ monetary symbols)
- Mathematics (25+ symbols)
- Arrows, miscellaneous symbols, Greek letters
- Special whitespace (non-breaking, thin, zero-width)

---

## 6. Coroutines

### speakAndAwait()

Suspends until speech finishes:

```kotlin
lifecycleScope.launch {
    val result = tts.speakAndAwait("Première phrase")
    if (result is SpeechResult.Success) {
        tts.speakAndAwait("Deuxième phrase")
    }
}
```

### speakAndAwait { } (DSL)

```kotlin
lifecycleScope.launch {
    tts.speakAndAwait {
        slow { text("Ceci est lu lentement.") }
        pause(500)
        fast { text("Puis rapidement.") }
    }
    // This code runs after speech finishes
    Log.d("TTS", "Speech complete")
}
```

### Cancellation

Cancelling the coroutine automatically stops speech:

```kotlin
val job = lifecycleScope.launch {
    tts.speakAndAwait("Un long texte...")
}

// Later
job.cancel()  // Stops speech and releases the coroutine
```

---

## 7. Synthesize to File

Save speech synthesis to an audio file:

```kotlin
val file = File(context.cacheDir, "output.wav")
val result = tts.synthesizeToFile("Texte à enregistrer", file)

if (result is SpeechResult.Success) {
    // WAV file created
}
```

With a preset:

```kotlin
tts.synthesizeToFile(
    text = "Annonce importante",
    file = outputFile,
    preset = SpeechPreset.ANNOUNCEMENT
)
```

---

## 8. Voice Control

### Automatic selection

By default, the library selects the best offline French voice:

1. Filters French voices (FR, CA, BE, CH) that don't require network
2. Among the highest quality voices, prefers known good voices
3. Falls back to the highest quality available voice

### List available voices

```kotlin
val voices = tts.listAvailableVoices()
voices.forEach { v ->
    Log.d("TTS", "${v.name} - quality: ${v.quality}")
}
```

Returns only offline French voices, sorted by descending quality.

### Change voice manually

```kotlin
val voices = tts.listAvailableVoices()
val preferred = voices.find { it.name.contains("frd") }
if (preferred != null) {
    tts.setVoice(preferred)
}
```

### Current voice

```kotlin
val name = tts.currentVoice?.name  // null if not yet initialized
```

### Customize priority

```kotlin
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    preferredVoiceNames = listOf(
        "fr-fr-x-frd-local",  // My preferred voice first
        "fr-fr-x-fra-local",
    )
))
```

---

## 9. Playback Control

```kotlin
tts.stop()            // Stop ongoing speech
tts.isSpeaking        // Boolean — true if currently speaking
```

---

## 10. Callbacks

All callbacks run on the **main thread**.

```kotlin
val tts = BetterFrenchTts(context)
    .onStart { utteranceId ->
        // Speech started for an utterance
    }
    .onDone { utteranceId ->
        // Speech finished
    }
    .onError { utteranceId ->
        // Speech error
    }
```

Callbacks are chained (fluent API) and can be set at any time.

---

## 11. Result Handling

All `speak*` methods return a `SpeechResult`:

```kotlin
sealed class SpeechResult {
    data object Success : SpeechResult()
    data class Error(val reason: String) : SpeechResult()
    data object NotReady : SpeechResult()
}
```

Usage:

```kotlin
when (val result = tts.speak("Bonjour")) {
    is SpeechResult.Success -> { /* OK */ }
    is SpeechResult.NotReady -> { /* TTS not yet initialized */ }
    is SpeechResult.Error -> { Log.e("TTS", result.reason) }
}
```

---

## 12. SSML Debugging

Preview generated SSML without speaking it:

```kotlin
// From text + preset
val ssml = tts.buildSsml("Bonjour", SpeechPreset.CALM)
// -> <speak><prosody rate="slow" pitch="-1st" volume="soft">Bonjour</prosody></speak>

// From DSL
val ssml = tts.buildSsml {
    text("Bonjour.")
    pause(400)
    slow { text("Lent.") }
}
// -> <speak>Bonjour.<break time="400ms"/><prosody rate="slow">Lent.</prosody></speak>
```

Useful for debugging or displaying the generated SSML to the user.

---

## 13. Configuration

### Config Summary

```kotlin
BetterFrenchTts.Config(
    defaultPreset = SpeechPreset.NEUTRAL,
    preferredVoiceNames = FrenchVoiceSelector.DEFAULT_PREFERRED_VOICES,
    autoChunkLongText = true,
    onReady = null,
    onInitError = null,
)
```

### SSML values for rate

| Value | Effect |
|---|---|
| `x-slow` | Very slow |
| `slow` | Slow |
| `medium` | Normal |
| `fast` | Fast |
| `x-fast` | Very fast |
| `50%` - `200%` | Percentage of normal rate |

### SSML values for pitch

| Value | Effect |
|---|---|
| `+Nst` | N semitones above (e.g. `+3st`) |
| `-Nst` | N semitones below (e.g. `-2st`) |

### SSML values for volume

| Value | Effect |
|---|---|
| `silent` | Mute |
| `x-soft` | Very soft |
| `soft` | Soft |
| `medium` | Normal |
| `loud` | Loud |
| `x-loud` | Very loud |

---

## 14. Long Texts

Android TTS has a limit of approximately 4000 characters per utterance. When `autoChunkLongText` is enabled (default), the library automatically splits:

1. At **paragraph** boundaries (`\n\n`)
2. At **sentence** boundaries (`. `, `! `, `? `)
3. At **clause** boundaries (`, `, `; `)
4. At the last **space**
5. As a last resort: hard cut

Each chunk is queued with `QUEUE_ADD` for seamless playback.

---

## 15. Lifecycle

### In an Activity

```kotlin
class MyActivity : ComponentActivity() {
    private lateinit var tts: BetterFrenchTts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = BetterFrenchTts(this)
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
```

### In Compose

```kotlin
@Composable
fun MyScreen() {
    val context = LocalContext.current
    val tts = remember { BetterFrenchTts(context) }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    Button(onClick = { tts.speak("Bonjour") }) {
        Text("Parler")
    }
}
```

### Important

**Always** call `shutdown()` when you no longer need the TTS. This releases the native engine resources.

---

## 16. Compose Integration

Complete example of a reusable component:

```kotlin
@Composable
fun SpeakButton(
    text: String,
    preset: SpeechPreset = SpeechPreset.NEUTRAL,
    label: String = "Parler"
) {
    val context = LocalContext.current
    val tts = remember { BetterFrenchTts(context) }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    Button(onClick = { tts.speak(text, preset) }) {
        Text(label)
    }
}

// Usage
SpeakButton(text = "Bonjour", preset = SpeechPreset.CALM, label = "Dire bonjour")
```

---

## 17. FrenchCharMap

Reference of character categories handled by `spellOut()`:

| Category | Examples | Count |
|---|---|---|
| FR accents lowercase | é, è, ê, ë, à, â, ù, ç, î, ô, ÿ, ñ | 17 |
| FR accents uppercase | É, È, Ê, À, Â, Ç, Ù, Û, Ô, Î, Ÿ, Ñ | 17 |
| Ligatures | œ, æ, Œ, Æ | 4 |
| Punctuation | . , ; : ! ? … · | 18 |
| Brackets | ( ) [ ] { } < > | 8 |
| AZERTY keyboard | @ # & _ \ / | ~ ^ ` + = * etc. | 16 |
| Currencies | €, $, £, ¥, ₿... | 25+ |
| Mathematics | ×, ÷, ±, ≠, √, ∞, π... | 25+ |
| Arrows | →, ←, ↑, ↓, ⇔, etc. | 9 |
| Greek letters | α, β, γ, δ, ω... | 12 |
| Miscellaneous symbols | ©, ®, ™, •, ♥, ★... | 20+ |
| Special whitespace | non-breaking, thin, zero-width | 5 |

**Total: 200+ characters**

Simple letters (a-z), uppercase (A-Z → "majuscule"), and digits (0-9) are handled automatically by the TTS engine. Unmapped Unicode characters trigger a fallback "caractère unicode [code]".
