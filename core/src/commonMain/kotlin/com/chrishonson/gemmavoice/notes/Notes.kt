package com.chrishonson.gemmavoice.notes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class Note(val id: String, val text: String, val savedAt: Long)

/** Notes the model saves through its save_note tool. Called from the engine's thread and the UI's. */
interface NotesRepository {
    /** Oldest first. */
    val notes: StateFlow<List<Note>>
    fun add(text: String): Note
    fun delete(id: String)
}

open class InMemoryNotesRepository(
    initial: List<Note> = emptyList(),
    private val now: () -> Long = { 0L }
) : NotesRepository {
    private val state = MutableStateFlow(initial)
    override val notes: StateFlow<List<Note>> = state.asStateFlow()

    override fun add(text: String): Note {
        val savedAt = now()
        val note = Note(id = "$savedAt-${state.value.size}", text = text.trim(), savedAt = savedAt)
        state.update { it + note }
        onChanged()
        return note
    }

    override fun delete(id: String) {
        state.update { notes -> notes.filterNot { it.id == id } }
        onChanged()
    }

    /** Lets a subclass persist every change; it reads [notes] itself, so the newest list is the one written. */
    protected open fun onChanged() = Unit
}
