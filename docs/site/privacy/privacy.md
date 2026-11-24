# Offwire privacy policy

Effective date: 2026-11-21

This policy covers Offwire (app.offwire.classic, on Android and iPhone) and Offwire Console
(app.offwire.console, on Android).

This policy describes the store builds from Google Play and the App Store. Developer builds, including those
attached to GitHub Releases, can also save debug audio clips, which stay on the device.

## Summary

Offwire runs its speech recognition, language model and voice on your device. Your voice, your conversations
and your notes never leave it. There is no account, no analytics, no crash reporting, no advertising and no
third-party tracking. We do not collect, receive or share any of your data.

## What the app connects to

The app goes online only to download its model files, once, when you start the download. These requests
fetch files and send nothing you said or typed.

| File | App | Host |
|---|---|---|
| Gemma 4 language model (E2B or E4B) | Both apps | huggingface.co |
| Computer voice (Pocket TTS) | Offwire Console | github.com |

Both hosts redirect the download to their storage servers (today us.aws.cdn.hf.co for Hugging Face and
release-assets.githubusercontent.com for GitHub), and the app follows those redirects. Every download uses
HTTPS. As with any download, these services see your IP address and the standard request headers, under their
own privacy policies. After the download the app works with no connection.

## What stays on your device

- Microphone audio. Offwire listens when you tap to talk, and, if you turn on the wake word, listens for it
  while the app is open. Audio is processed in memory and is not saved.
- Conversations. The chat shown on screen is kept in memory only and is gone when the app closes.
- Notes. Notes you ask the assistant to save are stored in the app's private storage. On Android they are
  encrypted with a key held in the Android Keystore. On iPhone they are protected by the device's file
  encryption.
- Settings. Your choices, such as the assistant name, wake word and selected model, are stored in the app's
  settings.
- Model files. The downloaded models are stored in the app's own storage.
- Download log. On Android the app keeps a local record of its downloads (time, host, purpose and size) for
  its network indicator.

On Android the app opts out of cloud backup and device-to-device transfer, so none of this is copied off the
device. On iPhone, notes and model files are excluded from iCloud and device backups. Settings on iPhone are
included in your device backup like any app's settings.

## Permissions

- Microphone, to hear your questions and the wake word.
- Internet and network state, to download the models and to wait for Wi-Fi unless you allow mobile data.
- Audio settings (Android), to route the assistant's voice while the microphone is open.

## Deleting your data

Delete individual notes in the app. Uninstalling the app deletes everything it stored: notes, settings,
models and the download log.

## Children

Offwire does not collect personal information from anyone, including children.

## Changes

If this policy changes, the new version will be posted at https://offwire.app/privacy with a new effective
date.

## Contact

Questions about this policy: privacy@offwire.app
