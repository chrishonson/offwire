package com.chrishonson.gemmavoice.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NotesStoreTest {
    private val testDispatcher = StandardTestDispatcher()
    private var clock = 1_000L
    private val repo = InMemoryNotesRepository(now = { clock++ })

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun addTrimsTextAndKeepsOldestFirst() {
        repo.add("  pharmacy closes at 9 ")
        repo.add("MRI is at Edward")
        assertEquals(listOf("pharmacy closes at 9", "MRI is at Edward"), repo.notes.value.map { it.text })
        assertEquals(listOf(1_000L, 1_001L), repo.notes.value.map { it.savedAt })
    }

    @Test
    fun stateShowsNotesNewestFirstAndFollowsNewSaves() = runTest(testDispatcher) {
        repo.add("first")
        val store = NotesStore(repo)
        assertEquals(listOf("first"), store.state.value.notes.map { it.text })
        repo.add("second")
        advanceUntilIdle()
        assertEquals(listOf("second", "first"), store.state.value.notes.map { it.text })
    }

    @Test
    fun deleteRemovesOnlyThatNote() = runTest(testDispatcher) {
        val keep = repo.add("keep")
        val drop = repo.add("drop")
        val store = NotesStore(repo)
        store.dispatch(NotesIntent.Delete(drop.id))
        advanceUntilIdle()
        assertEquals(listOf(keep), store.state.value.notes)
    }

    @Test
    fun everyChangeReachesTheSubclassWithTheNewestList() {
        val written = mutableListOf<List<String>>()
        val persisting = object : InMemoryNotesRepository() {
            override fun onChanged() {
                written += notes.value.map { it.text }
            }
        }
        val note = persisting.add("a")
        persisting.delete(note.id)
        assertEquals(listOf(listOf("a"), emptyList()), written)
    }
}
