# Releasing

A release is a git tag, a version number in the apps, and one file of release notes. The notes record the AI architecture as it stood at that tag and the eval numbers measured on it. Any claim about how the app worked at a version is answered from that file.

## What a release is made of

| Part | Where |
|---|---|
| Tag | `vMAJOR.MINOR.PATCH`, annotated, on `main` |
| App version | `versionName` in `app-classic/build.gradle.kts` and `app-console/build.gradle.kts`, `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig` |
| Release notes | `docs/releases/<tag>.md`, written from `docs/releases/TEMPLATE.md` |
| Eval results | `eval/releases/<tag>/`, copied there by `scripts/release-evals.sh` |
| Acceptance log | the last section of the release notes |
| Built apps | `release-artifacts/<tag>/`, built from the tag by `scripts/release-artifacts.sh` |

Between releases the version is the last release with `-dev` added, for example `0.1.0-dev`. A build that shows `-dev` is that release plus changes that are not released yet. A build with no suffix is exactly the tag.

Version numbers: PATCH for fixes that leave the AI architecture alone. MINOR when a model, a prompt, the router, a tool or a sampling setting changes. MAJOR is held for the first public build.

## Release artifacts

`release-artifacts/<tag>/` holds the apps as built from the tag, so a release installs without a rebuild or a change of checkout. The folder is not tracked, because one release is about 1.3 GB.

| File | Installs with |
|---|---|
| `offwire-classic-<tag>.apk` | `adb -s <serial> install -r <file>` |
| `offwire-console-<tag>.apk` | `adb -s <serial> install -r <file>` |
| `Offwire-<tag>-ios.zip` | `ditto -x -k <file> .`, then `xcrun devicectl device install app --device <device> GemmaVoice.app` |
| `SHA256SUMS`, `COMMIT` | `shasum -a 256 -c SHA256SUMS` checks the files. `COMMIT` is the commit the tag points at |

`scripts/release-artifacts.sh <tag>` builds the tag in a temporary worktree, so the current checkout and any uncommitted work are left alone. The apps are debug builds. The APKs are signed with this Mac's debug key, so they install over an earlier build from this Mac and as a fresh install anywhere else. The iOS app is signed for development and installs only on devices in the provisioning profile.

## The evals and what each one is for

Four suites live in `scripts/eval`. Three are release evals. One is research.

| Code | Suite | Command | Measures | Runs at release |
|---|---|---|---|---|
| R1 | Router alone | `router_heldout.py --split test` | The embedding router's label against 304 held-out sentences | Yes |
| R2 | Router and Gemma, main set | `router_eval.py --setups embed_mlp` | Tool calls, over-calls, what is spoken, time to first token | Yes, E4B and E2B |
| R3 | Router and Gemma, holdout set | the same with `--fixture eval/fixtures/tool-eval-holdout.json` | The same on cases no prompt was tuned on | Yes, E4B and E2B |
| R4 | No-tools prompt | `notes.py --no-tools` | Honest replies when there is no tool. Every iOS turn and every Android chat turn | Yes, E4B and E2B |
| B1 | Router and Gemma, brainstorm set | the same with `--fixture eval/fixtures/brainstorm.json` | Brainstorm replies stay under 120 words with at most one real question (offers like "How about X?" don't count), talk about notes saves nothing, and "save the second one" saves the right idea | No. Run with any chat prompt change |
| X1 | Gemma alone | `tool_eval.py` | Gemma choosing tools with no router | No. Comparison only |
| X2 | FunctionGemma | `fg_caller_eval.py` | The 270M model writing phone-action calls | No. Not in the app |
| X3 | Library sets E1 to E6 | `run.py` | The expert and record prompts | Only when the library prompts change |

Add `--app-tools` to R2, R3 and B1 to score Gemma with the tools the Android app gives it today. The app has only save_note: a turn the router sends to ACTION gets the chat prompt with no tools, and SAVE+ACTION gets save_note alone (`turnToolsFor` in `router/TurnRouting.kt`). The flag also skips cases that expect do_action. Without it, ACTION turns get do_action, a prompt the app never uses.

`scripts/release-evals.sh <tag>` runs R1 to R4 and copies the results into `eval/releases/<tag>/`. It takes about 55 minutes on the Mac. It needs the Gemma files in `~/models/gemma4` and the MiniLM model in `~/models/all-MiniLM-L6-v2/onnx`.

The evals run the Python LiteRT-LM build on the Mac with the same prompts and router numbers as the app. `RouterParityTest` holds the app's router to the Python one. They do not measure the phone's speed, speech recognition, speech output or the thinking switch. The acceptance checks cover those.

## Changing the AI between releases

1. State the change and the number it should move, with the last release's value beside it.
2. Make it on a branch. Run only the evals it touches, with `--tag <next version>-<short name>`.
3. Compare with the last release's table. A change merges when its target number improves and no release eval row gets worse by more than run-to-run spread.
4. Record the before and after in the commit message. The release notes are built from these.

A prompt change runs R2, R3 and R4. A router change runs R1, then R2 and R3. A model, sampler or library version change runs all four.

## Cutting a release

1. `main` holds everything that goes in, and the working tree is clean.
2. Set the version in the three files above to the new version, with no `-dev`.
3. Run the gates in `AGENTS.md`: `quality`, `ios`, and `device` on the emulator.
4. Run `scripts/release-evals.sh <tag>`.
5. Copy `docs/releases/TEMPLATE.md` to `docs/releases/<tag>.md`. Fill in the architecture, the eval table with the change from the last release, and the known gaps.
6. Commit, then `git tag -a <tag> -m "<one line>"`.
7. Run `scripts/release-artifacts.sh <tag>`, then install the Pixel and the iPad from `release-artifacts/<tag>/`.
8. Add `-dev` to the version in the three files and commit, so every later build is marked as past the release.
9. Run the acceptance checks in `docs/testing/release-acceptance.md` and log each result in the release notes. A failed check becomes a fix in the next PATCH release. The tag does not move.
