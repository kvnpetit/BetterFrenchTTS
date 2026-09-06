# Compatibility and known limitations

This page describes **2.1.1**. See [migration](../MIGRATION.md) for changes from 2.0.0.

## Platform and build requirements

Android 8.0 / API 26 minimum; build with JDK 21 and the checked-in Gradle wrapper.
This repository uses Android SDK 37. Updated AndroidX dependencies require consumer
apps to use `compileSdk >= 37`; `minSdk` remains 26. Consumer tooling must support the AAR.

## French voices and offline playback

No voice, engine or cloud service is bundled. Install French voice data in device
TTS settings. The default policy requires a French voice reported as offline and
installed. Initialization fails if none is eligible or the engine rejects it.
France is preferred; set `requireExactLocale = true` to refuse regional fallback.
`offlineOnly = false` allows network-required French voices. These policies also
apply to manual voice changes. Check `trySetVoice` results.

The engine's metadata is not a network sandbox. Validate with networking disabled.
An optional engine package requests a particular engine but Android may fall back.

## Native playback and SSML

Native is the default: text/aliases, real silent utterances, rate, pitch and volume
are Android operations. Typed dates/numbers/telephones are expanded by the library.
Preset/emphasis effects are approximations, not emotional synthesis. IPA and
unsupported say-as interpretations fail explicitly in native mode.

`PlaybackMode.SSML` and `speakSsml` are engine-specific alternatives. Android does
not promise portable interpretation of arbitrary XML/SSML. A voice may pronounce
markup literally or ignore tags. Test before enabling IPA or custom SSML.
See the [Android TextToSpeech reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

## Ranges, queues and long input

Native range offsets refer to transformed segments. SSML wrapper adjustment does
not provide full original-source mapping. Do not highlight original input blindly.
Callbacks are per engine utterance; use `speakAndAwait` for whole-request completion.

Queue pause stops playback; resume restarts the current item. Native text and
aliases split up to 3,900 UTF-16 units, with surrogate pairs preserved. SSML
atomic phoneme nodes cannot be split; raw SSML and queued SSML DSL items are not
automatically chunked. Native file export is one bounded text request.
Handle immediate rejection and asynchronous failure. Gapless playback, exact
timestamp resume and streamed audio concatenation are not provided.

## Audio focus and lifecycle

Focus denial rejects playback unless focus mode is `NONE`. Focus loss stops speech
by default; `PAUSE_QUEUE` preserves an active queue on transient loss for manual
resume. `IGNORE` explicitly delegates interruption policy to the application.
No automatic restart occurs. Focus and synthesis share `CONTENT_TYPE_SPEECH` and
the configured `audioUsage`. Apps targeting API 35+ need foreground eligibility
to request focus; the library does not create a foreground service.
Control instances on the main thread and release with shutdown.

Equal-style adjacent native fragments are merged to reduce artificial boundaries,
not to guarantee gapless audio. Export on a busy instance is rejected to avoid
changing active playback controls.
Voice changes are likewise rejected during synthesis. Awaitable export waits for
the engine's file-completion callback; cancellation/errors can leave partial files.
The complete preparation preview does not validate acoustic output or engine support.

## Android manifest

The library manifest supplies TTS service visibility for Android 11+:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

## Validation and quality

Local validation uses an Android 16 / API 36.1 emulator. The published 2.1.0 tag
was tested in CI on API 26 and 36. The development branch tests the minimum API 26
and API 37 (system image `37.0`); require successful checks on both for subsequent
releases. This boundary matrix does not prove every intermediate Android version.
Compilation uses SDK 37, while the demo intentionally retains `targetSdk 36`.
Compiling against an SDK and testing on that Android version are separate checks. Controlled engines
verify contracts without proving acoustic behavior. Other engines, physical devices
and regional accents require additional validation.

French normalization is heuristic, not full language understanding. Use explicit
formats for ambiguous data and aliases for audited names. No universal liaison,
homograph, accent or perfect-pronunciation claim is made.
See the [listening evaluation protocol](evaluation.md).
