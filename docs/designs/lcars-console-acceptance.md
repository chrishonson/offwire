# LCARS console acceptance, Pixel 9 Pro, 2026-11-02

Card 176. Walk-through by Nick and the coordinator on the Pixel 9 Pro (serial 4C041FDAP001SH), font scale 1.5, app-console package `com.chrishonson.gemmavoice`. It started on main 708b4c6 at 08:50 and ended on main 4a92960 at 15:35, which carries every fix below. Timings are from logcat (tag GemmaVoice); audibility was measured by recording the Mac microphone while Mac TTS asked the console questions and reading half-second loudness against the room floor, because the phone's own counters say nothing about what comes out of the loudspeaker.

## Results against the definition of done

| Check | Result | Evidence |
|---|---|---|
| Cold start, model present, LISTENING within 5 s, no tap | Fail on 708b4c6 (6 s), fixed by card 216 | with 216: launch 13:46:20.1, mic live (`Call audio on`) 13:46:21.3, model loaded 13:46:24.4; a question asked before the model loaded was answered once it had |
| "Computer, what time is it?" answered aloud, YOU and COMPUTER in the log | Pass | 08:58:57 transcript, reply `It is 8:58 AM`, both entries tagged |
| Overheard speech dim with no tag | Pass | "It worked this time." rendered as a dim quoted line, 10:08 |
| Speaking over a reply interrupts it, readout back to LISTENING | Pass | 10:18:38 `TTS stop() ... interruptLiveTurn, onAudioFrame` |
| Interrupting speech is answered (added by Nick during the walk-through) | Fail on 708b4c6, fixed under this card (PR 56, 6cd0ba8) | 11:19 to 11:20 chain Pixel 8, 7, 6 each interrupted and answered, all tagged YOU |
| STOP pill interrupts | Pass | 11:30:33 `TTS stop() ... handleLiveTap`, readout LISTENING |
| MUTE then UNMUTE, readout, rail and pill agree | Pass | MUTE gives MIC OFF + UNMUTE, UNMUTE gives LISTENING + MUTE |
| Library and Setup by pills only, Back returns, mic live on them | Pass | "Computer, what day is it?" from Library answered `It is Monday, November 2, 2026` 11:22:50 |
| Setup toggles persist across restart | Fail on 708b4c6, fixed by card 217 (PR 58) | wake word off survives force-stop and relaunch on 027df9d |
| CLEAR LOG empties the log, listening continues | Pass | 11:23:04 `resetChat`, next question answered 11:23:46 |
| Model missing: STANDBY with DOWNLOAD pill | Pass | models folder renamed: STANDBY, DOWNLOAD, "Not downloaded yet"; folder restored |
| Landscape: single-row header, transcript left, readout right | Pass | screenshot console-land |
| Largest font: everything fits | Pass at font scale 2.0 | header pills wrap to two rows, nothing truncated |
| Setup survives rotation or font change | Fail, card 218 (backlog) | both bring the app back on Console |
| Pause after the wake word, fragments answered on their own | Fail on 708b4c6, fixed by card 220 (two passes) | "Computer", one second, "what day is it": five of five answered on the branch and three of three on main 4a92960, every question transcribed whole, no voice-message bubbles; the spoken "Yes?" is gone because, once audible, it talked over the question |
| No crash across a barge-in | Fail on 9e77272 (crash on the TTS thread after a stop), fixed in PR 62 | barge-in mid-reply answered, no FATAL, same process alive |
| Replies audible on the loudspeaker | Fail on 708b4c6, fixed by cards 212 and 213 | ten of ten short replies measured on the Mac mic on cb63528 |
| Nick confirms the computer voice | Pass | Nick, 15:45: "Yes, both" |
| Nick confirms readability from arm's length | Pass | Nick, 15:45: "Yes, both" |

## What the audio chase found

- Card 212: neither app declared `MODIFY_AUDIO_SETTINGS`, so call audio mode was refused on every launch while the app logged "Call audio on".
- Card 213: the Pixel 9 Pro's loudspeaker path swallows the first 1.5 to 2 s after an output stream starts, on both the VoIP and media routes, regardless of call mode, mic source or echo effects. One-second replies vanished; long ones lost their opening. The synthesizer now keeps the track playing silence from the moment a reply starts generating until after speech ends, with at most 200 ms of silence ahead of speech.
- Card 220: frames were dropped while the wake word was transcribed and while "Yes?" played, which ate the first words of any question that followed a short pause; empty transcripts were sent to the model as voice messages. The mic now stays open throughout, an endpoint reached during transcription fires afterwards, silence is never a question, and the wake word is acknowledged by its YOU entry and the readout rather than by speech. The same pass fixed a crash: after a barge-in the synthesizer read a track the warmer had already released.

## Cards

Done today: 212, 213 (two passes), 216, 217, 220 (two passes), plus the barge-in fix under this card. PRs 55 to 62. Open: 218 (Setup lost on rotation), 211 (resource meter on typed turns). Card 215 was folded into this card at Nick's direction.
