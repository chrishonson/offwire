# iOS App Store submission

Prepared 2026-11-25 for the first App Store review of the classic app (`app.offwire.classic`). Copy the fields
below into App Store Connect. Nothing here has been submitted.

## Before you upload

| Item | State |
|---|---|
| Apple Developer Program | Team `P6PDV54GBR` is set in the project. Check the membership is still active at developer.apple.com/account before anything else. |
| Version | `main` carries `MARKETING_VERSION=0.4.1-dev` in `iosApp/Configuration/Config.xcconfig`, as it does between releases (`docs/RELEASING.md`). App Store Connect accepts only up to three dot-separated integers and rejects a suffix, so the upload build needs a plain `0.4.1`, matching the Android 0.4.1 bundle. Set it for the archive, and do not upload a `-dev` build. Build is `1`. Raise the build number on every upload. |
| Privacy manifest | `iosApp/iosApp/PrivacyInfo.xcprivacy`: no tracking, no data collected, reasons for UserDefaults, file size checks, free space and monotonic time. |
| Export compliance | `ITSAppUsesNonExemptEncryption = NO`: the only network use is the HTTPS model download through `NSURLSession`. |
| Memory entitlement | `com.apple.developer.kernel.increased-memory-limit` is in the entitlements. Automatic signing adds it to the App ID. |
| Wi-Fi only download | Fixed: iOS now uses `IosNetworkMonitor`, so the 2.6 GB download waits for Wi-Fi unless cellular is allowed in Settings. Before this, iOS assumed every network was Wi-Fi. |
| Device check copy | Done. iOS uses `IOS_DEVICE_CHECK_CONFIG`: the first screen says "Tested on an iPad (9th generation)", calls the iPhone a device, and sets the RAM floor at 2.8 GB, so a 3 GB device passes. Android keeps the Pixel 9 Pro wording. |
| App icon | `AppIcon.appiconset/app-icon-1024.png` present. |
| Screenshots | Needed: 6.9-inch iPhone (1320 x 2868), and 13-inch iPad (2064 x 2752) because the app supports iPad. |

## Minimum device

- Default model: Gemma 4 E2B, a 2,588,147,712 byte download (about 2.6 GB). E4B is only offered with 6 GB RAM or more.
- E2B passed the full live voice check on an iPad (9th generation): A13, 3 GB RAM, iOS 26.6.2 (`docs/ios-live-voice-check.md`).
- E4B was killed while loading on that 3 GB iPad, which is why it is gated.
- Recommended minimum: iPhone 11 or iPhone SE (2nd generation), meaning A13 and at least 3 GB, the tested floor.
  Setting `IPHONEOS_DEPLOYMENT_TARGET = 26.0` enforces exactly that, because iOS 26 drops only the A12 iPhone XS,
  XS Max and XR. The project is at 18.0 today, which also lets those A12 phones install it untested.
- Free storage: the device check asks for the download plus 1 GB of headroom.
- An iPhone 17e has run debug builds. No iPhone near the 3 GB floor has. Run it on the oldest iPhone you can borrow before submitting.

## App Store Connect record

- Name: Offwire
- Bundle ID: app.offwire.classic
- SKU: offwire-classic
- Primary language: English (U.S.)
- Category: Productivity. Secondary: Utilities.
- Price: Free, no in-app purchases.
- Privacy policy URL: https://offwire.app/privacy
- Support URL: https://github.com/chrishonson/offwire/issues
- Marketing URL: https://offwire.app/
- Copyright: 2026 Nick Christensen

## Listing text

Subtitle (30 max):

    Offline AI voice assistant

Promotional text (170 max):

    Talk to Gemma 4 on your phone with no connection, no account and nothing sent anywhere. Download the model once on Wi-Fi, then use it in airplane mode.

Description:

    Offwire is a voice assistant that runs entirely on your iPhone or iPad. Speech recognition, the language model and the voice all run on the device, so it works in airplane mode, in a basement or on a plane.

    Talk hands-free
    Ask a question, hear the answer, and ask again without touching the screen. Interrupt it mid-answer and it stops to listen.

    A wake word you can rename
    Turn on the wake word and Offwire answers only when you say its name. It starts as "Computer". Change it to anything you like.

    Private by design
    There is no account, no analytics and no ads. Your voice, your conversations and your notes never leave the device.

    Gemma 4 on device
    Offwire runs Google's open Gemma 4 model. The first launch shows the download size, checks your free storage, and downloads the model once, on Wi-Fi by default. After that, no connection is needed.

    Notes
    Ask Offwire to remember something and it keeps a note on your device.

    Open source
    Offwire is free and open source under the Apache 2.0 license.

    Requirements: about 2.6 GB of free storage for the model, plus some headroom. Works best on newer devices.

Keywords (100 max, comma separated, no spaces):

    offline,AI,assistant,voice,private,on-device,LLM,Gemma,chatbot,airplane,local,no account,notes

## App Privacy (nutrition label)

- Data collection: "No, we do not collect data from this app."
- That gives the label "Data Not Collected". It matches the policy at offwire.app/privacy and the privacy manifest.
- Microphone audio is processed on the device and never sent, so it is not "collected" in Apple's sense.

## Age rating

Answer the questionnaire honestly. The app ships no mature content, but the model generates free text. If the form
asks about AI or user-generated content, answer yes. I could not check the current form's wording, so read each
question rather than copying answers.

## Content rights

"Does your app contain, show, or access third-party content?" Yes. It downloads Google's Gemma 4 model from
Hugging Face and uses sherpa-onnx, ONNX Runtime and Silero VAD. Licenses are listed in the app's Licenses sheet and
`THIRD_PARTY_NOTICES.md`.

## Notes for App Review

    Offwire is a private, single-user voice assistant. Everything runs on the device: speech recognition, the Gemma 4 language model (Google, open weights) and speech output.

    First launch: a device check shows the model download size (about 2.6 GB) and free storage. Tap Continue, read the limits sheet, then tap Download in the chat. The download is one time, over HTTPS from huggingface.co, and uses Wi-Fi by default. Please test on Wi-Fi; on a fast connection it takes a few minutes. After the download the app works fully in airplane mode.

    The model file is data loaded by the LiteRT-LM runtime that ships inside the app. No executable code is downloaded (guideline 2.5.2).

    No account or sign-in is needed. There is no analytics, advertising or tracking, and no data leaves the device. Conversations are not shared with other people or posted anywhere, so there are no other users to report or block. Questions: privacy@offwire.app.

    To try it: allow the microphone, then ask "What can you do?" Turn on the wake word in Settings and say "Computer" before a question.

## Upload and submit (needs your Mac and your account)

1. Set `MARKETING_VERSION=0.4.1` for this build, with no `-dev`. In Xcode, select `Any iOS Device`, then Product, Archive.
2. In Organizer, Distribute App, App Store Connect, Upload.
3. In App Store Connect, create the app record with the fields above, attach the build, add screenshots, and fill
   App Privacy, age rating and content rights.
4. Submit for review only once you have decided to.
