# Better French TTS — Developer Documentation

**New package:** `io.github.kvnpetit.betterfrenchtts`. For projects using the
former `com.github.kvnpetit.betterfrenchtts` package, follow [MIGRATION.md](MIGRATION.md).

Complete API guide for developers integrating Better French TTS into their Android application.

Start with the [README](README.md) for installation and a lifecycle-aware example.
Read [compatibility and limitations](docs/compatibility.md) for offline prerequisites,
engine-dependent SSML behavior and range callback limitations. Examples below describe
the API and generated markup; they do not guarantee that every engine honors every tag.

> Full KDoc is also available at **[kvnpetit.github.io/better-french-tts](https://kvnpetit.github.io/better-french-tts/)**

---

## Table of Contents

1. [Initialization](#1-initialization)
2. [Simple API — speak()](#2-simple-api)
3. [DSL API](#3-dsl-api)
4. [Presets](#4-presets)
5. [Smart Spell-Out](#5-smart-spell-out)
6. [Text Preprocessing](#6-text-preprocessing)
7. [Pronunciation Dictionary](#7-pronunciation-dictionary)
8. [Speech Queue](#8-speech-queue)
9. [Word Highlighting](#9-word-highlighting)
10. [Coroutines](#10-coroutines)
11. [Synthesize to File](#11-synthesize-to-file)
12. [Voice Control](#12-voice-control)
13. [Playback Control](#13-playback-control)
14. [Callbacks](#14-callbacks)
15. [Result Handling](#15-result-handling)
16. [SSML Debugging](#16-ssml-debugging)
17. [Audio Focus](#17-audio-focus)
18. [Configuration](#18-configuration)
19. [Long Texts](#19-long-texts)
20. [Lifecycle](#20-lifecycle)
21. [Compose Integration](#21-compose-integration)
22. [FrenchCharMap — Reference](#22-frenchcharmap)

---

## 1. Initialization

### Basic

```kotlin
val tts = BetterFrenchTts(context)
```

The library initializes the Android TTS engine asynchronously and attempts to select
an offline French voice. Wait for `onReady` before speaking and handle `onInitError`.
If no matching voice is found, `onReady` can still run with `currentVoice == null`;
an app requiring offline French speech should refuse that fallback.

### With readiness callback

```kotlin
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    onReady = { instance ->
        // TTS is ready
        Log.d("TTS", "Selected voice: ${instance.currentVoice?.name}")
    }
))
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
| `preprocessText` | `Boolean` | `true` | Normalize abbreviations, ordinals, time, units, etc. |
| `autoChunkLongText` | `Boolean` | `true` | Automatically splits texts > 4000 chars |
| `audioFocus` | `AudioFocusMode` | `DUCK` | Audio focus strategy while speaking |
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

#### Pronunciation (inline)

```kotlin
tts.speak {
    text("Le mot ")
    phoneme("Huawei", "wa.wɛj")   // IPA phonetic transcription
    text(" se prononce ainsi. ")
    sub("Xiaomi", "Chao-mi")      // Simple text substitution
}
```

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

## 6. Text Preprocessing

When `preprocessText` is enabled (default: `true`), text is automatically normalized before synthesis.

### Supported patterns

| Pattern | Example input | Spoken output |
|---|---|---|
| Abbreviations | `M. Dupont, Mme Martin` | `Monsieur Dupont, Madame Martin` |
| Titles | `Dr Morel, Pr Duval, Me Martin` | `Docteur Morel, Professeur Duval, Maître Martin` |
| Ordinals | `1er, 2ème, 3e` | `premier, deuxième, troisième` |
| Time | `14h30, 8h` | `14 heures 30, 8 heures` |
| Units | `42 km, 3 kg, 22°C, 120 km/h` | `42 kilomètres, 3 kilogrammes, 22 degrés Celsius, 120 kilomètres par heure` |
| Currencies | `15€, $20, 10£` | `15 euros, 20 dollars, 10 livres sterling` |
| Percentages | `50%` | `50 pourcent` |
| Roman numerals | `Louis XIV, XXIe siècle` | `Louis quatorze, vingt-et-unième siècle` |
| Addresses | `bd Haussmann, av. Foch` | `boulevard Haussmann, avenue Foch` |
| Locutions | `etc., c.-à-d., N.B.` | `et cetera, c'est-à-dire, nota bene` |

### Supported units (35+)

Length (km, m, cm, mm), weight (kg, g, mg), volume (L, mL, cL, dL), speed (km/h, m/s), area (m², km², ha), time (min, sec, ms), frequency (Hz, kHz, MHz, GHz), data (Ko, Mo, Go, To), temperature (°C, °F).

### Disable preprocessing

```kotlin
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    preprocessText = false
))
```

---

## 7. Pronunciation Dictionary

Override how specific words are pronounced, using simple aliases or IPA phonetics.

### Alias (text substitution)

```kotlin
tts.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
tts.addPronunciation(PronunciationRule.Alias("Xiaomi", "Chao-mi"))

tts.speak("Mon téléphone Huawei.")
// TTS reads: "Mon téléphone Oua-ouei."
```

Generates an SSML `<sub alias="...">` tag.

### IPA (phonetic transcription)

```kotlin
tts.addPronunciation(PronunciationRule.Ipa("Lacoste", "la.kɔst"))
tts.addPronunciation(PronunciationRule.Ipa("Nutella", "nu.tɛ.la"))

tts.speak("Ma veste Lacoste sent le Nutella.")
```

Generates an SSML `<phoneme alphabet="ipa" ph="...">` tag.

### Rules

- Matching is **case-insensitive**
- Each word can have **one** active rule — adding a new rule for the same word replaces the previous one
- Rules apply to `speak(text)`, `speakAndAwait(text)`, `synthesizeToFile()`, and queue items

### Management

```kotlin
tts.removePronunciation("Huawei")  // Remove a single rule
tts.clearPronunciations()           // Remove all rules
```

### DSL equivalents

The DSL also supports pronunciation control inline:

```kotlin
tts.speak {
    text("Le mot ")
    phoneme("Huawei", "wa.wɛj")    // IPA inline
    text(" ou ")
    sub("Huawei", "Oua-ouei")      // Alias inline
}
```

---

## 8. Speech Queue

Play multiple items sequentially with full playback control.

### Enqueue items

```kotlin
// Text items
tts.enqueue("Premier élément.")
tts.enqueue("Deuxième, plus calme.", preset = SpeechPreset.CALM)

// DSL items
tts.enqueue {
    emphasis { text("Troisième, en emphase.") }
}

// Batch
tts.enqueueAll(listOf("Item A", "Item B", "Item C"))
tts.enqueueAll(listOf("X", "Y"), preset = SpeechPreset.NEWS)
```

### Start playback

```kotlin
tts.playQueue()
```

### Playback control

```kotlin
tts.pauseQueue()    // Pause — current utterance stops
tts.resumeQueue()   // Resume — replays current item from the start
tts.skipToNext()    // Skip to next item
tts.clearQueue()    // Clear queue and stop playback
```

> **Note:** Android TTS does not support mid-utterance pause. `resumeQueue()` replays the current item from the beginning.

### Progress tracking

```kotlin
tts.onQueueProgress { progress ->
    // progress.currentIndex  — 0-based index of current item
    // progress.totalItems    — total number of items
    Log.d("TTS", "Playing ${progress.currentIndex + 1}/${progress.totalItems}")
}

tts.onQueueFinished {
    Log.d("TTS", "All items spoken")
}
```

### Queue state

```kotlin
tts.isQueuePlaying        // true if actively playing
tts.isQueuePaused         // true if paused
tts.queueSize             // number of items in queue
tts.currentQueuePosition  // 0-based index, or -1 if inactive
```

### Interaction with speak()

Any direct `speak()`, `speakAndAwait()`, or `speakSsml()` call **cancels** the active queue. The queue is a separate mechanism — use it or use direct calls, not both simultaneously.

---

## 9. Word Highlighting

Track which word the TTS engine is currently speaking, for real-time UI highlighting.

### Setup

```kotlin
tts.onWordHighlight { highlight ->
    // highlight.utteranceId — unique ID of the utterance
    // highlight.start       — start index in the original text (inclusive)
    // highlight.end         — end index in the original text (exclusive)

    if (highlight.start >= 0) {
        // Highlight text[start..end]
    } else {
        // start == -1: speech finished, clear highlighting
    }
}
```

### Important notes

- For `speak(text)` and `speakAndAwait(text)`, wrapper offsets are adjusted, but preprocessing,
  escaping, substitutions and chunking can prevent an exact mapping to the original text.
- For DSL-based calls, positions refer to the generated SSML and may not map to any single source string
- When speech finishes, a final `WordHighlight(utteranceId, -1, -1)` is emitted to signal clearing
- Callback runs on the **main thread**

### Compose integration

```kotlin
var highlight by remember { mutableStateOf<WordHighlight?>(null) }

tts.onWordHighlight { wh ->
    highlight = if (wh.start >= 0) wh else null
}

Text(
    text = buildAnnotatedString {
        val h = highlight
        if (h != null && h.start in myText.indices && h.end <= myText.length) {
            append(myText.substring(0, h.start))
            withStyle(SpanStyle(background = Color.Yellow, fontWeight = FontWeight.Bold)) {
                append(myText.substring(h.start, h.end))
            }
            append(myText.substring(h.end))
        } else {
            append(myText)
        }
    }
)
```

---

## 10. Coroutines

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

## 11. Synthesize to File

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

## 12. Voice Control

### Automatic selection

By default, the library selects the best offline French voice:

Selection is attempted, not guaranteed. If there is no matching voice, the engine's
default remains active and `currentVoice` is null. A manually selected voice can also
require network access. See [offline behavior](docs/compatibility.md#tts-engines-and-offline-playback).

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

## 13. Playback Control

```kotlin
tts.stop()            // Stop ongoing speech
tts.isSpeaking        // Boolean — true if currently speaking
tts.isInitialized     // Boolean — true once TTS engine is ready and a French voice is selected
```

---

## 14. Callbacks

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
    .onWordHighlight { highlight ->
        // Real-time word position (see section 9)
    }
    .onQueueProgress { progress ->
        // Queue item changed (see section 8)
    }
    .onQueueFinished {
        // All queue items spoken (see section 8)
    }
```

Callbacks are chained (fluent API) and can be set at any time.

---

## 15. Result Handling

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

## 16. SSML Debugging

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

### speakSsml()

Speaks raw SSML directly, bypassing the DSL and preset system:

```kotlin
val ssml = """<speak><prosody rate="slow" pitch="+2st">Bonjour le monde</prosody></speak>"""
tts.speakSsml(ssml)

// Queue mode
tts.speakSsml(ssml, queueMode = TextToSpeech.QUEUE_ADD)
```

Useful when you build SSML from an external source or need full manual control over the markup.

---

## 17. Audio Focus

The library automatically manages Android audio focus while speaking. This tells other apps (music players, podcasts, etc.) to lower their volume or pause during speech.

### Modes

| Mode | Behavior |
|---|---|
| `DUCK` (default) | Other apps lower their volume while speaking |
| `GAIN_TRANSIENT` | Other apps pause and resume when speech finishes |
| `NONE` | No audio focus management, other apps continue normally |

### Configuration

```kotlin
// Duck other apps (default)
val tts = BetterFrenchTts(context)

// Pause other apps instead
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    audioFocus = BetterFrenchTts.AudioFocusMode.GAIN_TRANSIENT
))

// Disable audio focus
val tts = BetterFrenchTts(context, BetterFrenchTts.Config(
    audioFocus = BetterFrenchTts.AudioFocusMode.NONE
))
```

### Behavior

- Audio focus is **requested** when speech starts
- Audio focus is **released** when all speech finishes, is stopped, or an error occurs
- Coroutine cancellation also releases audio focus
- `synthesizeToFile()` does **not** request audio focus (no audio playback)

---

## 18. Configuration

### Config Summary

```kotlin
BetterFrenchTts.Config(
    defaultPreset = SpeechPreset.NEUTRAL,
    preferredVoiceNames = FrenchVoiceSelector.DEFAULT_PREFERRED_VOICES,
    preprocessText = true,
    autoChunkLongText = true,
    audioFocus = BetterFrenchTts.AudioFocusMode.DUCK,
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

## 19. Long Texts

Android TTS has a limit of approximately 4000 characters per utterance. When `autoChunkLongText` is enabled (default), the library automatically splits:

1. At **paragraph** boundaries (`\n\n`)
2. At **sentence** boundaries (`. `, `! `, `? `)
3. At **clause** boundaries (`, `, `; `)
4. At the last **space**
5. As a last resort: hard cut

Each chunk is queued with `QUEUE_ADD` for seamless playback.

---

## 20. Lifecycle

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

## 21. Compose Integration

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

## 22. FrenchCharMap

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
