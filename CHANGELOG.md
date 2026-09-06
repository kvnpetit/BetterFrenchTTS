# Changelog

This changelog is maintained manually. Add user-facing changes under `Unreleased`;
move them into a dated version section when preparing a release. See [RELEASING.md](RELEASING.md).

## [Unreleased]

Development target: **2.1.0** (`2.1.0-SNAPSHOT`). These changes have not been
released; the existing `v2.0.0` tag and release remain unchanged.

### Added

- Native Android playback for text, aliases, real silences, rate, pitch and volume;
  serial scheduling preserves controls across chunks and queued requests.
- French cardinal/ordinal, decimal, money, validated date, telephone and duration
  formatting; France conventions by default, explicit regional alternatives.
- Configurable normalization with an explanatory preview and protected URLs,
  email addresses, inline code and mixed identifiers.
- Whole-word pronunciation dictionaries with literal matching, case options,
  versioned export/import and atomic validation.
- Engine selection, strict offline voice filtering and optional exact-locale policy.
- French regression corpus, controlled-engine lifecycle tests, normalization preview
  and same-voice raw Android comparison in the demo.

### Changed

- Native playback is now the default. IPA requires explicit SSML playback and a
  compatible engine; unsupported native interpretations return errors.
- Initialization fails when no eligible French voice is available. Voice changes
  are checked before updating the reported active voice.
- Public configuration/rule constructors have new parameters: recompile consumers;
  binary compatibility with precompiled 2.0.0 consumers is not guaranteed.

### Fixed

- French currency spacing, singular hours/currencies, extended ordinals, protected
  identifiers, Unicode spelling, queue rejection, file completion callbacks and
  interrupted coroutine cleanup. Money conversion remains compatible with API 26.

See [migration](MIGRATION.md) for behavior changes and [evaluation](docs/evaluation.md)
for the distinction between automated correctness and audible quality.

## [2.0.0] - 2026-09-06

### Breaking changes

- Rename the repository and JitPack artifact to `better-french-tts`.
  The new dependency is `com.github.kvnpetit:better-french-tts:<tag>`.
- Move Kotlin packages from `com.github.kvnpetit.betterfrenchtts` to
  `io.github.kvnpetit.betterfrenchtts`. Update imports and recompile consumers;
  see [MIGRATION.md](MIGRATION.md).
- Change the demo application ID to `io.github.kvnpetit.betterfrenchtts.demo`.
  It installs separately from the old demo.

### Changed

- Use **Better French TTS** as the library's display name and **Better French TTS Demo** for the demo.
- Prepare releases and release notes manually; retain automatic build checks,
  AAR uploads and stable API documentation deployment.
- Simplify the README, document engine/voice compatibility and contribution steps,
  and keep local IDE configuration out of the repository.

### Fixed

- Prevent a crash on Android when speech preprocessing initializes: the Roman numeral
  expression now works with Android's ICU regex engine and preserves its surrounding text.
- Declare TTS service visibility in the library manifest for Android 11+ consumers.
- Escape all SSML attribute values and preserve Unicode surrogate pairs when splitting text.
- Render French century names with correct ordinal words.
- Split long nested DSL content and account for XML escaping when sizing speech chunks.
- Return synchronous engine failures instead of reporting successful chunk dispatch;
  complete suspended speech requests when the engine rejects them or playback stops.

### Added

- Regression tests for French preprocessing, Roman numerals, text chunking and SSML rendering,
  including tests executed on Android.
- Continuous build, lint and Maven publication checks.
- Emulator CI for API 26 and 36, demo UI smoke tests, and downloadable CI test reports.
- Controlled-engine tests for rejection, coroutine completion, shutdown and long speech,
  plus boundary, Unicode, XML and French normalization regression tests.
- Bug-report and pull-request templates, and consistent editor/line-ending settings.

## [1.1.0](https://github.com/kvnpetit/BetterFrenchTTS/compare/v1.0.0...v1.1.0) (2026-03-18)


### Features

* add advanced speech queue with pause, resume, and skip controls ([def8bb3](https://github.com/kvnpetit/BetterFrenchTTS/commit/def8bb31b308819213297693c1dd604478047051))
* add all library features to demo app ([2893d31](https://github.com/kvnpetit/BetterFrenchTTS/commit/2893d31435b54e04c38f432e9a689ff5d1b79473))
* add automatic audio focus management ([ee05840](https://github.com/kvnpetit/BetterFrenchTTS/commit/ee05840824ced5aa1ba063afba9299b81a6c82c7))
* add intelligent French text preprocessing ([e43e191](https://github.com/kvnpetit/BetterFrenchTTS/commit/e43e1916517d4a312a8cdfe92cf94acb22710211))
* add IPA phoneme support with &lt;phoneme&gt; SSML tag ([8bd2c58](https://github.com/kvnpetit/BetterFrenchTTS/commit/8bd2c584e8d8e3a47089df01e162d81a9322e33e))
* add pronunciation dictionary with SSML &lt;sub&gt; support ([cdde549](https://github.com/kvnpetit/BetterFrenchTTS/commit/cdde549e3d4333585425e5753caa5b042f6c7e4b))
* add word-by-word highlight tracking via onRangeStart ([7066c98](https://github.com/kvnpetit/BetterFrenchTTS/commit/7066c989a6e44f9416cd2c8403683d051ce82963))


### Bug Fixes

* add auto-chunking support to DSL speak and speakAndAwait methods ([d1a6c2d](https://github.com/kvnpetit/BetterFrenchTTS/commit/d1a6c2d6e1d9be2b5246c84e9ed8d3558456e6e1))
* add auto-chunking support to speakAndAwait(text) ([6469b7f](https://github.com/kvnpetit/BetterFrenchTTS/commit/6469b7fd7a9b0d75cf9d3f4ca0fe6f496d563ee7))
* declare singleVariant for maven-publish compatibility ([36ece39](https://github.com/kvnpetit/BetterFrenchTTS/commit/36ece39cf93df4c6a9ade76794334e54142c6f10))
* resolve Dokka versioning olderVersionsDir type mismatch ([6fa9bcf](https://github.com/kvnpetit/BetterFrenchTTS/commit/6fa9bcf7c3dd4afdd2d76d103fd84ffbe66ac5ae))
