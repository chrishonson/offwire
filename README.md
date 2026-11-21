# Offwire

Offwire is an on-device voice AI assistant built with Kotlin Multiplatform and Jetpack Compose Multiplatform.

- **Model & Runtime**: Runs Gemma 4 E2B locally via Google's LiteRT-LM (`com.google.ai.edge.litertlm`).
- **Voice Activity Detection**: Uses Silero VAD (`silero_vad.onnx` via ONNX Runtime) on Android with clause-level TTS streaming and barge-in.
- **Wake Word Mode**: Optional wake word toggle (`gemma`, plus the STT spellings `jemma` and `gema`) in live voice mode, modeled on the desktop ships-computer project. Each utterance is transcribed on-device with NVIDIA Parakeet TDT 110M int8 (via sherpa-onnx) and matched against the wake words as whole words. Unaddressed speech is never sent to Gemma: it is kept only as "overheard" context (last 20 lines) and shows no bubbles. A bare wake word ("Hey Gemma") answers "Yes?" and sends the next utterance. A 6-second follow-up window after spoken replies, and replies that ask a question, let the next utterance through without the wake word. While the wake word is on, barge-in is only allowed during woken or follow-up turns.
- **Resource Meter**: An off-by-default setting shows a compact CPU and process-memory meter only while live voice is thinking or speaking, plus a two-second tail. Android logs per-turn CPU and PSS peaks under the `GemmaVoice` tag.
- **Notes**: Gemma has one tool, `save_note`. Ask it to note or remember something and it saves an encrypted note on the device; the classic app lists notes on its Notes screen, and Gemma answers from them.
- **Platforms**: Android first (`minSdk 31`), iOS planned later.
- **UX Reference**: [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai).
- **Architecture**: Strict MVI (Model-View-Intent) — see [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md).

## Modules

`:core` is the Kotlin Multiplatform library with the voice loop (VAD, STT, TTS, audio), the LiteRT engine, model download, the notes store, and the MVI stores and contracts, with no Compose dependency. Two thin Compose app modules sit on it, each with its own look:

| Module | Look | applicationId | iOS |
|---|---|---|---|
| `:app-console` | LCARS console, voice only (the product) | `app.offwire.console` | no |
| `:app-classic` | Warm Evening, typed input, orb and bubbles | `app.offwire.classic` | yes, hosts `iosApp/` |

The benchmark activities live in `:app-classic`.

## Build, Run & Test

Install and run the debug build on a connected Android device or emulator:

```bash
./gradlew :app-console:installDebug
./gradlew :app-classic:installDebug
```

Assemble the debug APK:

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run static analysis (Detekt):

```bash
./gradlew detekt
```

Compile the iOS simulator target:

```bash
./gradlew :core:compileKotlinIosSimulatorArm64 :app-classic:compileKotlinIosSimulatorArm64
```

## Console voice

With `AppConfig.clonedVoice` on (`:app-console`), replies are spoken by Kyutai Pocket TTS through sherpa-onnx. The int8 model (about 190 MB) is downloaded with the Gemma model. The default voice is Caro Davy, a CC0 LibriVox reading from [kyutai/tts-voices](https://huggingface.co/kyutai/tts-voices) (see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). The voice is not cloned on the phone: the reference encoder in the sherpa export only reads the first second of a clip, so `core/src/androidMain/assets/voices/caro-davy-conditioning.onnx` is a constant ONNX graph holding the conditioning the Mac computes from the whole clip, produced with `scripts/bake-voice-conditioning.py`. The system voice speaks until the model files are present.

A personal build can speak in another voice. Bake it and put it at `core/src/androidMain/assets/voices/local/conditioning.onnx`, which is gitignored, then build as usual. When that file is bundled the app uses it instead of the default (logcat shows `Pocket TTS voice: voices/local/conditioning.onnx`). Only voices you have the rights to belong in a build you share.

```bash
uv run --with mlx-audio --with onnx python scripts/bake-voice-conditioning.py my-voice.wav \
    core/src/androidMain/assets/voices/local/conditioning.onnx
```

## Native libraries

Speech-to-text uses the sherpa-onnx **static-link onnxruntime** AAR, because its arm64 build has no `libonnxruntime.so` and so does not clash with `onnxruntime-android` (used by Silero VAD). The AAR is not on Maven Central. It is checked in at `core/libs/sherpa-onnx-static-link-onnxruntime-2.0.1.aar` and comes from the [k2-fsa/sherpa-onnx v2.0.1 release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1). The Parakeet TDT 110M (int8) model files live in `core/src/androidMain/assets/parakeet/` and grow the APK by about 95 MB over the previous Moonshine tiny model.

## License

The app code is licensed under the MIT License (see [LICENSE](LICENSE)).

The Gemma 4 model is not part of this repository; it is downloaded at first run from Hugging Face and is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). Offwire is not affiliated with Google. Other third-party components are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and in the in-app Licenses screen, including NVIDIA Parakeet TDT under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/).
