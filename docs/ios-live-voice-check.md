# iOS live voice check

Date: 2026-11-04. Device: iPad (9th generation, iPad12,1), iOS 26.6.2. App: `:app-classic` Debug build, installed with `xcodebuild` and `devicectl`. Checked by Nick, speaking at the iPad.

| Check | Result |
|---|---|
| Hands-free turn, ask again without touching the screen | Pass |
| Wake word on: unaddressed speech is a dim quoted line with no reply, the name gets an answer, renaming changes the wake word | Pass |
| Barge-in, including several in a row, and the next question is answered | Pass after fix F2 |
| Landscape side-by-side live layout, both orientations | Pass |
| Mute, Stop, rotation, leaving and returning to the app | Pass |
| Knock, cough and TV noise trigger no replies | Pass |
| No empty reply bubbles, no hang on "Thinking" | Pass |
| CPU and memory meters | Pass after fix F1 |

## Fixes made during the check

- F1: the iOS `ResourceMonitor` returned no values, so both meters stayed empty. It now reads process CPU time (`getrusage`) and the app memory footprint (`task_info`).
- F2: with the wake word on, barge-in was ignored when the assistant's name was in the same utterance as the question ("Computer, tell me a story"). It only worked after a separate "Computer" then a question. The fix is in `ChatStore` and affects Android too, so the LCARS console gets it. `LiveVoiceStoreTest` now expects the interruption.

## Notes

- `:app-console` has no iOS target, so the LCARS skin was not tested on the iPad.
- One `devicectl process launch --console` launch showed a black screen with the process running. Four later console launches (real terminal, background with `nohup`, and the original install, launch, wait, console sequence) all rendered normally, so `--console` is safe to use and the cause was not found. The iPad's state at that moment is the likely cause.
