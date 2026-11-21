package com.chrishonson.gemmavoice.limits

const val LIMITS_HEADING = "Limits"
const val LIMITS_MODEL_DESCRIPTION = "A small AI model running on this phone, with no internet."
const val LIMITS_FAILURE_MODES =
    "It can mishear what you say and state things that are not true."
const val LIMITS_ACCURACY_MEASURES = "Accuracy measures in this build"
const val LIMITS_NO_MEASURES = "None yet."
const val LIMITS_FINAL_LINE = "Never guaranteed. Verify with a professional."

fun limitsSheetLines(safeguards: List<Safeguard> = emptyList()): List<String> = buildList {
    add(LIMITS_HEADING)
    add(LIMITS_MODEL_DESCRIPTION)
    add(LIMITS_FAILURE_MODES)
    add(LIMITS_ACCURACY_MEASURES)
    if (safeguards.isEmpty()) {
        add(LIMITS_NO_MEASURES)
    } else {
        safeguards.forEach { add(it.description) }
    }
    add(LIMITS_FINAL_LINE)
}
