# Compatibility and known limitations

## Platform and build requirements

The library declares API 26 (Android 8.0) as its minimum Android version.
This repository builds with JDK 21, Gradle 9.3.1 and Android SDK 36.1.
Use the checked-in Gradle wrapper; installing a separate global Gradle is unnecessary.
Consumer builds must use Android/Kotlin tooling compatible with the published AAR.
Android API support and the JDK used to build the library are different requirements.

## TTS engines and offline playback

The library uses the device's default Android TTS engine. It does not bundle a voice,
install an engine or download voice data. Configure French voice data in the device's
text-to-speech settings; menus vary by device and engine.

Voice selection prefers voices reported as French and not requiring a network connection.
If no matching voice is found, initialization can still complete with the engine's
default voice; `currentVoice` stays null. Do not treat `onReady` alone as proof that
an offline French voice is available. Applications that require offline playback
should check `currentVoice`, refuse this fallback, handle synthesis errors, and
validate playback with networking disabled. Manually calling `setVoice()` can also
select a voice that needs the network.

The library makes no direct cloud API requests. This does not guarantee that the
selected third-party engine never contacts its own services.

## SSML and pronunciation

The DSL and presets build XML/SSML. Playback passes that string to Android's
`TextToSpeech.speak`; there is no separate SSML interpreter in this library.
Android's public text-to-speech API does not promise portable interpretation of
arbitrary SSML. An engine may ignore markup, pronounce it as text, or support only
some constructs. IPA phonemes, aliases, pauses and preset effects therefore require
listening tests on the engines and voices you intend to support.

A successful rendering test proves the generated string is as expected. A successful
playback callback proves that the engine completed the request, not that every tag
was honored or that pronunciation sounded correct.

## Range callbacks and queues

Range callbacks depend on engine support. Normalization, XML escaping, pronunciation
substitutions and chunking can change character positions. The current implementation
adjusts wrapper offsets but does not maintain a complete source-position mapping;
do not assume every callback index maps exactly to the original input. DSL positions
refer to the generated markup. Validate highlights against the text you display.

Pausing a queue stops the current utterance. Resuming restarts the current item;
it does not resume at an exact audio timestamp.

Long text and nested DSL containers are split using their rendered XML length.
Atomic pronunciation nodes (such as a single alias or phoneme) cannot be split
without changing their meaning. Oversized requests return `SpeechResult.Error`;
shorten those nodes. Raw SSML and queued DSL items are not automatically split.
Handle dispatch errors as well as asynchronous playback errors.

## Android manifest

Apps targeting Android 11 or later should declare TTS service visibility:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>
    <!-- Your application element goes here. -->
</manifest>
```

The current source tree supplies this through the library manifest. Keep the query
in your app when consuming an older artifact that does not include it.
See the [Android TextToSpeech reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

## Validation coverage

On 2026-09-06, local checks on an Android 16 / API 36.1 emulator with Google TTS
and voice `fr-fr-x-frd-local` confirmed initialization, a start/completion cycle,
and no crash for the demo's default French sentence after the preprocessing fix.
This is not an acoustic quality assessment or a full SSML conformance test.

The CI configuration runs instrumentation on API 26 and API 36. These checks cover
Android regex behavior, package identity, demo UI and controlled-engine error/lifecycle
contracts without requiring a downloaded voice. Controlled-engine tests do not
prove acoustic behavior on a real engine.
Other engines, physical devices and regional French voices are not yet verified here.

For a bug report, include Android version, engine name/version, selected voice,
library tag or commit, network conditions and a minimal non-sensitive input.
