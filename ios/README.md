# ClarID-Pocket for iOS — developer proof of concept

Native SwiftUI app for iPhone and iPad, targeting iOS/iPadOS 15 or later.
Scan ClarID 0.04 subjects and biosamples offline, with automatic entity/format
detection and disease labels. Built and launched in Xcode; scanning, focus,
zoom, settings, and the update check have been tested on a physical iPad.

## Installation limits

This release provides source for developers to build and test with **Xcode on
a Mac and their own Apple signing account**. It is not a public iOS installer;
there is no IPA, TestFlight invitation, or App Store listing.

With a free **Personal Team**, provisioning profiles expire after **seven days**.
Rebuild and reinstall through Xcode to continue using the app. The device may
require you to trust your developer certificate. These limits make this route
unsuitable for routine installation by end users. See
[Apple's Personal Team limits](https://developer.apple.com/help/account/basics/about-your-developer-account).

## Build and run with Xcode

1. Make the **whole repository** available to macOS; the project reads reference
   dictionaries from `../core/`.
2. Open `ios/ClarIDPocket.xcodeproj` in Xcode and select the `ClarIDPocket` scheme.
3. To preview the interface, choose an iPhone or iPad simulator and press **Run**.
   Camera scanning requires a physical device.
4. For QR testing, connect the iPad to the Mac, unlock it, and trust the Mac.
   Select your Apple signing team under **Signing & Capabilities**, then select
   the iPad as the run destination. Enable Developer Mode if the iPad requests it.
5. Press **Run** (⌘R) to install and launch. If the device reports an untrusted
   developer, open **Settings → General → VPN & Device Management**, select your
   signing account, and trust it. Allow camera access when the app opens.

Run the decoder tests and simulator build from the repository root on macOS:

```sh
bash ios/tools/check_macos.sh
```

## Development

`Core/` is a Foundation-only Swift package. Its tests use the same 76 ClarID-Tools
fixtures and ICD-10 dictionaries as Android, plus malformed input, ambiguity,
width, overflow, and scanner-selection checks:

```sh
swift test --package-path ios
```

`App/` contains SwiftUI screens and an AVFoundation QR scanner. A single visible
QR captures immediately; with multiple codes, the centre selects one. Tap to
focus and pinch to zoom. Camera capture stops when the scanner closes or the app
leaves the foreground.

Only entity, format, and subject widths persist in the app's `UserDefaults`.
Scanned identifiers and results stay in memory; there is no scan history or
payload upload. The app bundles ClarID 0.04 only. **Check for updates** makes a
manual request to GitHub's latest stable release endpoint. It compares app
versions, reports connection and rate-limit errors, and opens official release
details in the browser. It never installs packages or downloads codebooks.
Android-only releases are clearly identified as having no iOS package.
An iOS package is recognized as `clarid-pocket-VERSION-ios.ipa` or
`clarid-pocket-VERSION.ipa` with GitHub's `uploaded` state. App Store or TestFlight
distribution can be added when those channels are configured.

`Resources/clarid-codebook.json` is generated from the authoritative Android YAML.
After changing that YAML, regenerate and verify it (requires Python and PyYAML):

```sh
python3 ios/tools/prepare_reference.py
python3 ios/tools/prepare_reference.py --check
```

After adding Swift files, regenerate the committed Xcode project:

```sh
python3 ios/tools/generate_project.py
```

The app icon uses the existing Android artwork. Its generator requires Pillow.
The Xcode project has no external Swift package dependencies. Android builds and
releases remain independent. Bundled data licensing and provenance are recorded
in the repository's `THIRD_PARTY_NOTICES.md` and `core/` resources.
