package com.chrishonson.gemmavoice.notes

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

private const val LOG_TAG = "GemmaVoice"

/**
 * The model's one tool. The library names it save_note from the method and describes it to the model. The engine
 * turns off the library's automatic tool calling and runs the calls itself (see ToolTurn), so this method runs only
 * if that is turned back on.
 */
class NoteTools(private val repository: NotesRepository) : ToolSet {
    @Tool(description = SAVE_NOTE_DESCRIPTION)
    fun saveNote(@ToolParam(description = SAVE_NOTE_TEXT_DESCRIPTION) text: String): String {
        Log.i(LOG_TAG, "save_note called")
        return repository.saveNote(text)
    }
}
