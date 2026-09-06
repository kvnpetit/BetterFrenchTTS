# Better French TTS — Developer Documentation

This guide describes **2.1.0-SNAPSHOT**, not the published 2.0.0 artifact.
Package: `io.github.kvnpetit.betterfrenchtts`. Read [migration](MIGRATION.md),
[compatibility](docs/compatibility.md) and [evaluation](docs/evaluation.md).
Online generated API documentation follows stable releases.

## 1. Initialization

Create on the main thread, wait for `onReady`, handle `onInitError`, and call
`shutdown()` when the owner is destroyed. No voice or engine is bundled.

```kotlin
val speech = BetterFrenchTts(context, BetterFrenchTts.Config(
    locale = java.util.Locale.FRANCE,
    requireExactLocale = true,
    offlineOnly = true,
    onReady = { it.speak("Bonjour !") },
    onInitError = { code -> /* Offer device TTS settings. */ }
))
```

`isInitialized` and `currentVoice` expose readiness. `enginePackage` requests an
installed engine; Android may fall back if that engine cannot be used.

## 2. Simple API

`speak(text)` returns `SpeechResult`: `Success` means accepted, not completed.
The default is `TextToSpeech.QUEUE_FLUSH`; `QUEUE_ADD` appends.
Handle `NotReady`, `Error(reason)` and asynchronous errors.

## 3. DSL API

```kotlin
speech.speak {
    text("Votre rendez-vous : ")
    date("01/09/2026")
    pause(400)
    slow { text("Merci de venir à l'heure.") }
    sub("Huawei", "Oua-ouei")
}
```

Native mode compiles text, aliases, pauses, prosody, emphasis, sentences,
paragraphs, numbers, ordinals, dates, telephones and spelling to Android operations.
IPA/phoneme and unknown say-as types fail explicitly. SSML mode renders markup
instead; engine support must be tested. Native sentence/paragraph endings add
180/350 ms silences.

## 4. Presets

Use `speak(text, preset = SpeechPreset.CALM)`, `speakWithPreset`, or DSL
`withPreset`. Presets change rate, pitch and volume, not voice identity.
Their names do not promise emotional voices or real whisper synthesis.
Native emphasis approximates emphasis using rate changes.

## 5. Smart Spell-Out

`spellOut("Été AB12", pauseMs = 150)` uses French character names and native
pauses. Spelling normalizes composed/decomposed accents and handles Unicode
code points without splitting surrogate pairs. Unknown supplementary characters
use a code-point description, not an inferred emoji name.

## 6. Text Preprocessing

Import `io.github.kvnpetit.betterfrenchtts.preprocessing.*`.
`FrenchTextPreprocessor.process(text, Options(...))` works without an engine.
Options independently enable abbreviations, ordinals, times, currencies,
percentages, units and contextual Roman numerals; region defaults to France.
URLs, mail, inline backticks and common mixed identifiers are protected.
Rules do not disambiguate all French: inspect ambiguous text and disable
inappropriate rules. Dates and arbitrary digit sequences are not universally guessed.

`speech.preview(text)` exposes normalization stages, honoring `preprocessText`.
It does not apply the dictionary, synthesize audio or map source offsets.

```kotlin
FrenchFormats.cardinal(91)                 // quatre-vingt-onze
FrenchFormats.ordinal(1, feminine = true) // première
FrenchFormats.number("1 234,50")           // preserves decimal zeros
FrenchFormats.money("12,50")              // douze euros et cinquante centimes
FrenchFormats.date("01/09/2026")           // premier septembre deux mille vingt-six
FrenchFormats.telephone("06 01 02 03 04")  // pairs including leading zeros
FrenchFormats.duration(3661)               // une heure et une minute et une seconde
```

Pass output to DSL `text(...)`. Integer range is ±999,999,999,999; ordinals must
be positive. Money supports two fractional digits maximum (EUR/USD/CAD/GBP).
Dates require a four-digit year, explicit dmy/mdy/ymd order and a valid calendar
date. Invalid explicit inputs throw `IllegalArgumentException`.
Belgium/Switzerland use septante/nonante; Switzerland defaults to quatre-vingts,
not universal huitante. Canada retains France number conventions here.
Voice locale does not automatically change `normalization.region`.

## 7. Pronunciation Dictionary

```kotlin
speech.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
val saved = speech.exportPronunciations()
speech.importPronunciations(saved, replace = true)
speech.removePronunciation("Huawei")
speech.clearPronunciations()
```

Literal whole-word, case-insensitive matching is the default; configure
`wholeWord` and `ignoreCase` on a rule. Longest matches win; case-folded keys
identify rules. `PronunciationRule.Ipa` requires compatible SSML playback.
Export is versioned text with Base64 fields in TSV rows, not executable regex
or JSON. Import validates every row before mutation and limits input to one million characters.
Persist the string yourself; the library does not save it automatically.

## 8. Speech Queue

Use `enqueue`, `enqueueAll`, `playQueue`, `pauseQueue`, `resumeQueue`,
`skipToNext` and `clearQueue`. Resume restarts the current item, not a timestamp.
`onQueueProgress` reports item positions; `onQueueFinished` successful exhaustion.
Rejection ends the active queue. State is exposed by `queueSize`,
`currentQueuePosition`, `isQueuePlaying` and `isQueuePaused`.

## 9. Word Highlighting

`onWordHighlight` depends on engine support. Native offsets refer to the current
transformed segment, not the original document. SSML wrapper adjustment is not
a complete source mapping. Normalization, dictionaries and chunking change
positions; never apply offsets blindly to original text. End markers use -1.

## 10. Coroutines

`speakAndAwait(text)` and the DSL overload await the whole request, including
native segments. Stop, shutdown, flush and rejection complete pending work with
an error. Cancellation stops the entire instance; use separate instances for
independent cancellation.

## 11. Synthesize to File

`synthesizeToFile(text, file)` dispatches synthesis; success does not mean the
file is finished. Use callbacks and an app-writable destination. Native export
supports a single text/alias request up to 3,900 characters; it does not join
audio files or export a structured pause/prosody timeline.
Export is rejected while this instance is busy. Stop it first or use a separate
instance so file prosody cannot change an active playback request.

## 12. Voice Control

`listAvailableVoices()` returns eligible French voices. Requested locale,
installed data, quality and preferred names guide selection.
`offlineOnly = true` excludes network-required/not-installed voices.
`requireExactLocale = true` refuses other French locales, including manual changes.
Use `trySetVoice(voice)` to inspect rejection; legacy `setVoice` discards its
result. `currentVoice` changes only after engine acceptance.

## 13. Playback Control

`stop()` stops playback and clears queue/pending work. `shutdown()` also releases
the engine and readiness; create a new instance to restart.

## 14. Callbacks

Speech callbacks are delivered on the main thread. IDs identify engine segments,
not an entire long request. Prefer `speakAndAwait` for whole-request completion;
do not assume one `onDone` per structured or chunked call.

## 15. Result Handling

Always inspect `SpeechResult`. Dispatch can succeed and later fail.
Invalid explicit formatter arguments throw; unsupported native plans return
errors. Completion is not proof of audible correctness.

## 16. SSML Debugging

`buildSsml(text)` and its DSL overload render markup regardless of playback mode.
`speakSsml(xml)` is an explicit engine-specific escape hatch, not a portable
SSML interpreter. Select `PlaybackMode.SSML` for legacy DSL rendering.
Validate untrusted markup at application level.

## 17. Audio Focus

`NONE`, `DUCK` (default) and `GAIN_TRANSIENT` configure focus requests.
Unless mode is `NONE`, focus denial returns `SpeechResult.Error` before speech
dispatch. `focusLossBehavior` defaults to `STOP`. `PAUSE_QUEUE` preserves an active
queue after transient loss for manual `resumeQueue()`; other playback is stopped.
Permanent loss always stops with these two policies. No automatic restart occurs.
`IGNORE` is an explicit escape hatch for applications managing their own policy.
Native jobs retain focus across segments. `audioUsage` (default `USAGE_MEDIA`) and
`CONTENT_TYPE_SPEECH` are applied to both the engine and focus request.
Apps targeting API 35+ must be foreground or use an appropriate foreground service
to obtain focus; this library does not create a service. Test device restrictions.

## 18. Configuration

`Config` includes `defaultPreset`, `preferredVoiceNames`, `preprocessText`,
`autoChunkLongText`, `audioFocus`, `onReady`, `onInitError`, `playbackMode`,
`locale`, `offlineOnly`, `enginePackage`, `normalization`, `requireExactLocale`,
`audioUsage` and `focusLossBehavior`.
Native rate accepts named speeds or 10–400%; pitch accepts named levels or
semitones within its supported factor range. Named volume levels are bounded
to Android's 0–1 range; x-loud cannot amplify above 1.

## 19. Long Texts

Native adjacent text/aliases with identical controls are merged first, preserving
the spaces supplied by the caller. Add spaces explicitly in the DSL; `pause`
or a control change creates a boundary. Then text splits at up to 3,900 UTF-16 units without splitting surrogate
pairs. Steps serialize controls with completion. SSML chunking uses rendered
length and cannot split atomic phonemes. Oversized raw SSML/file requests fail.
Chunk boundaries can introduce pauses; gapless synthesis is not guaranteed.

See [compatibility](docs/compatibility.md) for engine-dependent limitations.

## 20. Lifecycle

Create/configure/control an instance on the main thread and release it when its
owner is disposed. Do not retain Activity references in callbacks after destruction.
No background service or voice downloader is supplied.

## 21. Compose Integration

Create one instance with `remember`, expose readiness via state and shut down in
`DisposableEffect` cleanup. Do not create an engine on each recomposition.
See the demo for lifecycle ownership and same-voice raw-engine comparison.

## 22. FrenchCharMap

`FrenchCharMap.resolve(char)` provides spelling names. It is not a general French
grapheme-to-phoneme or liaison engine. Audit proper-name dictionaries by listening.
