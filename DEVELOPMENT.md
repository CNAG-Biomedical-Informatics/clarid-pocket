# Developing ClarID Pocket

Build instructions, device checks, and implementation details.
For installation and everyday use, see [README.md](README.md).

## Build

Requirements: JDK 17 or 21; Android SDK platform 36 and build tools 35.0.0.
Gradle 8.13, Android Gradle Plugin 8.13.2, and Kotlin 2.2.21 are pinned.
The app supports Android 8/API 26 and later, targeting API 36.

On a standard supported Android development host, set `sdk.dir` in the ignored
`local.properties` or set `ANDROID_HOME`, then run:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The current Linux ARM64 VM uses a community SDK at `../android-sdk-arm64`.
Its native ARM64 AAPT2 needs an override because AGP normally fetches an x86-64
binary. The helper applies it without adding machine-specific paths to Gradle:

```sh
bash tools/android-gradle.sh -g /tmp/clarid-pocket-gradle-home \
  :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The `-g` option reuses this session's downloaded dependencies; it can be omitted
to use the normal persistent Gradle cache. The first build needs network access
for build dependencies. The installed app does not need those downloads.

Output: `app/build/outputs/apk/debug/app-debug.apk` (development signing).
Tests only, without configuring an Android SDK:

```sh
./gradlew -PcoreOnly=true :core:test
```

Android Studio can open this directory directly. On the ARM64 Linux VM use the
helper above. On macOS use the Mac's SDK path in `local.properties` and the
standard Gradle command; do not carry the Linux AAPT2 override to macOS.

## Try it on the Galaxy A51

USB is useful during development because it permits installation, diagnostics,
and screenshots. Enable USB debugging on the phone and attach it to the Linux
VM through Parallels. Unlock the phone and accept the debugging authorization.

```sh
../android-sdk-arm64/platform-tools/adb devices
../android-sdk-arm64/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also transfer the APK to the phone and open it manually. Android may
ask you to permit installation from the app used to open the file.

For the first offline test, install without launching, enable airplane mode,
then launch ClarID Pocket. Leave **Auto** selected (default stub width 3, human
width 5). Tap **Scan QR**, allow Camera, and scan this example from another screen.
A single visible QR scans immediately, without holding still or fitting it
inside the guide. If several are visible, aim the centre crosshair at the one
you want. An ambiguous target keeps the camera open. Tap to focus or pinch to zoom:

![Stub biosample QR](examples/stub_basic.png)

Expected: project TCGA-AML; human; subject 1; liver; tumor; RNA-seq; challenge;
P1M; disease **I25.110**, “Atherosclerotic heart disease of native coronary artery
with unstable angina pectoris”.

More examples:

- [Two diseases, stub width 3](examples/stub_multiple_conditions.png):
  I25.110 and C22.0, with separate labels; C22.0 is “Liver cell carcinoma”.
- [Human identifier, decimal width 5](examples/human_basic.png): Auto detects Human; semantic fields match the basic stub example.
- [Subject, stub](examples/subject_stub_basic.png) and
  [subject, human](examples/subject_human_basic.png): Auto displays study, subject
  ID, type, sex, age group, and diseases.
- [Subject with two diseases](examples/subject_stub_multiple_conditions.png).
- Raw payloads are in the corresponding `examples/*.txt` files.

Device checks still required: fresh-install offline scanning; permission denial
and retry; background/foreground and rotation; repeated scans; malformed QR
handling; settings restoration; legibility at larger font sizes. The APK build,
lint, and core tests do not replace these camera and visual checks.

## Compatibility and data handling

- Only codebook version 0.04 is supported. Legacy versions and unimplemented
  pattern profiles are rejected. The UI currently uses the bundled book only.
- Defaults are decimal subject width 5 and Base62 width 3. Configurable bounds
  are 1–18 decimal digits and 1–10 Base62 characters (signed 64-bit values).
- One to ten conditions. Biosamples support one-digit durations or P0N and
  optional Bdd then Rdd.
  Human condition syntax follows the pinned numeric-suffix profile. Arbitrary
  Perl regexes/sprintf patterns are not evaluated as Kotlin expressions.
- Parsing rejects malformed tails, bad counts, whitespace, overflowing fields,
  unknown vocabulary values, and unknown condition ordinals. It is intentionally
  stricter than permissive behaviors of the reference decoder.
- Subject study names are free strings in the reference format; they need not
  be in the codebook. Known study names/stubs receive a codebook label. Subject
  type, sex, and age group must match the codebook; stub widths are 1/1/2.
- Missing disease labels preserve the code and display “Label unavailable”.
  This does not permit unknown condition ordinals. Human codes are validated
  against the supported syntax; dictionary coverage is a separate check.
- The injected Not Available species stub has a different width from the other
  species stubs; it is not supported in this initial fixed-width stub decoder.
- The scanning model and codebooks are bundled. Camera is the only requested
  Android runtime permission; Internet and network-state permissions are removed
  from the merged manifest. There is no app backend, app analytics, or identifier
  logging. Only decoding settings are persisted; scans are held in memory.
- The app stores no scan history. Backup and device transfer are disabled.

## On-device storage

The application ID is `org.cnag.clarid.pocket`. Android stores its data in the
app's private internal directory. For the primary Android user, the settings
file is normally:

```text
/data/user/0/org.cnag.clarid.pocket/shared_prefs/decode-settings.xml
```

The Android user/profile number can differ. The app uses Android's
`getSharedPreferences("decode-settings", MODE_PRIVATE)` API rather than a
hard-coded filesystem path. These are private preferences, not an encrypted
vault or a file exposed in Downloads.

Only four preferences are persisted: `format-v2`, `entity`, `human-width`, and
`stub-width`. QR text, decoded fields, disease results, and errors live in the
ViewModel's memory; there is no scan database, scan history, or camera-image
storage. Starting another scan or changing settings clears the current result;
process termination also loses it.

The codebook, schema, ICD-10 dictionaries, and barcode model are packaged inside
the APK. They are not downloaded at runtime. The app has no Internet permission.
Cloud backup and device transfer are excluded by the manifest and data-extraction
rules. Uninstalling the app or clearing its storage removes the saved settings.
Normal updates signed with the same key preserve them.

## Reference and verification

Use the [ClarID-Tools documentation](https://cnag-biomedical-informatics.github.io/clarid-tools/)
for the shared identifier specification and codebook details. The README covers installation and everyday use; this document covers development
and the supported implementation subset.

The Kotlin engine is checked in both explicit and Auto modes against **76 JSON
fixtures** (31 biosamples, 45 subjects) generated by ClarID-Tools release 0.04 at
commit `3789effd7481cef5928a251095f2b7f792b9fa3f`. Fifteen core JUnit tests cover
all decoded fields/labels, entity/format detection and ambiguity, manual
overrides, invalid inputs/settings, defaults, safe YAML loading, unsupported
versions, missing labels, and asset checksums. Five app unit tests cover immediate
capture, neighbouring/duplicate codes, oversized QR codes, and visible targets.

`core/src/main/resources/clarid/0.04/provenance.json` records source paths and
SHA-256 hashes. The scripts `tools/snapshot_reference.py`,
`tools/generate_reference_fixtures.py`, and `tools/generate_subject_fixtures.py`
reproduce reference assets/fixtures from the pinned sibling checkout without
modifying it. The installed app has no dependency on that checkout or Perl.

The ARM64 SDK comes from
[android-sdk-custom release 37.0.0](https://github.com/HomuHomu833/android-sdk-custom/releases/tag/37.0.0).
It is a community build, not an official Linux ARM64 Android Studio release.
Its downloaded archive checksum is recorded in the SDK's
`CLARID_INSTALL_SOURCE.json`.

See [third-party notices](THIRD_PARTY_NOTICES.md) for bundled data provenance.

## Signed releases

Release builds use a dedicated private key. Keep the keystore and its passwords
outside Git and back up both in secure storage; future Android updates must use
the same key. Debug builds continue using Android's debug key.

Gradle reads `~/.config/clarid-pocket/signing.properties` by default, or the path
in `CLARID_SIGNING_PROPERTIES`. That private file contains `storeFile` (absolute
keystore path), `storePassword`, `keyAlias`, and `keyPassword`. Never commit it.
Release packaging fails if the signing setup is missing.

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Commit the release changes and create an annotated tag matching `versionName`
   (for example `git tag -a v0.1.8 -m "ClarID Pocket 0.1.8"`).
3. Build the clean tagged commit with the private signing setup:

   ```sh
   bash tools/android-gradle.sh :core:test :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
   ```

4. Verify the APK with Android SDK `apksigner verify --verbose --print-certs`
   and `zipalign -c -P 16 4`. Confirm its version and release certificate.
5. Copy `app/build/outputs/apk/release/app-release.apk` to a versioned APK in
   `dist/` and generate a matching SHA-256 checksum.
6. Push the commit and tag. Create or update a **draft** GitHub Release for that
   tag with the APK, checksum, and installation notes; review before publishing.

The first release-key APK requires uninstalling an existing debug-key build
before installation. This clears its saved settings. Subsequent release-key
updates install over the existing app.
