# ClarID Pocket

Scan ClarID QR labels to view biosample or subject details, including disease
codes and labels. Works offline on **Android 8 or newer**.

## Install

1. Copy the ClarID Pocket APK to your phone. The current development build is
   `clarid-pocket-0.1.8-debug.apk` (available locally in `dist/`).
2. Open the APK on your phone. If prompted, allow installation from the browser
   or file manager you used, then tap **Install**.
3. Open **ClarID Pocket** and tap **Scan QR**. Allow camera access when asked.

You do not need Android Studio or a USB connection to use the app.
There is no published download release yet; the APK must be supplied separately.
To update, open a newer APK signed with the same key and install it over the
existing app.

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
Scanning works without internet, and the app keeps no scan history.

[Developer instructions](DEVELOPMENT.md) ·
[ClarID documentation](https://cnag-biomedical-informatics.github.io/clarid-tools/) ·
[Third-party notices](THIRD_PARTY_NOTICES.md)
