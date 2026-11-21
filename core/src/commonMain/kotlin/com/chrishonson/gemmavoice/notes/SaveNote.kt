package com.chrishonson.gemmavoice.notes

// The model's one tool, the same on both platforms. The descriptions are what the model reads to decide when to
// call it.
const val SAVE_NOTE_NAME = "save_note"
const val SAVE_NOTE_DESCRIPTION = "Call when the person asks you to note, remember, write down, log or save " +
    "something for later. Saves it to their notes on this phone."
const val SAVE_NOTE_TEXT_DESCRIPTION = "The note, worded close to how the person said it."

/** Runs a save_note call and returns what the model reads back. */
fun NotesRepository.saveNote(text: String): String {
    add(text)
    return "Saved to their notes. Now tell the person once what you saved."
}
