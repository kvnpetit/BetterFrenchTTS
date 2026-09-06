# Manual releases

The maintainer chooses versions, edits `CHANGELOG.md` and publishes releases manually.
No bot opens release PRs or edits version files. The Git tag is the release version;
there is no `version.txt` to keep in sync. This release checkout defaults to `2.1.1`.

## Prepare a release

1. Keep changes in `CHANGELOG.md` under `## [Unreleased]` during development.
2. Choose a new, unused version (this release checkout is **2.1.1**).
   Move its notes into `## [X.Y.Z] - YYYY-MM-DD` with the intended publication date,
   leaving an empty `## [Unreleased]` section above it. Confirm the date again if publication is delayed.
   Use the changelog as the single source for release notes; keep detailed upgrade
   instructions in [MIGRATION.md](MIGRATION.md). During release preparation,
   replace the README's previous dependency with the release tag
   and update its release-status notice. Keep compatibility claims aligned with actual checks.
3. Run the checks below, review the diff, then commit and push the release preparation
   when ready. Wait for all **Verify Better French TTS** jobs to succeed, including
   the Android emulator matrix (API 26 and 37, using image `37.0`).
4. In GitHub **Releases → Draft a new release**, create the new `vX.Y.Z` tag on the
   checked commit. Use `Better French TTS X.Y.Z` as the title and copy that version's
   changelog notes into the description. Include the migration guide for breaking changes.
5. Publish the release. A draft or a pushed tag alone does not trigger artifact publication.

Use the same `vX.Y.Z` tag convention for future releases. Prereleases may use tags
such as `vX.Y.Z-rc.1`; mark them as prereleases in GitHub. Their AAR is uploaded,
but they do not replace the stable documentation website.

Never reuse or move the published `v2.1.0` tag. Workflow improvements on `main`
apply to future releases; they do not change workflows stored in existing tags.

## Checks

With JDK 21 and the Android SDK available, run from the repository root:

```sh
./gradlew checkKotlinFormat assemble testDebugUnitTest lint :better-french-tts:dokkaGeneratePublicationHtml :better-french-tts:publishToMavenLocal '-Pversion=2.1.1'
./gradlew connectedDebugAndroidTest
```

On Windows, use `./gradlew.bat`. Keep the version argument quoted in PowerShell.
The second command needs an authorized Android device or emulator; it checks
Android-specific behavior that host JVM tests cannot validate.

## What happens after publication

The `release.yml` workflow checks out the release tag, validates its format and
dated changelog entry, runs builds/tests/lint and preserves the built AAR as a workflow
artifact. After emulator tests pass, a separate publication job uploads that same
`better-french-tts-release.aar` to the existing release without rebuilding it.
Only the publication and documentation deployment jobs receive repository write
permissions. The workflow never generates or rewrites your release notes.
The shared emulator workflow must pass on API 26 and 37 before the AAR is uploaded
for future releases. The published 2.1.0 workflow used API 26 and 36.

After a successful build, stable releases also generate Dokka documentation and
deploy it to the `gh-pages` branch, preserving the `_versions` archive.
The versioning plugin includes archived publications with `version.json` in its
menu. Historical references are regenerated from their original release tags;
do not fabricate metadata or document current source as an older version. Release API source links use the
release tag. Search, theme switching and package navigation are provided by
Dokka's standard HTML output.

To preview version navigation locally, pass `-PdokkaVersion=X.Y.Z`,
`-PdokkaSourceRef=vX.Y.Z` and `-PdokkaOlderVersionsDir=/path/to/versions`
to `:better-french-tts:dokkaGeneratePublicationHtml`. Each child directory must
contain a previous versioning-enabled Dokka publication, including `version.json`.
Without these options, local documentation is labelled `dev` and links to `main`.
Run `bash scripts/check-docs.sh` after a default local build to check metadata,
the overview and API source links. For a versioned preview, add
`<publication-directory> X.Y.Z A.B.C` to check navigation to an included
older publication and back. CI runs the basic checks before publishing docs.
CI also rehearses the full release documentation pipeline with the real archives:
`bash scripts/build-docs.sh dev <commit-sha> <new-work-directory>`.
The same script is used by stable releases with the release version and tag. It
restores archives, generates the reference, prepares the site and verifies all
navigation before publication. CI previews are never published. Missing historical
metadata fails the build instead of silently dropping an archived version.
The fast script regression suite is
`bash scripts/test-docs.sh <new-test-directory>`; it checks a valid versioned site,
overwrite/nested-output protection, invalid metadata and broken-link detection.

### Regenerate historical references without releasing library artifacts

Run `bash scripts/regenerate-docs.sh /absolute/new/directory`.
This extracts the original stable tags into isolated directories and runs the
current Dokka/Android documentation toolchain against each tag's source files.
It does not check out or edit the working tree, compile historical artifacts,
move tags or create releases. Historical dependency resolution uses the current
documentation toolchain, so this is an API reference rebuild, not a binary rebuild.
The default historical versions are 1.0.0, 1.1.0, 2.0.0 and 2.1.0.

Generate the current stable reference with that output's `versions` directory as
`dokkaOlderVersionsDir`, its exact tag as `dokkaSourceRef`, and its tag's extracted
`better-french-tts/src/main/java` as `dokkaSourceDirectory`. Use
`dokkaOutputDirectory` to keep the generated site separate from other builds.
Then run `bash scripts/prepare-docs.sh <generated-site> <new-deployment-directory>` to create navigable `_versions`
archives, normalize URL separators and preserve immutable archive links.
Run `bash scripts/check-docs-links.sh <new-deployment-directory>` before publishing
only its contents to `gh-pages`. These scripts use Bash (including Git Bash on
Windows); JSON/HTML processing uses Python 3's standard library, with no pip
dependencies. Python 3 is available on the GitHub-hosted Ubuntu runner; install it
locally and ensure `python3` is on PATH when using Git Bash. No PowerShell is needed.
Publish stable versions in increasing order: each stable release becomes the
documentation website's current version.

Check that the workflow succeeds, the AAR is attached and the documentation is available.
Then resolve the tagged dependency through JitPack and verify its build before announcing it:

```kotlin
implementation("com.github.kvnpetit:better-french-tts:v2.1.1")
```

GitHub releases and JitPack builds are separate. The release workflow does not
publish to Maven Central. JitPack builds the requested tag using `jitpack.yml`.
If the workflow fails, the GitHub release is already public: inspect the failure
and rerun the failed jobs for a transient problem. For a source fix, publish a new
version rather than moving an existing tag. A rerun replaces the attached AAR.

When preparing the following development cycle, update the default snapshot in
`better-french-tts/build.gradle.kts` and the verification workflow's version argument.

## Historical automation

Release-please is no longer used. Its former release PR #2 is closed; do not merge
old automation branches. Existing tags, releases and changelog history remain intact.
