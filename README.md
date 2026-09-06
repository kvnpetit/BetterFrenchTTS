# Better French TTS

[![CI](https://github.com/kvnpetit/better-french-tts/actions/workflows/verify.yml/badge.svg)](https://github.com/kvnpetit/better-french-tts/actions/workflows/verify.yml)
[![JitPack](https://jitpack.io/v/kvnpetit/better-french-tts.svg)](https://jitpack.io/#kvnpetit/better-french-tts)

A Kotlin library for French text-to-speech on Android. It adds French text
normalization, voice selection, a speech queue and an SSML-building DSL on top
of the device's existing TTS engine.

**Development: 2.1.0-SNAPSHOT (not released).** The current checkout adds native
Android playback and French formatting tools. These features are not in the
existing `v2.0.0` artifact. See [migration](MIGRATION.md) and the
[evaluation protocol](docs/evaluation.md).

Native playback merges adjacent fragments with identical controls to avoid
unnecessary utterance boundaries. Audio focus denial returns an error; focus loss
stops playback by default, with an optional manual-resume queue policy.
These controls do not guarantee perfect pronunciation or gapless audio on every engine.

The development API also offers `previewSpeech` (normalization, dictionary and
native segments or SSML), `synthesizeToFileAndAwait` (actual engine completion),
and structured Android error details. Pronunciation matchers are cached until
the dictionary changes. See the [integration guide](DEVELOPER.md).

**2.0.0 introduces the naming migration.** The new artifact is
`com.github.kvnpetit:better-french-tts` and the package is
`io.github.kvnpetit.betterfrenchtts`. See the [migration guide](MIGRATION.md)
if you use `BetterFrenchTTS` 1.x.

## Requirements and scope

- Android 8.0 / API 26 or later.
- A TTS engine and French voice data installed on the device.
- JDK 21 and an Android SDK for building this repository.
- Development checkout: Android SDK 37 (`compileSdk >= 37` for consuming apps).
- No cloud account or API key is needed by the library.

Offline playback requires a downloaded voice that supports offline synthesis.
The development version requires an installed offline French voice by default.
France (`Locale.FRANCE`) is preferred; set `requireExactLocale = true` to forbid
other French locales. Native playback uses real Android controls and silences.
SSML is opt-in and engine-dependent; valid markup does not prove audible support.
Read [compatibility and limitations](docs/compatibility.md) before integrating.

## Installation

Add JitPack to your application's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

For the existing stable 2.0.0 release (not the new development features):

```kotlin
dependencies {
    implementation("com.github.kvnpetit:better-french-tts:v2.0.0")
}
```

JitPack builds tags on demand. Use the coordinate once the corresponding GitHub
release and successful JitPack build are available. Old 1.x tags still contain
the old packages.

For a source checkout, the demo already uses
`implementation(project(":better-french-tts"))`. See [contributing](CONTRIBUTING.md)
to build it locally.

## Quick start

Initialize on the main thread, wait for `onReady`, handle errors and release the
engine with `shutdown()`. For example, in an Activity registered in your app's manifest:

```kotlin
import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.SpeechResult

class SpeechActivity : Activity() {
    private var speech: BetterFrenchTts? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = BetterFrenchTts(this, BetterFrenchTts.Config(
            onReady = { engine ->
                // Defensive check; 2.1 development enforces offline voices by default.
                val voice = engine.currentVoice
                if (voice == null || voice.isNetworkConnectionRequired) {
                    Log.e("Speech", "Install an offline French voice first")
                } else {
                    engine.onError { Log.e("Speech", "Playback failed: $it") }
                    when (val result = engine.speak("Bonjour, bienvenue !")) {
                        SpeechResult.Success -> Unit // Queued, not necessarily finished.
                        SpeechResult.NotReady -> Log.w("Speech", "Engine is not ready")
                        is SpeechResult.Error -> Log.e("Speech", result.reason)
                    }
                }
            },
            onInitError = { code -> Log.e("Speech", "Initialization failed: $code") }
        ))
    }

    override fun onDestroy() {
        speech?.shutdown()
        super.onDestroy()
    }
}
```

When integrating a published artifact, include the TTS service query described
in [compatibility](docs/compatibility.md#android-manifest). The current source
tree also supplies it through the library manifest.

## Features and examples

- [French preprocessing](DEVELOPER.md#6-text-preprocessing): abbreviations, time, units and Roman numerals.
- [DSL](DEVELOPER.md#3-dsl-api) and [presets](DEVELOPER.md#4-presets): construct speech markup.
- [Spelling](DEVELOPER.md#5-smart-spell-out) and [pronunciation rules](DEVELOPER.md#7-pronunciation-dictionary).
- [Speech queue](DEVELOPER.md#8-speech-queue): enqueue, pause, resume and skip items.
- [Coroutines](DEVELOPER.md#10-coroutines), [audio focus](DEVELOPER.md#17-audio-focus)
  and [range callbacks](DEVELOPER.md#9-word-highlighting).

The [developer guide](DEVELOPER.md) contains advanced examples.
[Generated API documentation](https://kvnpetit.github.io/better-french-tts/)
follows published stable releases and may lag the development source.

## Demo and development

```sh
./gradlew :app:installDebug
```

Use `./gradlew.bat` on Windows and an authorized device or emulator.
Open **Better French TTS Demo** on the device; see the [demo guide](app/README.md).

The repository keeps the library in `better-french-tts/`, the demonstration app
in `app/`, shared build versions in `gradle/`, and CI in `.github/workflows/`.
Tests live alongside their module under `src/test` and `src/androidTest`.

[Contributing and tests](CONTRIBUTING.md) · [Report a bug](https://github.com/kvnpetit/better-french-tts/issues/new/choose)
· [Changelog](CHANGELOG.md) · [Manual releases](RELEASING.md)

## License

[MIT](LICENSE).
