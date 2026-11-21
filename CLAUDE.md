# CLAUDE.md

Read AGENTS.md first.

## Device safety

Run the device gate ONLY against the emulator, with `ANDROID_SERIAL=emulator-5554` exported in the same shell, for example `ANDROID_SERIAL=emulator-5554 python3 scripts/run-gate.py device`. Never run any adb or connected task without that variable. Never touch a physical device unless the card says it must run on the Pixel 9 Pro; then target it by its own serial and keep `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, so the app and its 2.6 GB model are never uninstalled. If `emulator-5554` is not listed by `adb devices`, start it headless with `~/Library/Android/sdk/emulator/emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a -port 5554 -no-window -no-snapshot-save &`, then wait until `adb -s emulator-5554 shell getprop sys.boot_completed` prints `1`. Stop and report only if it does not boot within 5 minutes.
