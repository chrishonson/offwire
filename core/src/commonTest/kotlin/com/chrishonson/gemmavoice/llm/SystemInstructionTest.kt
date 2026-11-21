package com.chrishonson.gemmavoice.llm

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemInstructionTest {
    @Test
    fun deviceWordDefaultsToPhone() {
        val text = systemInstruction("Computer")
        assertContains(text, "You are Computer,")
        assertContains(text, "running fully on this phone.")
    }

    @Test
    fun iPadWordAppearsWhenAsked() {
        val text = systemInstruction("Computer", "iPad")
        assertContains(text, "running fully on this iPad.")
        assertFalse(text.contains("this phone"))
    }

    @Test
    fun withoutNotesItSaysThereAreNoTools() {
        assertContains(systemInstruction("Computer"), "You have no tools")
    }

    @Test
    fun neitherPromptAsksForShortOrImmediateAnswers() {
        listOf(systemInstruction("Computer"), systemInstruction("Computer", notes = emptyList())).forEach {
            assertFalse(it.contains("short", ignoreCase = true))
            assertFalse(it.contains("right away", ignoreCase = true))
        }
    }

    @Test
    fun withNotesItDescribesTheToolAndListsTheNotes() {
        val text = systemInstruction("Computer", notes = listOf("Pharmacy closes at 9", "MRI is at Edward"))
        assertFalse(text.contains("You have no tools"))
        assertContains(text, "You have one tool. It saves a note")
        assertContains(text, "- Pharmacy closes at 9\n- MRI is at Edward")
    }

    @Test
    fun theToolComesBeforeAnyWordsAndTheConfirmationOnce() {
        assertContains(
            systemInstruction("Computer", notes = emptyList()),
            "Call it once per request, before you say anything, then tell the user once, in a sentence, what you saved."
        )
    }

    @Test
    fun withNoNotesYetItSaysSo() {
        assertContains(systemInstruction("Computer", notes = emptyList()), "No notes yet.")
    }

    @Test
    fun onlyTheNewestNotesFit() {
        val notes = (1..MAX_NOTES_IN_INSTRUCTION + 5).map { "note $it" }
        val text = systemInstruction("Computer", notes = notes)
        assertFalse(text.contains("- note 5\n"))
        assertContains(text, "- note 6\n")
        assertContains(text, "- note ${MAX_NOTES_IN_INSTRUCTION + 5}\n")
    }

    @Test
    fun aRoutedSaveTurnNamesSaveNoteAsItsOneTool() {
        val text = routedSystemInstruction("Computer", listOf("Gate code 2280"), saveNote = true)
        assertContains(text, "You have one tool, save_note. This app can save notes to the user's Notes screen")
        assertContains(text, "The user's message asks for what it does: call it once")
        assertContains(text, "- Gate code 2280")
        assertFalse(text.contains("You have no tools"))
    }

    @Test
    fun aRoutedChatTurnHasNoToolsButKeepsTheNotes() {
        val text = routedSystemInstruction("Computer", emptyList(), saveNote = false)
        assertContains(text, "This app can save notes to the user's Notes screen on this phone.")
        assertContains(text, "so you have no tools in this reply. When asked what you can do, say you can talk, " +
            "answer questions and save notes. When asked whether you can save notes, say yes.")
        assertContains(text, "Never say you saved, noted, set, sent or added")
        assertContains(
            text,
            "When the user points back to something you said, like the second one or the flight one, " +
                "build on that exact idea from your last reply."
        )
        assertContains(text, "No notes yet.")
        assertContains(text, "You cannot set reminders, alarms or timers")
    }

    @Test
    fun routedTurnsShareTheShippedPromptsOtherLines() {
        val shipped = systemInstruction("Computer", notes = listOf("a"))
        val routed = routedSystemInstruction("Computer", listOf("a"), saveNote = false)
        assertEquals(shipped.substringBefore("You have"), routed.substringBefore("This app can save"))
        assertTrue(routed.substringAfter("from today. ").startsWith(shipped.substringAfter("from today. ")))
    }

    @Test
    fun onlyTheE4bChatTurnAsksForShorterAnswersOnRequest() {
        val shorter = "whether that sounds good. When the user asks for shorter answers, " +
            "answer in one or two sentences. "
        assertContains(routedSystemInstruction("Computer", emptyList(), saveNote = false, shorterOnAsk = true), shorter)
        assertFalse(routedSystemInstruction("Computer", emptyList(), saveNote = false).contains("shorter answers"))
        assertContains(
            routedSystemInstruction("Computer", emptyList(), saveNote = false, shorterOnAsk = true),
            "answer in one or two sentences. When the user says the one before or the one after, count through"
        )
        assertFalse(routedSystemInstruction("Computer", emptyList(), saveNote = false).contains("one before"))
        assertFalse(
            routedSystemInstruction("Computer", emptyList(), saveNote = true, shorterOnAsk = true).contains("shorter")
        )
        assertTrue(shorterOnAskFor("/data/models/gemma-4-E4B-it.litertlm"))
        assertFalse(shorterOnAskFor("/data/models/gemma-4-E2B-it.litertlm"))
    }

    @Test
    fun aRecapIncludesTheNotesOnlyWhenThereAreNotes() {
        val recap = "When the user asks for a recap or what you came up with, include any note about it. "
        val notes = listOf("Try the shelf idea Saturday")
        assertContains(routedSystemInstruction("Computer", notes, saveNote = false), recap)
        assertFalse(routedSystemInstruction("Computer", emptyList(), saveNote = false).contains("recap"))
    }

    @Test
    fun e4bAsksForHonestyAboutNamesAndE2bOnlyAboutPushback() {
        val honest = "Say a name, date or number only when you are sure of it."
        listOf(true, false).forEach { save ->
            val e4b = routedSystemInstruction("Computer", listOf("a"), saveNote = save, shorterOnAsk = true)
            assertContains(e4b, honest)
            assertTrue(e4b.endsWith("Never make up details about a person or thing you do not know."))
            val e2b = routedSystemInstruction("Computer", listOf("a"), saveNote = save)
            assertFalse(e2b.contains(honest))
            assertTrue(e2b.endsWith("Never make up details about a person or thing you do not know."))
            assertContains(e2b, "When the user says you are wrong, do not just agree")
            assertFalse(e4b.contains("do not just agree"))
        }
        assertFalse(systemInstruction("Computer", notes = listOf("a")).contains(honest))
    }

    @Test
    fun onlyTheE4bChatTurnSaysToSkipPraiseAndCheckIns() {
        val noFlattery = "Don't praise the user's ideas, and don't end by asking whether that sounds good."
        assertContains(
            routedSystemInstruction("Computer", listOf("a"), saveNote = false, shorterOnAsk = true),
            "build on that exact idea from your last reply. $noFlattery"
        )
        val e4bSave = routedSystemInstruction("Computer", listOf("a"), saveNote = true, shorterOnAsk = true)
        assertFalse(e4bSave.contains("praise"))
        assertFalse(routedSystemInstruction("Computer", listOf("a"), saveNote = false).contains("praise"))
        assertFalse(systemInstruction("Computer", notes = listOf("a")).contains("praise"))
    }

    @Test
    fun theIntroIsNotFriendly() {
        val intro = "You are Computer, a voice assistant running fully on this phone. "
        assertTrue(routedSystemInstruction("Computer", emptyList(), saveNote = false).startsWith(intro))
        assertTrue(systemInstruction("Computer", notes = emptyList()).startsWith(intro))
        assertFalse(routedSystemInstruction("Computer", emptyList(), saveNote = false).contains("friendly"))
    }
}
