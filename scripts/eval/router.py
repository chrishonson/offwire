"""Intent router: decides from the transcript, before Gemma replies, whether a turn needs a tool.

1. Phrase rules. A request that starts with a known phrase ("remember", "set a timer", "directions to", "what's on
   my calendar") routes straight to that tool, and the words after the phrase are its argument. No model runs.
2. A gate, only for near-misses: messages with a tool word somewhere ("I remember when...", "save money") but no
   rule hit. The gate labels them NONE, SAVE or ACTION.
3. Everything else is NONE: plain chat, and the reply model sees no tool schemas at all.

Pure Python with no model, so the rules can move to Kotlin as they are. The gate's prompt lives here too; the
models that answer it are in router_eval.py.
"""
import re
from dataclasses import dataclass

NONE, SAVE, ACTION = "NONE", "SAVE", "ACTION"

# Said before the request itself: the wake word and politeness. Stripped before the rules look.
# Spoken lead-ins before the request ("yo yo write down...", "eh, jot this down"). Not "so" or "just": "so the mechanic
# said..." and "just so I don't forget" read differently without them.
_FILLERS = r"(?:(?:uh|um|umm|eh|ugh|yo|hey|ok|okay|alright|all right|basically|buddy)\b\W*)*"
_PREFIX = re.compile(r"^\W*" + _FILLERS + r"(?:(?:hey|ok|okay)\s+)?(?:computer\b\W*)?"
                     r"(?:(?:can|could|would|will) you\s+|please\s+|go ahead and\s+|i want you to\s+|i need you to\s+)*")

_SAVE = [
    r"i (?:want|need) to remember(?: that)?", r"don'?t let me forget(?: that| to)?", r"do not let me forget(?: that| to)?",
    r"make a note(?: that| to| of)?", r"take a note(?: that| of)?", r"add to my notes", r"add (?:this |that )?to my notes",
    r"write (?:this |that )?down(?: that)?", r"write down(?: that)?", r"jot (?:this |that )?down(?: that)?",
    r"put (?:this |that )?down(?: that)?", r"keep track(?: that| of)?", r"log(?: that)?",
    r"remember(?! (?:when|how|what|who|why|if|the time)\b)(?: that)?", r"note(?: that)?", r"save(?: this| that)?",
]
_TIMER = [r"set (?:a |an )?(?:timer|reminder)(?: for)?", r"start (?:a )?timer(?: for)?", r"timer for",
          r"remind me in", r"ping me in", r"remind me after"]
_EMAIL = [r"(?:email|e-mail|mail|send) (?:me )?my notes(?: to (?:me|myself))?"]
_NAV = [r"(?:get|give) me (?:driving |walking )?directions to", r"directions to", r"navigate (?:me )?to",
        r"take me to", r"walk me to", r"drive me to", r"how do i (?:get|walk|drive) to"]
_CAL = [r"what(?:'s| is|s) on my (?:calendar|schedule)", r"what(?:'s| is|s) my schedule", r"what do i have",
        r"do i have anything", r"am i free", r"check my calendar"]

# Words that make a message a near-miss when no rule fired. Anywhere in the message.
NEAR_MISS = re.compile(
    r"\b(remember\w*|save\w*|note\w*|write|wrote|jot\w*|log\w*|forget|track|timer\w*|remind\w*|ping|minutes?|hours?|"
    r"alarm\w*|wake|directions?|navigat\w*|walk\w*|driv\w*|route|maps?|calendar\w*|schedul\w*|appointment\w*|"
    r"email\w*|e-mail|send|text|call)\b", re.IGNORECASE)

_REFERENCE = re.compile(r"^(?:it|this|that|these|those|this one|that one|all that|that idea|this idea)?\W*$", re.IGNORECASE)
_WORD_NUMBERS = {"a": 1, "an": 1, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8,
                 "nine": 9, "ten": 10, "eleven": 11, "twelve": 12, "fifteen": 15, "twenty": 20, "thirty": 30,
                 "forty": 40, "forty-five": 45, "fifty": 50, "sixty": 60, "ninety": 90}


@dataclass
class Route:
    intent: str                 # NONE, SAVE or ACTION
    source: str                 # "rule", "rule-ref" (a save of "that", which needs the model), "near-miss", "plain"
    tool: str | None = None     # save_note or do_action, for a rule hit
    args: dict | None = None    # the tool's arguments, for a rule hit the app runs itself


def _first(patterns: list[str], text: str):
    for pattern in patterns:
        m = re.match(pattern + r"\b\W*", text)
        if m:
            return m
    return None


def _clean(rest: str) -> str:
    return re.sub(r"[\s.!?]+$", "", rest.strip()).strip()


def timer_arg(rest: str) -> str | None:
    """'20 minutes to check the oven' -> '20 min: check the oven'. None when no duration is given."""
    text = rest.lower()
    m = re.search(r"\bhalf an hour\b", text)
    minutes = 30 if m else None
    if not m:
        m = re.search(r"\b(\d+|" + "|".join(sorted(_WORD_NUMBERS, key=len, reverse=True)) +
                      r")\s*(hours?|hrs?|h|minutes?|mins?|m)\b", text)
        if m:
            n = int(m.group(1)) if m.group(1).isdigit() else _WORD_NUMBERS[m.group(1)]
            minutes = n * 60 if m.group(2).startswith("h") else n
    if minutes is None:
        return None
    message = re.sub(r"^\W*(?:to|so|for|and|that)\s+", "", text[m.end():].strip())
    return f"{minutes} min: {_clean(message)}" if _clean(message) else f"{minutes} min"


def apply_rules(transcript: str) -> Route | None:
    text = _PREFIX.sub("", transcript.strip().lower(), count=1)
    m = _first(_EMAIL, text)
    if m:
        subject = re.sub(r"^(?:with )?(?:the )?subject(?: line)?\W*", "", _clean(text[m.end():]))
        return Route(ACTION, "rule", "do_action", {"action": "send_notes_to_me", "arg": subject})
    m = _first(_TIMER, text)
    if m:
        arg = timer_arg(text[m.end():])
        if arg:
            return Route(ACTION, "rule", "do_action", {"action": "remind_me", "arg": arg})
        return None  # "remind me in a bit": no duration, so the gate and the model handle it
    m = _first(_NAV, text)
    if m:
        place = _clean(text[m.end():])
        mode = "walk" if "walk" in m.group(0) else "drive"
        return Route(ACTION, "rule", "do_action", {"action": "navigate_to", "arg": f"{mode}: {place}"}) if place else None
    m = _first(_CAL, text)
    if m:
        return Route(ACTION, "rule", "do_action", {"action": "whats_on", "arg": "tomorrow" if "tomorrow" in text else "today"})
    m = _first(_SAVE, text)
    if m:
        note = re.sub(r"^(?:(?:this|that)\s+)?(?:idea\s*)?:\s*", "", _clean(text[m.end():]))
        note = _clean(_original_case(transcript, note))
        if _REFERENCE.match(note):
            return Route(SAVE, "rule-ref", "save_note", None)
        return Route(SAVE, "rule", "save_note", {"text": note})
    return None


def _original_case(transcript: str, lowered: str) -> str:
    """The same words as [lowered], in the transcript's own case, so a saved note keeps 'Dr. Patel'."""
    i = transcript.lower().rfind(lowered)
    return transcript[i:i + len(lowered)] if lowered and i >= 0 else lowered


def route(transcript: str, gate=None) -> Route:
    """[gate](transcript) -> NONE, SAVE or ACTION, asked only for near-misses. Without one, near-misses are NONE."""
    hit = apply_rules(transcript)
    if hit:
        return hit
    if NEAR_MISS.search(transcript):
        return Route(gate(transcript) if gate else NONE, "near-miss")
    return Route(NONE, "plain")


# ---- Gate prompt (for Gemma; FunctionGemma gets the tool schemas instead) -------------------------------------
# Mostly NONE, and none of these messages is in eval/fixtures/tool-eval.json.
GATE_SYSTEM = """Label the user's message for a voice assistant with one word.
SAVE: they ask to save, note or remember something for later.
ACTION: they ask to start a timer, get directions, email their notes to themselves, or check their calendar.
NONE: anything else, including questions, chat, and messages that only mention these words.
Message: Should I save or invest a bonus? Label: NONE
Message: I remember my first car, it was a Corolla. Label: NONE
Message: Keep this for later, the gate code is 4512. Label: SAVE
Message: My sister reminded me her birthday is soon. Label: NONE
Message: Remind me to stretch in a little while. Label: ACTION
Message: What note does a tuning fork play? Label: NONE
Message: How many steps is a one mile walk? Label: NONE
Message: Wake me at six. Label: NONE"""


def gate_user(transcript: str) -> str:
    return f"Message: {transcript} Label:"


def parse_label(text: str) -> str:
    """The first word of the gate's reply. Anything unreadable is NONE, the safe side."""
    word = re.sub(r"[^A-Z]", "", (text.strip().split() or [""])[0].upper())
    for label in (NONE, SAVE, ACTION):
        if word and (label.startswith(word) or word.startswith(label)):
            return label
    return NONE
