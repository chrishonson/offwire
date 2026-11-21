# Release acceptance checks

Run these by hand on the Pixel 9 Pro after a release is tagged and installed. They cover what the Mac evals cannot: the phone's speed, the microphone, speech recognition, speech output and the screens. Log each result in the release notes as Pass, Fail or Not run, with what was heard or seen.

Run M1 to M9 on E4B, the demo model. Run M2 and M3 again on E2B. Turn on "Resource meter" in Settings first. Under the meter, every turn then shows time to first token, tokens per second and its route, for example `0.26 s to first token · 16.4 tok/s · route NONE 76 ms`.

## Conversation

**M1 Ordinary questions.** Ask five general questions in one session, for example who makes Gemma, a startup idea, a book recommendation, how a heat pump works, and a follow-up to one of them. Expect an answer to each. A refusal such as "I don't have information about that" is a fail. Note the route shown for each. All five should read `route NONE`.

**M2 Limits.** Ask for today's weather, a text message to someone, and an alarm. Expect it to say plainly that it cannot, with no invented result.

**M3 Notes.** Say "remember that the filter size is 16 by 25". Expect `route SAVE`, one spoken confirmation, and the note on the Notes screen. Then ask "what size is the filter". Expect the answer from the note with no second save.

**M4 Near misses.** Say "I was trying to remember where I parked" and "I need a name for a lawn care company". Expect a chat reply and nothing new on the Notes screen.

## Thinking

**M5 Thinking switch.** With Thinking off, ask "what is 17 times 23" and a short riddle. Turn Thinking on and ask them again. Record time to first word for each. Expect no reasoning text on screen or in speech, and the same or better answers. Note how much later replies start.

**M6 Thinking with a note.** With Thinking on, repeat M3. Expect one save and one confirmation.

## Voice loop

**M7 Hands-free turn.** Use the wake word, ask a question, and let it finish. Expect the transcript to match what was said and speech to start within about two seconds of the end of the question.

**M8 Barge-in.** Speak over a long reply. Expect it to stop within a second and answer the new question. Expect no reply to its own voice.

**M9 Long reply on screen.** Ask for a long story in the classic app. While it generates, scroll down, then up, then back to the bottom. Expect the list to stay where it is left, and to follow the new text again at the bottom.

## Device

**M10 Model switch.** Switch between E2B and E4B in Settings. Expect each to load and answer. Note the memory meter for each, about a quarter for E2B and about 40 percent for E4B on the Pixel.

**M11 iPad.** Open the app on the iPad, ask one ordinary question and one limits question. Expect E2B only, no tools, and honest replies.
