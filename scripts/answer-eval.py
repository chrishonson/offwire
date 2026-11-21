#!/usr/bin/env python3
"""Run checked-answer evaluation against JSON fixtures."""

import os
import subprocess
import sys
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
BEGIN_MARKER = "ANSWER_EVAL_BEGIN"
END_MARKER = "ANSWER_EVAL_END"


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: scripts/answer-eval.py <library.json> <questions.json>")

    library = Path(sys.argv[1]).resolve()
    questions = Path(sys.argv[2]).resolve()
    if not library.is_file() or not questions.is_file():
        sys.exit("library and questions must both be readable JSON files")

    environment = os.environ.copy()
    environment["ANSWER_EVAL_LIBRARY"] = str(library)
    environment["ANSWER_EVAL_QUESTIONS"] = str(questions)
    command = [
        "./gradlew",
        ":core:testDebugUnitTest",
        "--tests",
        "*AnswerEvalTest",
        "-i",
        "--rerun-tasks",
    ]
    result = subprocess.run(
        command,
        cwd=ROOT,
        env=environment,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    output = result.stdout
    start = output.find(BEGIN_MARKER)
    end = output.find(END_MARKER, start)
    if result.returncode != 0:
        sys.stderr.write(output)
        sys.exit(result.returncode)
    if start < 0 or end < 0:
        sys.exit("answer evaluation completed without an output block")
    block = textwrap.dedent(output[start + len(BEGIN_MARKER):end]).strip()
    print(block)


if __name__ == "__main__":
    main()
