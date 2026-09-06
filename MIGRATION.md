# Migrating to Better French TTS 2.x

The library is now named **Better French TTS**, with repository and artifact
`better-french-tts`. This is the same library under consistent names, not a
separate speech engine. The package migration is a breaking source and binary change.

This source tree targets the next 2.x release. It does not mean a new release
has already been published. Use the first new tag containing the migration, or
the full SHA of its published commit on JitPack. Do not use an old 1.x tag to
obtain the new packages.

## Dependency

Keep `maven { url = uri("https://jitpack.io") }` in your dependency repositories.
Replace the old dependency:

```kotlin
implementation("com.github.kvnpetit:BetterFrenchTTS:<old-tag>")
```

with:

```kotlin
implementation("com.github.kvnpetit:better-french-tts:<new-tag-or-commit>")
```

Remove the old dependency to avoid including both versions. The JitPack group
remains `com.github.kvnpetit`; the Kotlin package is independent of that group.
`io.github.kvnpetit:better-french-tts` is not a published Maven Central coordinate.

## Kotlin imports and integration

Replace `com.github.kvnpetit.betterfrenchtts` with
`io.github.kvnpetit.betterfrenchtts` in imports, fully qualified names,
reflection strings and any consumer R8/ProGuard rules. Recompile consumers.

```kotlin
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.SpeechPreset
```

The entry class remains `BetterFrenchTts`; existing methods and speech behavior
are unchanged. The local module dependency remains `project(":better-french-tts")`.

## Repository, documentation and demo

- Repository: https://github.com/kvnpetit/better-french-tts
- Documentation: https://kvnpetit.github.io/better-french-tts/
- Gradle root project and checkout folder: `better-french-tts`.
- Demo label: `Better French TTS Demo`.
- Demo application ID: `io.github.kvnpetit.betterfrenchtts.demo`.

The demo installs as a separate application from the old demo; Android does not
automatically transfer its data. Update scripts that launch it by package name.
Git history, old tags and release assets are preserved. Historical changelog
entries retain their original links. Update bookmarks to the new Pages URL;
GitHub does not redirect renamed project Pages sites automatically.

## Local verification

With JDK 21 and the Android SDK configured, run (`gradlew.bat` on Windows):

```sh
./gradlew assemble testDebugUnitTest lint \
  :better-french-tts:dokkaGeneratePublicationHtml \
  :better-french-tts:publishToMavenLocal '-Pversion=2.0.0-SNAPSHOT'
```

With an Android device or emulator connected, also run
`./gradlew connectedDebugAndroidTest` to verify the application package IDs.
Publishing to Maven local does not publish a public release. Validate JitPack
resolution against the pushed migration commit before announcing a release.
