# Store listing

Working material for the Play Store and GitHub Releases pages. The listing text lives in
`fastlane/metadata/android/en-US/` so a Play upload can consume it unchanged.

## Icon

`scripts/render-launcher-icon.py` is the single source for the launcher icon. It writes the adaptive
icon layers (`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_launcher_background.xml`),
the legacy mipmap PNGs, and `docs/store/icon-512.png` for the store.

The icon is the app's own frame: a black ground, an orange elbow turning into a header bar, two rail
blocks in ice and violet, and the talk orb in sunflower. Every shape sits inside the 66 dp safe circle of
the 108 dp adaptive viewport, so round, squircle and square masks all show the whole composition. The
monochrome layer lets Android 13 themed icons tint it. Colors come from `Lcars` in `Theme.kt`.

## Screenshots (phone, 1080 x 2400, dark status bar)

1. Console listening: "COMPUTER", the orb lit, the phase label reading I'm listening.
2. Console speaking: a question and a spoken reply in the transcript log, the orb breathing.
3. Barge-in: the hint "Just start talking to interrupt" visible under the phase label.
4. Library: two collections, one selected, in LCARS blocks.
5. Setup: the model download card with its progress bar.
6. Landscape console: transcript beside the live panel.

Feature graphic (1024 x 500): the elbow and bar from the icon running across the width, title in
Antonio, tagline "Say Computer. Nothing leaves your phone."

## Listing facts

- Category: Productivity. Content rating: Everyone.
- Privacy policy: https://offwire.app/privacy, source in `docs/site/privacy/` (`index.html`, `privacy.md`).
  The URL is also in `fastlane/metadata/android/en-US/privacy_url.txt`. fastlane supply does not upload a
  privacy policy URL, so enter it by hand under Play Console, App content, Privacy policy.
- Permissions: RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, INTERNET and ACCESS_NETWORK_STATE (model download only).
- Distribution: GitHub Releases first, Play Store later (decided 2026-10-31).

## Play Data safety answers

The same answers apply to app.offwire.classic and app.offwire.console. They match the privacy policy.

- Does the app collect or share any of the required user data types? No.
- Is all user data encrypted in transit? Yes. The only network traffic is the HTTPS model download from
  huggingface.co (both apps) and github.com (Console voice), which sends no user data.
- Do you provide a way for users to request that their data is deleted? Not applicable, nothing is collected.
  Notes can be deleted in the app, and uninstalling removes everything the app stored.
- Account creation: none.

Per data type, for reference if Play asks:

| Data type | Collected | Shared | On device only |
|---|---|---|---|
| Audio (voice) | No | No | Processed in memory, never saved |
| Messages / chat | No | No | In memory, gone when the app closes |
| Notes (user content) | No | No | App storage, Keystore-encrypted on Android |
| App settings | No | No | App preferences |
| Personal info, location, contacts, photos, files | No | No | Not accessed |
| App activity, app info and performance, crash logs, diagnostics | No | No | Local download log only |
| Device or other IDs | No | No | Not accessed |

Android backup and device transfer are disabled (`allowBackup="false"`, `data_extraction_rules.xml`
excludes every domain), so nothing reaches Google's backup service either.

## Clear before publishing

1. Name. "Gemma" is Google's model name. Gemma 4 is licensed under Apache 2.0, which grants no trademark
   rights, and a product named "Gemma Voice" may still read as a Google product. Decide on the name or add
   "not affiliated with Google" to the listing.
2. Voice model. The sherpa-onnx Pocket TTS ONNX export's README marks it non-commercial, while the
   archive's LICENSE is CC-BY-4.0 and Kyutai's own license requires consent of the person whose voice is
   cloned. A free app on GitHub is the safe case; anything paid or ad-supported is not until this is
   resolved.
3. Voice likeness. The shipped voice is Caro Davy, a CC0 LibriVox reading (THIRD_PARTY_NOTICES.md), not a
   clone of any actor. Personal voices stay local and are never shipped (card 273). The listing avoids any
   Star Trek trademark. "Starship computer" and "LCARS" are descriptive references; the icon and skin use
   no Paramount artwork.
4. Model license notice: the download screen states Gemma 4 is licensed under Apache 2.0 and that Offwire is
   not affiliated with Google (card 306).
