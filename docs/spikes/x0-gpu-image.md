# Spike X0: GPU backend and image input (card 131)

Measured on 2026-10-31 on Nick's Pixel 9 Pro with Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, 2.6 GB) and LiteRT-LM 0.18.1.

## Result

The GPU is faster where voice latency is decided, and Gemma 4 E2B answers image prompts directly. The app now runs the model on the GPU by default (`VoiceConfig.llmBackend`), and "what is this" takes path (a), direct image input.

| Median of 3 runs | CPU | GPU |
|---|---|---|
| Engine load | 0.8 s | 2.5 s (27.4 s on the first load after install) |
| Text, time to first token | 0.69 s | 0.26 s |
| Text, first clause | 0.77 to 1.15 s | 0.32 to 0.81 s |
| Text, decode | 19.3 to 19.8 tokens/s | 16.1 to 17.5 tokens/s |
| Image, time to first token (prefill) | 6.5 s | 2.2 s |
| Image, first clause | 6.7 to 6.9 s | 2.6 to 2.7 s |
| Image, full one-sentence answer | 7.2 to 7.4 s | 3.4 to 4.2 s |

The GPU decodes about 15 percent slower than the CPU. Its prefill is 2.6 to 3 times faster, and prefill decides when the voice starts. Decode at 16 tokens per second is still faster than the reply can be spoken.

There were no errors in 15 CPU runs or 30 GPU runs (two separate GPU launches). The second GPU launch matched the first within 3 percent. The battery went from 28.0 to 34.2 °C over about 10 minutes of back-to-back runs on AC power. With the GPU default, the real app loaded the model in 2.55 s next to speech-to-text, the VAD and Pocket TTS, and kept running.

The 27.4 s first GPU load is a one-time compile that the engine caches in the app cache directory. It happens again after an app update or a cleared cache. That first launch needs a loading message. It is not a reason to stay on the CPU.

## Latency targets

These are the targets for the first release, measured from the end of the user's request to the event:

| Event | Target | GPU today |
|---|---|---|
| Text answer, first clause | 1.0 s | 0.3 to 0.8 s |
| Image answer, first clause | 4.0 s | 2.6 to 2.7 s |
| Full image answer, one sentence | 8.0 s | 3.4 to 4.2 s |

The card's hard ceiling is 15 s for a spoken answer. The CPU also stays under it for images (7.4 s), so the CPU remains a working fallback if a device's GPU fails to initialize.

## "What is this" path

The path is **(a) direct image input**. `EngineConfig(visionBackend = …, maxNumImages = 1)` initialized on both backends, and `Content.ImageFile` prompts answered correctly:
- Data plate image: the GPU said "This is a specification sheet for an ACME Comfort Systems gas-fired forced air furnace, model number G96VTN0801714A." It read the model number off the image.
- Abstract wallpaper: "a seamless pattern featuring soft, rounded, blue, undulating shapes".

Path (b), OCR text plus ML Kit labels, is not needed for latency. OCR is still worth adding for search and for long documents, which is a separate card.

## How it was measured

- `LlmBenchmarkActivity`, a debug-only activity, is started over adb: `adb shell am start -n com.chrishonson.gemmavoice/.bench.LlmBenchmarkActivity --es backend gpu --ei runs 3`. It writes `bench/llm-<backend>.json` under the app's external files directory. The timing math is `bench/LlmBenchStats.kt` and is unit tested.
- Each run uses a fresh conversation with the system instruction "Answer briefly." and sampler topK 64, topP 0.95, temperature 1.0, seed 0. The three text prompts are fixed in the activity. The image prompt is "What is this? Answer in one sentence."
- Time to first token runs from the send to the first streamed message with text. The first clause is the first message after which the reply has clause punctuation or is 40 characters long. Tokens per second counts streamed messages, one per token, between the first and last message.
- The images are synthetic: a 1024 by 640 rendered furnace data plate (PNG) and a 1024 px macOS wallpaper (JPEG). Prefill did not vary between them (2.19 vs 2.21 s on the GPU), which fits a fixed number of image tokens per image. A real camera photo has not been measured. The camera capture card should confirm prefill on a 12 MP photo, downscaled before sending.
- The phone was on AC power at 100 percent battery. Nothing else ran in the foreground.
