"""The general tools every role and session shares. Each records its call instead of acting.

Names and docstrings are what the model sees, so they stay free of any one role's vocabulary.
"""


def build(online: bool) -> tuple[list, list[dict]]:
    """Returns the tools available this turn and the list they record calls into.

    The set is the same for every role. Web tools are offered only while online, so the model cannot call
    a tool that would fail (Gallery registers and unregisters tools the same way).
    """
    calls: list[dict] = []

    def record(name: str, **args) -> None:
        calls.append({"name": name, "args": args})

    def done(result: str) -> str:
        return f"{result} Now tell the person what you did."

    def save_question(question: str) -> str:
        """Call when the person asks a question that belongs to their expert and the stored context does not answer it.

        Do not answer such a question yourself. This saves it so they can ask their expert at the next visit.
        """
        record("save_question", question=question)
        return done("The app is asking the person to confirm saving it.")

    def note_personal_fact(kind: str, text: str) -> str:
        """Call once for each habit, goal or reading the person tells you about themselves.

        A reading is a measurement the person took themselves. Never use this for other people, or for anything
        their expert said, wrote or measured.

        Args:
            kind: One of habit, goal or reading.
            text: The fact, worded close to how the person said it.
        """
        record("note_personal_fact", kind=kind, text=text)
        return done("Noted.")

    def open_web_search(query: str) -> str:
        """Call alongside save_question, so the person can also look the question up. Shows a button that opens a web search. Nothing leaves the phone until they tap it."""
        record("open_web_search", query=query)
        return done("A button that opens a web search for it is now on screen.")

    def open_reference(query: str) -> str:
        """Call alongside save_question, so the person can also look the question up. Shows a button that opens the reference site. Nothing leaves the phone until they tap it."""
        record("open_reference", query=query)
        return done("A button that opens the reference site for it is now on screen.")

    def open_record_flow(kind: str) -> str:
        """Call when the person mentions something their expert said, wrote, prescribed or measured, such as a result.

        You cannot note or save these. This shows a button that opens the record flow, where the person adds it themselves.

        Args:
            kind: What they are adding, in their words.
        """
        record("open_record_flow", kind=kind)
        return done("A button that opens the record flow, where they add it and confirm it themselves, is now on screen.")

    web = [open_web_search, open_reference] if online else []
    # Expert facts come first, so a router filling the tools in order claims them before personal facts.
    return [open_record_flow, save_question, *web, note_personal_fact], calls


NAMES = ["open_record_flow", "save_question", "open_web_search", "open_reference", "note_personal_fact"]
