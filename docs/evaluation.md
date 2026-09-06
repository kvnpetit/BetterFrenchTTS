# French (France) evaluation protocol

The library is a preparation/control layer, not a voice model. Passing tests does
not establish perfect French pronunciation, liaison, homograph disambiguation,
regional accent or emotional expressiveness.

## Automated coverage

Run `testDebugUnitTest` and `connectedDebugAndroidTest`. The reference corpus
contains 200 assertions: 40 authored examples repeated in five prefix contexts,
not 200 independent linguistic phenomena. Separate tests cover formats, dictionary
round-trips, Unicode, native compilation, scheduling, cancellation and rejection.
Controlled engines verify API contracts, not acoustic quality.

## Listening comparison

1. Record the commit, device/API, engine/version, exact voice, network state and
   selected options. For France, use `Locale.FRANCE` and `requireExactLocale = true`.
2. Install the demo. Preview normalization before listening; verify intended meaning.
3. Compare native library playback and “Android brut, même voix”. Use the same
   voice, baseline rate/pitch/volume and warm up both engines first.
4. Randomize order, hide which variant is playing when possible, and have French
   listeners score intelligibility, meaning preservation and naturalness separately.
5. Include prices, dates, telephone numbers, acronyms, proper names, addresses,
   ambiguous words, negation, liaison and long passages. Include unchanged controls
   and cases where normalization should be disabled.
6. Repeat timings; report sample count, median and p95. The demo's first-start
   timing includes scheduling/engine work and is not an isolated synthesis benchmark.
7. Test offline, missing voice data, interruptions, errors and multiple real devices.

Do not use a successful callback, generated SSML or speech-recognition transcript
alone as proof of pronunciation quality. Report regressions as well as improvements.
No cross-engine listening benchmark is claimed by this repository.

## Local verification — 2026-09-06

The 2.1 development changes passed 251 JVM tests and 25 Android tests (22 library,
3 demo) on the Android 16 / API 36.1 emulator. Debug/release assembly, Lint, Dokka
and publication to local Maven as 2.1.0-SNAPSHOT succeeded. Library Lint reports no
issues; demo Lint reports zero errors with dependency/resource warnings remaining.
The demo initialized with Google TTS voice `fr-fr-x-frd-local`, completed a native
speech cycle, displayed normalization and started the same-voice raw comparison.
These checks did not include a scored listening study or real-device/API 26 run.
