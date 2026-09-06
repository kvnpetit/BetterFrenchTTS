# Better French TTS — Demo App

Interactive demo application for exercising the Better French TTS APIs. Generated
SSML and its audible interpretation are different: read the
[compatibility guide](../docs/compatibility.md) for engine-dependent limitations.

## Run the demo

```bash
./gradlew :app:installDebug
```

Or open the project in Android Studio and run the `app` module.

Use `./gradlew.bat` in PowerShell. The installed label is **Better French TTS Demo**
and its application ID is `io.github.kvnpetit.betterfrenchtts.demo`.

> **For audible tests:** install/configure a TTS engine and download French voice data
> in its settings. Google TTS with `fr-fr-x-frd-local` was used for the local Android 16
> smoke test. Other engines and voices may behave differently. UI tests do not need voice data.

## Features demonstrated

### Simple speech
- Free text input with **Speak** button
- **Spell out** button (character by character)
- **Stop** button
- **Real-time word highlighting** of the currently spoken word

### Presets (12 built-in + custom)
- Grid of 12 built-in presets (Neutral, Calm, Excited, Teaching, Storytelling, News, Whisper, Announcement, Reading, Dictation, Notification, Meditation)
- Custom preset (configurable rate, pitch, volume)

### DSL
- Variable rate (`slow`, `fast`)
- Variable volume (`soft`, `loud`)
- Spell out + telephone (`spellOut`, `telephone`)
- Numbers and ordinals (`number`, `ordinal`)
- Paragraphs, emphasis and pitch (`paragraph`, `sentence`, `strong`, `lowPitch`, `pitch`)
- Preset + DSL combined (`speakWithPreset`)
- Chaining presets in the DSL (`withPreset`)

### Text preprocessing
- Abbreviations (M., Mme, Dr, bd)
- Ordinals (1er, 3ème, 20ème)
- Time formats (14h30, 8h)
- Units (km, min, km/h, °C)
- Currencies and percentages (€, $, £, %)
- Roman numerals (XIV, XVIIe siècle)

### Pronunciation dictionary
- Add aliases (Huawei → Oua-ouei, Xiaomi → Chao-mi)
- Add IPA rules (Lacoste → la.kɔst, Nutella → nu.tɛ.la)
- Test in a sentence
- Clear all rules

### Speech queue
- Launch a queue of 4 items (text + presets + DSL)
- Controls: Pause / Resume / Skip / Clear
- Real-time progress display

### Coroutines
- Sequential `speakAndAwait` (2 phrases chained automatically)
- `speakAndAwait` with DSL
- Real-time status updates

### Raw SSML
- Editable text field with SSML markup
- Direct playback via `speakSsml`

### Synthesize to file
- Save text as a WAV file in the app's cache directory
- Toast notification with the file path

### SSML preview
- View generated SSML (Neutral / Storytelling / DSL)
- Monospace display in a Card

### Audio focus
- Mode selection (NONE / DUCK / GAIN_TRANSIENT)
- TTS instance recreated on mode change
- Test with background music

### Voice selection
- List available offline French voices
- Manually select a voice
- Display voice name and quality score

## Tech stack

- Jetpack Compose + Material 3
- Kotlin Coroutines
- Uses the local Better French TTS module and AndroidX UI/lifecycle dependencies

## Verification

Run `./gradlew :app:connectedDebugAndroidTest` on an authorized emulator/device.
The smoke tests check the demo identity and editable speech input. For playback,
verify initialization, start/completion, stop and the feature under test, and record
the engine/voice. Successful callbacks alone do not verify pronunciation quality.
