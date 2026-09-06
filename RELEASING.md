# Manual releases

The maintainer chooses versions, edits `CHANGELOG.md` and publishes releases manually.
No bot opens release PRs or edits version files. The Git tag is the release version;
there is no `version.txt` to keep in sync. Local builds default to `2.0.0-SNAPSHOT`.

## Prepare a release

1. Keep changes in `CHANGELOG.md` under `## [Unreleased]` during development.
2. Choose a version. The naming/package migration requires **2.0.0**.
   Move its notes into `## [2.0.0] - YYYY-MM-DD` with the actual release date,
   leaving an empty `## [Unreleased]` section above it. Do not date an unpublished release prematurely.
   Use the changelog as the single source for release notes; keep detailed upgrade
   instructions in [MIGRATION.md](MIGRATION.md). During release preparation,
   replace the README's development dependency with the release tag
   and update its release-status notice. Keep compatibility claims aligned with actual checks.
3. Run the checks below, review the diff, then commit and push the release preparation
   when ready. Wait for all **Verify Better French TTS** jobs to succeed, including
   the Android emulator matrix (API 26 and 36).
4. In GitHub **Releases → Draft a new release**, select or create `v2.0.0` on the
   checked commit. Use `Better French TTS 2.0.0` as the title and copy that version's
   changelog notes into the description. Include the migration guide for breaking changes.
5. Publish the release. A draft or a pushed tag alone does not trigger artifact publication.

Use the same `vX.Y.Z` tag convention for future releases. Prereleases may use tags
such as `v2.1.0-rc.1`; mark them as prereleases in GitHub. Their AAR is uploaded,
but they do not replace the stable documentation website.

## Checks

With JDK 21 and the Android SDK available, run from the repository root:

```sh
./gradlew assemble testDebugUnitTest lint :better-french-tts:dokkaGeneratePublicationHtml :better-french-tts:publishToMavenLocal '-Pversion=2.0.0'
./gradlew connectedDebugAndroidTest
```

On Windows, use `./gradlew.bat`. Keep the version argument quoted in PowerShell.
The second command needs an authorized Android device or emulator; it checks
Android-specific behavior that host JVM tests cannot validate.

## What happens after publication

The `release.yml` workflow checks out the release tag, validates its format and
dated changelog entry, runs builds/tests/lint and uploads `better-french-tts-release.aar`
to the existing release. It never generates or rewrites your release notes.
The shared emulator workflow must pass on API 26 and 36 before the AAR is uploaded.

After a successful build, stable releases also generate Dokka documentation and
deploy it to the `gh-pages` branch, preserving the `_versions` archive.
Publish stable versions in increasing order: each stable release becomes the
documentation website's current version.

Check that the workflow succeeds, the AAR is attached and the documentation is available.
Then resolve the tagged dependency through JitPack and verify its build before announcing it:

```kotlin
implementation("com.github.kvnpetit:better-french-tts:v2.0.0")
```

GitHub releases and JitPack builds are separate. The release workflow does not
publish to Maven Central. JitPack builds the requested tag using `jitpack.yml`.
If the workflow fails, the GitHub release is already public: inspect the failure
and rerun the failed jobs for a transient problem. For a source fix, publish a new
version rather than moving an existing tag. A rerun replaces the attached AAR.

When preparing the following development cycle, update the default snapshot in
`better-french-tts/build.gradle.kts` and the verification workflow's version argument.

## Transition from the previous automation

After these local changes are committed and pushed, close the old automated release
PR #2 without merging it. Its migration notes are already included in `Unreleased`.
Closing it before the workflow removal reaches GitHub would leave the old bot able
to recreate or update it on another push. Existing tags, releases and changelog history
remain intact.
