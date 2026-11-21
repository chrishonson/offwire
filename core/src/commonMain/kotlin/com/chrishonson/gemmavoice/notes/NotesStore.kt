package com.chrishonson.gemmavoice.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Newest first, the order the Notes screen shows. */
data class NotesState(val notes: List<Note> = emptyList())

sealed interface NotesIntent {
    data class Delete(val id: String) : NotesIntent
}

class NotesStore(private val repository: NotesRepository) : ViewModel() {
    val state: StateFlow<NotesState> = repository.notes
        .map { NotesState(it.reversed()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotesState(repository.notes.value.reversed()))

    fun dispatch(intent: NotesIntent) {
        when (intent) {
            is NotesIntent.Delete -> repository.delete(intent.id)
        }
    }
}
