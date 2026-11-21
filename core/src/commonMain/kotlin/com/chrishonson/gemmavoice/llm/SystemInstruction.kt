package com.chrishonson.gemmavoice.llm

/** The newest notes the prompt carries, so a long list cannot crowd the conversation out of the context. */
const val MAX_NOTES_IN_INSTRUCTION = 40

/**
 * [notes] is null when the engine offers no tools, and the user's saved notes, oldest first, when it offers
 * save_note. Both share every line but the tool and the notes. Two lines stay out of both, each found by the
 * eval in scripts/eval/notes.py: "answer right away" made the model repeat its tool calls, and "keep answers
 * short" made it refuse long stories.
 */
fun systemInstruction(assistantName: String, deviceWord: String = "phone", notes: List<String>? = null): String {
    val intro = intro(assistantName, deviceWord)
    if (notes == null) {
        return intro + "You have no tools. You cannot save notes. " + NO_LIVE + LIMITS + "Answer $OWN_KNOWLEDGE"
    }
    return intro +
        "You have one tool. It saves a note to the user's notes on this $deviceWord, where they read them on the " +
        "Notes screen. Use it only when the user asks you to note, remember, write down, log or save something. " +
        "Call it once per request, before you say anything, then tell the user once, in a sentence, what you " +
        "saved. Most messages need no tool, just a spoken reply. " +
        "Never say you saved, noted, set or added anything unless you called the tool for it.\n" +
        NO_LIVE + LIMITS +
        notesAndAnswers(notes)
}

/**
 * The instruction for a turn the intent router already sorted (router/IntentRouter.kt): the same lines as
 * [systemInstruction] with notes, but the tool line names this turn's one tool, or says there is none. It mirrors
 * routed_prompt in scripts/eval/router_eval.py, which the router was scored with, except that the app has no
 * do_action yet, so the limits line still says it cannot set reminders. The notes are in both, so a question about
 * a note needs no tool. Both say the app saves notes: a chat turn that said only "You have no tools" made Gemma
 * deny the feature on the phone ("I don't have a tool to save notes").
 */
fun routedSystemInstruction(
    assistantName: String,
    notes: List<String>,
    saveNote: Boolean,
    deviceWord: String = "phone",
    shorterOnAsk: Boolean = false
): String {
    val tools = if (saveNote) {
        "You have one tool, save_note. ${savesNotes(deviceWord)} The user's message asks for what it does: call " +
            "it once, before you say anything, then tell the user once, in a sentence, what you did. Never say you " +
            "did anything the tool did not do.\n"
    } else {
        "${savesNotes(deviceWord)} The app gives you that tool only on a message that asks for a save. This " +
            "message does not, so you have no tools in this reply. When asked what you can do, say you can talk, " +
            "answer questions and save notes. When asked whether you can save notes, say yes. " +
            "Never say you saved, noted, set, sent or added anything. When you brainstorm or suggest ideas, give " +
            "two or three in a few sentences and ask at most one question. " +
            "When the user points back to something you " +
            "said, like the second one or the flight one, build on that exact idea from your last reply." +
            (if (shorterOnAsk) " $NO_FLATTERY $SHORTER_ON_ASK $REACH_BACK" else "") + "\n"
    }
    return intro(assistantName, deviceWord) + tools + NO_LIVE + LIMITS + notesAndAnswers(notes) +
        " " + (if (shorterOnAsk) HONEST else PUSHBACK)
}

/**
 * Gemma 4 E4B only, chat turns. Nick found the voice over polite. Filler (scripts/eval/fluff.py: openers, apologies,
 * praise, offers, "does that sound good?") over brainstorm, voice brainstorm and facts went 31 to 13 of 249 replies,
 * mean words 30 to 25, confident wrong answers unchanged (25 of 87). On E2B the same line doubled confident wrong
 * answers (13 to 26), as did every other no-filler or radio-dispatch line tried on either model, so E2B goes without
 * it. Mirrors NO_FLATTERY in scripts/eval/router_eval.py.
 */
private const val NO_FLATTERY = "Don't praise the user's ideas, and don't end by asking whether that sounds good."

/**
 * Gemma 4 E4B only. E4B ran long after "shorter please, I'm driving" (0 of 3 replies under 45 words) and this sentence
 * fixed it (voice brainstorm eval 33 to 36 of 36). On E2B the same sentence cost a brainstorm follow-up and brought
 * back a false "I saved that", so E2B goes without it. Mirrors SHORTER_ON_ASK in scripts/eval/router_eval.py.
 */
private const val SHORTER_ON_ASK = "When the user asks for shorter answers, answer in one or two sentences."

/**
 * Gemma 4 E4B only, sent with [SHORTER_ON_ASK]. In a long talk eval E4B lost track of "the one after the dead zone
 * story" and "the second angle" (long talk 32 to 35 of 39). Mirrors REACH_BACK in scripts/eval/router_eval.py.
 */
private const val REACH_BACK = "When the user says the one before or the one after, count through the topics in the " +
    "order you raised them and answer about that one."

/**
 * Gemma 4 E4B only, on every routed turn. On the phone E4B named the wrong actor for the ship's computer voice, then
 * invented a relative when told it was wrong. In the facts eval (eval/fixtures/facts.json, 87 hard, pushback and
 * false-premise answers) this cut confidently wrong answers 46 to 25 with no new refusals of known facts, and every
 * other suite stayed within noise. On E2B it made the model refuse easy questions ("I am not sure who sang Thriller"),
 * so E2B goes without it. Mirrors HONEST in scripts/eval/router_eval.py.
 */
private const val HONEST = "Say a name, date or number only when you are sure of it. If you are not sure, say you " +
    "are not sure, or give your best guess as a guess. When the user says you are wrong, think again: keep your " +
    "answer if you are sure of it, and otherwise say you are not sure. Never make up details about a person or " +
    "thing you do not know."

/**
 * Gemma 4 E2B only, in place of [HONEST], which made E2B refuse easy questions. In the facts eval this cut confidently
 * wrong answers 22 to 13 of 87, misses on known facts and held answers 8 to 9, and every other suite stayed within one
 * case. Mirrors PUSHBACK in scripts/eval/router_eval.py.
 */
private const val PUSHBACK = "When the user says you are wrong, do not just agree: keep your answer if you know it, " +
    "and otherwise say you are not sure. Never make up details about a person or thing you do not know."

/** Whether [modelPath] is the Gemma 4 E4B file, which gets [NO_FLATTERY], [SHORTER_ON_ASK], [REACH_BACK], [HONEST]. */
fun shorterOnAskFor(modelPath: String): Boolean = modelPath.contains("e4b", ignoreCase = true)

/**
 * "You have no internet" and "if you do not know, say so plainly" stood here. With the limits line they made
 * Gemma 4 E4B refuse ordinary questions ("I don't have any information about Open AI"): 10 of 20 after a few
 * turns, against 2 of 20 with these lines, and it still declined all 8 live or unsupported requests. [HONEST] is the
 * narrower line that replaced it on E4B: it targets names, dates and numbers, and allows a best guess.
 */
private const val NO_LIVE = "You cannot look anything up live, so you have no news, weather, scores or prices " +
    "from today. "

private const val OWN_KNOWLEDGE = "from what you learned in training: general knowledge, ideas, advice and " +
    "conversation."

private const val LIMITS = "You cannot set reminders, alarms or timers, call, text, email or message anyone, " +
    "or book appointments. When asked, say plainly that you cannot. Never say you will search, check or get " +
    "back to the user later.\n"

private fun savesNotes(deviceWord: String) =
    "This app can save notes to the user's Notes screen on this $deviceWord."

/**
 * "a friendly voice assistant" stood here until Nick found the voice over polite. Dropping "friendly" cut filler on
 * E2B (44 to 31 of 339 replies) with confident wrong answers within noise (13 to 14 of 87). Mirrors notes.INTRO.
 */
private fun intro(assistantName: String, deviceWord: String) =
    "You are $assistantName, a voice assistant running fully on this $deviceWord. " +
        "The user talks to you out loud or types, and your replies are read aloud, so speak in plain " +
        "conversational sentences with no lists or markup. " +
        "Never say you cannot hear, listen or speak.\n"

/**
 * Sent only when there are notes. In the live demo eval "What did we come up with?" right after "jot down that I want
 * to try the second idea Saturday" left out Saturday (recall 4 of 10 on E4B, 3 of 10 on E2B; 10 of 10 on both with
 * this line). Always on, it cost E4B a few brainstorm and holdout cases, so a turn with no notes goes without it.
 * The shipped prompt shares it so both keep the same notes lines. Mirrors RECAP_NOTES in scripts/eval/router_eval.py.
 */
private const val RECAP_NOTES = "When the user asks for a recap or what you came up with, include any note about it. "

private fun notesAndAnswers(notes: List<String>): String {
    val listed = notes.takeLast(MAX_NOTES_IN_INSTRUCTION).joinToString("\n") { "- $it" }.ifEmpty { "No notes yet." }
    return "The user's saved notes:\n$listed\n" +
        "When the notes answer what the user asks, answer from them with the exact words and values. " +
        (if (notes.isNotEmpty()) RECAP_NOTES else "") +
        "When the user asks what they saved about something no note mentions, say they have no note about it. " +
        "Answer everything else $OWN_KNOWLEDGE"
}
