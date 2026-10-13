# ClarID-Pocket

Scan ClarID QR labels to view biosample or subject details, including disease
codes and labels. Works offline on **Android 8+**. An **iOS/iPadOS 15+ developer
proof of concept** is also available as source.

## Install on Android

1. Download the `.apk` file from
   [GitHub Releases](https://github.com/CNAG-Biomedical-Informatics/clarid-pocket/releases)
   to your phone.
2. Open the APK on your phone. If prompted, allow installation from the browser
   or file manager you used, then tap **Install**.
3. Open **ClarID-Pocket** and tap **Scan QR**. Allow camera access when asked.

You do not need Android Studio or a USB connection to use the app.
To update, open a newer APK signed with the same key and install it over the
existing app.

## iOS — developer proof of concept

The native SwiftUI app has been tested on a physical iPad. Installation requires
a **Mac, Xcode, and your own Apple signing account**; the device may ask you to
trust your developer certificate. With a free Personal Team, signing expires
after **seven days**, requiring a rebuild and reinstall through Xcode.

This is a developer testing route. No public IPA, TestFlight, or App Store
download is provided. See the [iOS setup and limitations](ios/README.md).

## Scan a label

Leave **Auto** selected and point the camera at a ClarID QR code. If several
codes are visible, aim the centre crosshair at the one you want. Tap to focus
or pinch to zoom.

A checkmark confirms decoding. **Detected: Subject** or **Detected: Biosample**
identifies the result, followed by its decoded fields and disease labels.

Try this QR from another screen:

![Example biosample QR](examples/stub_basic.png)

The **gear button** opens settings: choose the identifier entity first, then
its format. **Advanced** contains the subject widths. Widths must match those used to create
the label; Auto does not infer them.

## Compatibility and privacy

Supports **ClarID 0.04** with the bundled codebook, in Human and Stub formats.
Older ClarID releases and codebook downloads are not supported.
In **Settings → About**, **Check for updates** checks published app releases on
GitHub and offers a download page for newer versions. This optional check needs
internet; it never uploads scanned identifiers.
Scanning works without internet, and the app keeps no scan history.

[Developer instructions](DEVELOPMENT.md) ·
[ClarID documentation](https://cnag-biomedical-informatics.github.io/clarid-tools/) ·
[Third-party notices](THIRD_PARTY_NOTICES.md)
