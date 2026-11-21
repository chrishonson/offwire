# Design: LCARS Console (gemma-voice)

Design session on 2026-10-31, branch `agent/card-165`
Repo: chrishonson/gemma-voice
Status: APPROVED by Nick ("make it so")
Follows: card 165 "bridge computer" (LCARS skin, computer voice, flags), `docs/designs/local-ai-library.md`

## Intent

The phone is a voice-only console. Nobody types. The user says "Computer", asks, and
reads or hears the answer. Nick's words while watching the Pixel 9 Pro: "it looks too
crowded", "get rid of the whole concept of type a message and the keyboard", "keep up
with changes in the main branch, so make a lot of this stuff a configuration".

The original UX reference was PocketPal AI with a Warm Evening large-type theme for older
users with poor eyesight. That audience stays. The large type scale in `Theme.kt` stays.

## Goals, non-goals, anti-goals

Goals
- Fewest controls on screen that still let the user mute, interrupt and read what was said.
- The phone's state (listening, working, speaking, mic off, standby) reads from across a room.
- Every look-and-feel difference from `main` is an `AppConfig` flag so the branch keeps merging.

Non-goals
- No typed input, no keyboard, anywhere.
- No wake-word rename. The wake word is always "Computer".
- No chat history persistence. Conversations are one in-memory log (decision D9).
- No new theme. Palette, type and frame are the ones in `Theme.kt` and `LcarsFrame.kt`.

Anti-goals (a failure even if it works)
- A console that needs a tap before it listens.
- A top bar with icon buttons. Nav is words in pills, like the control-plane board.
- Any phase indicator smaller than the transcript type.

## Constraints

- Strict MVI (`docs/ARCHITECTURE.md`). `ChatStore` owns state. Screens read `ChatState`
  and dispatch `ChatIntent`. No screen holds voice state of its own.
- Kotlin Multiplatform, Compose Multiplatform, Android first. Screens live in `commonMain`.
- `AppConfig` flags drive every difference from `main`. Defaults on this branch are the
  console. `main`'s look is every flag the other way.
- Warm Evening (`Skin.WARM_EVENING`) keeps today's chat screen, orb and bubbles. Nothing in
  this design changes that skin.

## Decisions

Nick decided D1 to D9 on 2026-10-31. D1 to D8 were proposed as a package and accepted as
sent, with the amendment in D6. D9 was a separate question.

- D1 Three screens in one frame: Console (home), Library, Setup. All three sit inside
  `LcarsFrame`. The header's second row (today's `actions` slot) is the only navigation.
  Console shows LIBRARY and SETUP pills. Library and Setup show a CONSOLE pill back. The
  Library icon button goes away.
- D2 Console opens listening. No TALK pill. When the model is ready the store enters live
  mode on its own, with the wake word on. Before the model is ready the body shows the
  model card and a DOWNLOAD pill, and the readout says STANDBY.
- D3 One pill at the bottom of Console, and its job follows the phase. MUTE (ice) while
  listening, STOP (tomato) while thinking or speaking, UNMUTE (orange) while muted.
  Tap-anywhere-to-interrupt and voice barge-in stay. The live panel's Mute, Stop and
  wake-word switch go away under LCARS.
- D4 A readout replaces the orb under LCARS. Phase word in `displaySmall` Antonio caps,
  one hint line, a row of level blocks. The rail's top block takes the phase colour. The
  orb stays for Warm Evening.
- D5 The transcript is a log, not bubbles. Newest at the bottom, `bodyLarge` type, a
  coloured start bar and a YOU or COMPUTER tag per entry. Overheard speech stays, dim and
  italic with no tag, so the user sees the phone heard them but was not addressed.
- D6 Setup holds everything that is not talking: wake word, listen at start, model and
  voice-model status, mic permission status, CLEAR LOG. Voice on/off is gone from every
  screen. Nick had it removed from the chat screen the same day (`AppConfig.speakToggle`,
  commit f424231) because live voice always speaks.
- D7 Landscape keeps the same frame. Title and nav share one header row, transcript on the
  left, readout and the one pill on the right. The rail stays thin. No board-style wide
  rail with labelled tabs, because a 150dp rail would eat the transcript.
- D8 Flags. The branch's existing defaults stay. Three new flags, listed under Flags.
  Readout versus orb and log versus bubbles follow `skin` and need no flag of their own.
- D9 The Library is collections of captured items (photos, voice notes, text notes,
  documents), as `docs/designs/local-ai-library.md` defines it. It is not a list of past
  chats. Conversations stay ephemeral. Saved transcripts per collection are deferred, see
  Deferred.
- D10 The Limits pill sits in the header actions of every console screen.

## Screens

Every sketch is portrait on a Pixel 9 Pro. `[word]` is a pill. The left column `▌` is the
rail. Type sizes are the roles in `Theme.kt`.

### S1 Console

The home screen. The app opens here and stays here.

```
╭────╮ COMPUTER                          title: titleLarge, orange
│elbw│                 [LIBRARY] [SETUP]  nav pills: labelLarge, ice and violet
╰────┴─────────────────────────────────  frame bar
▌ ▐ LISTENING                             readout: displaySmall, phase colour
▌ ▐ Say "Computer" to ask                 hint: bodyLarge, secondary text
▌ ▐ ▮▮▮▮▯▯▯▯▯▯                            level blocks, lit by liveLevel
▌ ▐
▌ ▐ ·the furnace filter looked dirty·     overheard: dim italic, no tag
▌ ▐
▌ ▐ YOU                                   tag: labelMedium, ice
▌ ▐ Computer, how old is a water          bodyLarge
▌ ▐ heater with serial 0309A44?
▌ ▐
▌ ▐ COMPUTER                              tag: labelMedium, sunflower
▌ ▐ That serial decodes to March 2009.
▌ ▐ The heater is about seventeen
▌ ▐ years old.
▌ ▐
▌ ▐ ┌──────────────────────────────┐
▌ ▐ │            MUTE              │      one pill, 72dp, labelLarge
▌ ▐ └──────────────────────────────┘
```

Readout states, from `ChatState`:

| State | Phase word | Hint | Pill | Rail top block |
|---|---|---|---|---|
| `modelStatus` Missing | STANDBY | "Not downloaded yet" | DOWNLOAD | frame |
| `modelStatus` Downloading | STANDBY | "Downloading, 42%" | none | frame |
| `modelStatus` Loading | STANDBY | "Loading, almost there" | none | frame |
| `modelStatus` Error | STANDBY | the error message | RETRY (dispatches `DownloadModel`) | tomato |
| `micDenied` | MIC BLOCKED | "Allow the microphone in Settings" | none | tomato |
| LISTENING, `wakeWordEnabled` | LISTENING | Say "Computer" to ask | MUTE | ice |
| LISTENING, wake word off | LISTENING | none | MUTE | ice |
| LISTENING, `micMuted` | MIC OFF | none | UNMUTE (orange) | tomato |
| THINKING | WORKING | Tap anywhere to interrupt | STOP (tomato) | orange, pulsing alpha as the orb does today |
| SPEAKING | SPEAKING | Start talking to interrupt | STOP (tomato) | sunflower |
| THINKING or SPEAKING with `micMuted` | as above | as above | STOP (tomato) | tomato |
| IDLE after an exit (only when `autoListen` is off) | STANDBY | Tap TALK to start | TALK | frame |

Muted during a turn is near unreachable, because the pill is STOP during a turn and a muted
mic starts no turn. The row exists so the builder does not have to guess: STOP wins, the
rail says the mic is off, and MIC OFF with UNMUTE returns once the turn ends.

Level blocks: ten blocks. The lit count is `liveLevel` scaled to ten while listening or
speaking. Ice while listening, sunflower while speaking, none while thinking.

The transcript is the existing `MessageList` with a log entry style under LCARS: a
`LcarsBar` on the start edge (ice for USER, sunflower for ASSISTANT, line colour for
OVERHEARD), a tag line, the text. No bubble shape, no max width, no voice icon. Streaming
replies render as today, text growing in place.

Reads from `ChatState`: `modelStatus`, `modelName`, `messages`, `livePhase`, `liveLevel`,
`wakeWordEnabled`, `micDenied`, `micMuted`, `isGenerating`, `assistantName` (title only).

Dispatches: `DownloadModel` (DOWNLOAD and RETRY pills), `ToggleMute` (MUTE and UNMUTE),
`LiveTap` (STOP pill, and a tap anywhere on the readout or transcript while thinking or
speaking), `EnterLiveMode` (TALK pill, only when `autoListen` is off).

STOP means "interrupt this turn and go back to LISTENING", which is what `LiveTap` does
today. It is not `Stop`, which only cancels generation and would leave the phase stuck,
and not `ExitLiveMode`, which would kill the session with no pill to restart it. My call.

Not on this screen any more under LCARS: `Send`, `InputChanged`, `TellJoke`, `NewChat`,
`ToggleWakeWord`, `ExitLiveMode`, rename intents.

Auto listen: `App` dispatches `EnterLiveMode` once `modelStatus` becomes Ready when
`appConfig.autoListen` is on. `handleEnterLiveMode` already guards against double entry.
If the mic permission is refused, `micDenied` is set and the readout shows MIC BLOCKED. The
builder decides whether the dispatch lives in `App` as a `LaunchedEffect` on `modelStatus`
or inside `ChatStore` after `handleStart` loads the model. Either keeps MVI: the store
still owns the transition.

Wake word default: `wakeWordEnabled` starts at the persisted value when `SettingsStore`
has one, else `appConfig.wakeWordDefault`. The store takes the default as a constructor
argument the way it takes `voiceSpec`. Same for `autoListen`. A persisted value wins over
the flag, so a user who turns the wake word off finds it off at the next launch. On `main`
the wake-word switch gains persistence after the merge, which is behaviour, not look, and
does not break "main's look is every flag the other way". My call.

Recovery when the session is IDLE and there is no TALK pill (my call):
- CLEAR LOG under `autoListen` dispatches `NewChat` with the live session kept, so the mic
  keeps running and the console comes back to LISTENING with an empty log. `resetChat`
  already has a `keepLive` argument.
- `micDenied`: when the app returns to the foreground with `autoListen` on, the phase IDLE
  and the model Ready, `App` dispatches `EnterLiveMode` again. If the user granted the
  permission in Settings, the console starts listening. If not, `micDenied` is set again
  and the readout stays MIC BLOCKED.
- Any other IDLE under `autoListen` (an engine error, say) retries the same way on the next
  foreground.

The mic and the wake word stay live while the user is on Library or Setup. The store
outlives the screens, the user can ask the computer about the library while looking at it,
and a reply spoken while Setup is open is the ship's computer behaving as it should. Turns
land in the console log as usual. My call.

### S2 Library

Content unchanged from card 132 and the library cards. What changes is the frame row.

```
╭────╮ LIBRARY
│elbw│                   [CONSOLE] [NEW]   CONSOLE in orange, NEW in sunflower
╰────┴─────────────────────────────────
▌ ▐ ▐ BASEMENT WALK-THROUGH                collection cards as today
▌ ▐   6 items · Oct 30
▌ ▐ ▐ MEDICAL RECORDS
▌ ▐   2 items · Oct 28
```

The Back icon button in `CollectionsScreen` becomes the CONSOLE pill. The create action
becomes the NEW pill. Both use the frame's `actions` slot. There is no collection detail
view today. When a later library card adds one, its header row is [LIBRARY] back.

The NEW pill opens an inline voice-driven form panel (card 205). The assistant speaks prompts
for title, address (optional, "skip" accepted), and person ID when the Health template is
selected. Spoken utterances go directly to the form. Template is chosen via pills (Generic,
Inspection, Health). Action pills provide CANCEL, SKIP (on address step), and CREATE (once a title
exists). Skip and cancel also work by voice ("skip", "cancel"). Confirmation reads back the
created collection title and address. No text fields or keyboard input exist on this path.

Reads: `CollectionsState` as today. Dispatches: `CollectionsIntent` as today, plus the
`onBack` callback that `App` already wires.

### S3 Setup

New screen. Everything that is not talking.

```
╭────╮ SETUP
│elbw│                          [CONSOLE]
╰────┴─────────────────────────────────
▌ ▐ ▐ WAKE WORD                  (  ●)    titleMedium, switch at 1.25x as today
▌ ▐   Only answer after "Computer"        bodySmall
▌ ▐ ▐ LISTEN AT START            (  ●)
▌ ▐   Open the console already listening
▌ ▐
▌ ▐ ▐ GEMMA 4 E2B                         model card, as on the console before Ready
▌ ▐   Ready. Voice model ready.
▌ ▐ ▐ MICROPHONE
▌ ▐   Allowed
▌ ▐
▌ ▐ ┌──────────────────────────────┐
▌ ▐ │          CLEAR LOG           │      tomato
▌ ▐ └──────────────────────────────┘
```

Reads from `ChatState`: `wakeWordEnabled`, `modelStatus`, `modelName`, `micDenied`.
LISTEN AT START reads a new `ChatState.autoListen` field, seeded from
`appConfig.autoListen` and persisted through `SettingsStore` next to the assistant name.

Dispatches: `ToggleWakeWord`, `NewChat` (CLEAR LOG), `DownloadModel` (when the model card
shows Missing), and a new `ToggleAutoListen`.

The Pocket TTS voice-model status comes from `modelStatus` today, because the voice model
downloads with Gemma. Showing it as its own line is the builder's call. If `ChatState` does
not expose it separately, one line "Ready" for both is fine.

CLEAR LOG clears without confirmation. The log is ephemeral by D9 and the pill is not
reachable by accident from the console.

### Navigation

`App` already switches on a private `AppScreen` enum. It gains `SETUP`. The nav pills call
the same callbacks `onOpenLibrary` and `onBack` do today, plus `onOpenSetup`. Navigation
stays in `App`, not in `ChatStore`. Android Back returns to Console from either screen,
through Compose Multiplatform's `BackHandler` in `commonMain`. Back on Console does what
the system does today: it leaves the app and the mic stops as it does now. My call.

Pills in the nav row are `SecondaryButton` under LCARS with the colour per screen: ice for
LIBRARY, violet for SETUP, orange for CONSOLE, sunflower for NEW.

### Landscape

`ChatBody` already branches on `wide`. Under LCARS and `wide`:

```
╭────╮ COMPUTER                      [LIBRARY] [SETUP]     one header row
╰────┴────────────────────────────────────────────────
▌ ▐ YOU                               │ LISTENING
▌ ▐ Computer, how old is a water      │ Say "Computer" to ask
▌ ▐ heater with serial 0309A44?       │ ▮▮▯▯▯▯▯▯
▌ ▐                                   │
▌ ▐ COMPUTER                          │
▌ ▐ That serial decodes to March 2009.│ ┌──────────────┐
▌ ▐                                   │ │     MUTE     │
▌ ▐                                   │ └──────────────┘
```

`LcarsFrame` gains a `singleRowHeader` layout when the width exceeds the height: title at
the start, actions at the end, elbow height at its minimum. The right column is 300dp.
Setup and Library in landscape just use the single-row header with their normal body.

## Flags

The visual differences are owned by the application modules, not flags: `app-console` is
LCARS only, while `app-classic` is Warm Evening.

## Older user with poor eyesight

- Every size is in sp through `Theme.kt`, so the phone's font-size setting still scales it.
- Phase word `displaySmall` (34sp), transcript `bodyLarge` (20sp), tags `labelMedium`
  (17sp). Nothing on Console is under 17sp.
- One pill, 72dp tall, full width, labelled with a word. No icons without words.
- Phase colour appears in three places at once: the word, the rail block, the pill. Colour
  is never the only signal.
- Contrast: every pair used here is already in `ThemeContrastTest`. Any new pair (tomato
  rail on black, violet pill text) goes into that test.
- `contentDescription` on the readout reads the phase word and hint together, so TalkBack
  says "Listening. Say Computer to ask."

## Open questions

Left open on purpose. None blocks the first card.

- Q1 Should overheard lines stay in the log for the older-user audience, or hide behind a
  flag? Decided to keep them (D5) and revisit after a session on the Pixel.
- Q2 With the wake word off, should the console show who it heard before it answers, or is
  the YOU entry enough? Builder keeps today's behaviour.
- Q3 Does the pill need a long-press to leave live mode entirely, or is MUTE enough now
  that nothing else runs? The doc assumes MUTE is enough and gives no way to leave live
  mode under `autoListen`.

## Deferred

- O2 Saved transcripts per collection: each console session attaches to the open
  collection, its log persists as a text note, and reopening the collection shows past
  exchanges and feeds them to the model as context. Needs persistence and an "active
  collection" on the console. Nick chose O1 (ephemeral) on 2026-10-31. A backlog card
  records O2 so it is not lost.

## Calls made after the builder check

A reviewer read the doc cold and asked eight questions. None needed Nick. Each answer is
in the section it belongs to and marked "my call". In one list:

- STOP dispatches `LiveTap`, not `Stop` or `ExitLiveMode`.
- CLEAR LOG keeps the live session under `autoListen`. IDLE under `autoListen` retries
  `EnterLiveMode` on the next foreground, which also covers a mic permission granted later.
- Persisted wake-word and listen-at-start values win over the flags, which are seeds.
- Model Error gets a RETRY pill. Downloading and Loading show their progress as the hint.
- Muted during a turn: STOP wins, rail block tomato.
- Mic and wake word stay live on Library and Setup.
- No collection detail view exists. The [LIBRARY] back row waits for the card that adds one.
- Back returns to Console via `BackHandler`. Back on Console leaves the app, as today.

## What is left to the builder

- Exact dp of the readout block, the level block grid and the nav pill padding.
- Whether auto listen is dispatched from `App` or inside `ChatStore`.
- Whether the voice-model status gets its own line on Setup.
- The pulsing animation on the rail block while thinking.
- Test plan: `ChatStoreTest` for `wakeWordDefault`, `autoListen` and `ToggleAutoListen`,
  `ThemeContrastTest` for new colour pairs, a Compose test per screen for the pill state
  table above.

## Cards

One card per screen or change, all in backlog, repo gemma-voice, gates quality and device.

- C1 card 166 console nav: nav pills in the frame row, Library icon removed, `settingsScreen` flag,
  `AppScreen.SETUP`. (S1 header, S2 header)
- C2 card 172 setup screen: S3 with wake word, listen at start, model and mic status, CLEAR LOG,
  `ToggleAutoListen`, `autoListen` persisted. Depends on C1.
- C3 card 167 open listening: `autoListen` and `wakeWordDefault` constructor defaults, persisted through
  `SettingsStore`, TALK pill removed, foreground retry, STANDBY and MIC BLOCKED readout
  states. (S1 D2)
- C4 card 168 one pill: the context pill replaces the live panel controls under LCARS. (S1 D3)
- C5 card 169 phase readout: readout, level blocks and rail colour replace the orb under LCARS.
  (S1 D4)
- C6 card 170 transcript log: log entries replace bubbles under LCARS. (S1 D5)
- C7 card 173 console landscape: single-row header and two-column body. (D7) Depends on C4, C5.
- C8 card 171 saved transcripts: deferred O2, backlog only.
