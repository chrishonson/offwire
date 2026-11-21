# Card 278: classic app 90s hardware restyle

- `android/`: emulator-5554 (Pixel 3a, API 34). `device-check-*` is the installed app. The emulator has too little free storage to pass the device check, so the other screens were rendered on the same emulator by a throwaway instrumented test that composes each real screen inside `GemmaVoiceTheme(darkTheme = …)` with fake stores.
- `ios/`: `device-check-simulator-*` is the installed app on the iPhone 17 Pro simulator (iOS 26.2). The rest were rendered in that simulator by a throwaway `runSkikoComposeUiTest` harness, the same way, without safe-area insets. No Simulator GUI was available to tap past the device check.
- `console/`: the console app built from `main` (`before-*`) and from this branch (`after-*`), launched on emulator-5554. Below the status bar the only pixel difference is the live free-storage figure.
