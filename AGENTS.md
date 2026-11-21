# AGENTS.md

Guidance for coding agents working in this repository.

## Definition of done

A change is done only when:
1. Merged to main.
2. The `quality` gate and the `device` gate pass, and the `ios` gate passes for any change under `core/src/iosMain`, `core/src/commonMain`, `app-classic/src/iosMain`, `app-classic/src/commonMain` or `iosApp/`.

### Verification gates

| Gate | Description | Command |
|---|---|---|
| `quality` | Detekt, assemble debug, unit tests, coverage check | `python3 scripts/run-gate.py quality` |
| `device` | Instrumented tests on Android emulator | `ANDROID_SERIAL=emulator-5554 python3 scripts/run-gate.py device` |
| `ios` | Kotlin iOS compile and tests, Xcode simulator build (Mac with Xcode only) | `python3 scripts/run-gate.py ios` |

### Device safety

Run the device gate ONLY against the emulator, with `ANDROID_SERIAL=emulator-5554` exported in the same shell, for example `ANDROID_SERIAL=emulator-5554 python3 scripts/run-gate.py device`. Never run any adb or connected task without that variable. Never touch a physical device unless the card says it must run on the Pixel 9 Pro; then target it by its own serial and keep `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, so the app and its 2.6 GB model are never uninstalled. If `emulator-5554` is not listed by `adb devices`, start it headless with `~/Library/Android/sdk/emulator/emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a -port 5554 -no-window -no-snapshot-save &`, then wait until `adb -s emulator-5554 shell getprop sys.boot_completed` prints `1`. Stop and report only if it does not boot within 5 minutes.

### Benchmarks

The LLM runs on the GPU by default (`VoiceConfig.llmBackend`). To measure it on a phone, use the debug-only `LlmBenchmarkActivity`: `adb shell am start -n app.offwire.classic/com.chrishonson.gemmavoice.bench.LlmBenchmarkActivity --es backend gpu --ei runs 3`, with the device's serial pinned. It writes `bench/llm-<backend>.json` under the app's external files directory. The benchmark activities live in `app-classic`. Results and method: `docs/spikes/x0-gpu-image.md`.
