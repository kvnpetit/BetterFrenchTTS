# Contributing

Bug reports, documentation corrections and focused fixes are welcome. This is a
small maintainer-led library: discuss substantial API or architecture changes in an
issue before starting a large implementation. English and French reports are welcome.

## Local setup

1. Install JDK 21 and Android Studio or the Android command-line tools.
2. Clone the repository and open its root in Android Studio.
3. Install the Android SDK required by the Gradle build (currently platform 36.1).
   Set `ANDROID_HOME` or use a local `local.properties` with `sdk.dir`.
4. Use `./gradlew` on Linux/macOS or `./gradlew.bat` in PowerShell.

Do not commit IDE/device configuration, SDK paths, credentials or generated build
outputs. `.idea/` remains local. Respect `.editorconfig` and `.gitattributes`.

## Project layout

- `better-french-tts/`: published Android library, with packages for preprocessing,
  SSML, DSL construction, spelling and voice selection.
- `app/`: runnable demo; its dependency points to the local library module.
- `gradle/`: wrapper and shared dependency/plugin versions.
- `.github/workflows/`: validation and manually triggered release publication.
- `DEVELOPER.md` and `docs/`: detailed usage and compatibility information.

## Run checks

```sh
./gradlew assemble testDebugUnitTest lint :better-french-tts:dokkaGeneratePublicationHtml :better-french-tts:publishToMavenLocal '-Pversion=2.0.0-SNAPSHOT'
```

For Android tests, start an emulator or connect a device and accept its debugging
authorization. Confirm that `adb devices` reports `device`, not `unauthorized`:

```sh
./gradlew connectedDebugAndroidTest
./gradlew :app:installDebug
```

Host JVM tests cover deterministic text/markup behavior. Instrumented tests cover
Android's regex engine, manifest/package integration and demo UI. They are required
for changes involving Android behavior; a green host test suite is insufficient.
The CI runs host checks plus emulator tests on API 26 and 36 and saves test reports.

The demo's audible checks additionally need an installed TTS engine and downloaded
French voice. Test initialization, speech start/completion, stop and the changed
feature. Report the engine/voice and whether you listened to the output. Do not
claim acoustic or SSML correctness solely from a successful callback.

## Propose a change

- Keep changes focused and follow existing Kotlin/package conventions.
- Add a regression test that fails for the reported bug; avoid placeholder tests.
- Explain user-visible changes under `Unreleased` in `CHANGELOG.md`.
- Update the developer/compatibility guides if API behavior or limitations change.
- Describe the change, tests run and any untested platforms in the PR template.
- Use Conventional Commit titles, such as `fix: handle empty speech input`.
  Breaking API changes must be described explicitly.

Do not update dependency versions, rewrite unrelated code, create tags or publish
releases as part of an unrelated fix. Releases are maintained manually following
[RELEASING.md](RELEASING.md).

## Report a bug

Use the [bug form](https://github.com/kvnpetit/better-french-tts/issues/new?template=bug_report.yml).
Include a minimal reproduction, the exact library tag/commit, Android API level,
TTS engine and voice, expected/actual behavior and relevant logs. Remove private
text, identifiers and credentials from examples and logs. Search existing issues first.
Be respectful and focus discussion on reproducible behavior.
