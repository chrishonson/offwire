# Saved transcripts per collection

Card 171, option O2 from `lcars-console.md`. Drafted 2026-11-02, approved by Nick the same evening. Three
decisions, each with the alternative it beat.

Status: implemented.

## D1 A session attaches to the open collection

The console session belongs to whichever collection is open in the Library. The console
header names it. With no collection open the session is ephemeral, as today (O1). Opening
another collection swaps the log for that collection's transcript.

Beat: a chooser in Setup. It adds a screen and a state the user has to remember; the open
collection is already the thing they are working on.

## D2 The last six exchanges go into the prompt

Each exchange is one question and its answer. A library question gets the last six, oldest
first, between the system prompt and the retrieved items, inside the fresh conversation that
rule C13 of `local-ai-library.md` requires. Older exchanges stay in the transcript but not in
the prompt. Free conversation outside a collection is unchanged.

Beat: the whole transcript. A 2B model on a phone loses the question under a long preamble,
and six covers "what did you say about this yesterday".

## D3 CLEAR LOG clears the screen, not the transcript

CLEAR LOG empties the readout and the model's working memory as today. The collection's
transcript keeps every exchange. Forgetting is done in the Library by deleting the
transcript item, which already exists for any item.

Beat: CLEAR LOG deleting the transcript. A persisted log that one tap destroys is not
persisted.

## Storage

One item per collection, kind `TRANSCRIPT`, held through `LibraryRepository` like a text
note. Append-only: each exchange carries a timestamp and the two texts. Shown in the Library
as a dated item so it can be opened or deleted.

## Done when

- A session on an open collection survives a restart and reappears in the console log.
- A library question on that collection receives the last six exchanges.
- CLEAR LOG leaves the transcript intact.
- Quality and device gates green, checked on the Pixel 9 Pro.
