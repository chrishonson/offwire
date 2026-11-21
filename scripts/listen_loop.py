"""Tests the phone's voice loop by ear: the Mac asks a question out loud and listens for the phone to answer.

Put the phone next to the Mac in live mode. For each question the Mac measures the room's quiet level, speaks
the question with the wake word, then records and decides whether the phone replied: a reply is sound that stays
clearly above the quiet level. Built to run live on a projector, so it prints a loudness bar as it listens.

Then it tests interrupting (skip with --no-barge-in): it asks for a long answer, talks over the reply with a new question,
and checks that the phone went quiet and answered the new question instead of talking on.

Usage: python3 scripts/listen_loop.py [--no-barge-in] [--wake Computer] [--device "MacBook Pro Microphone"] [question ...]
"""
import argparse
import array
import math
import re
import statistics
import subprocess
import sys

RATE = 16_000
WINDOW = RATE // 10  # 100 ms of samples
FLOOR_DB = -90.0

LONG_QUESTION = "tell me a long story about a lighthouse keeper and her dog."
INTERRUPTION = "what is two plus two?"

QUESTIONS = [
    "what is the capital of France?",
    "how many legs does a spider have?",
    "what color do you get when you mix blue and yellow?",
]


def dbfs(samples: array.array) -> float:
    """Loudness of 16-bit samples in dB below full scale."""
    if not samples:
        return FLOOR_DB
    rms = math.sqrt(sum(s * s for s in samples) / len(samples))
    return max(FLOOR_DB, 20 * math.log10(rms / 32768)) if rms else FLOOR_DB


def first_sustained(levels: list[float], threshold: float, windows: int) -> int | None:
    """Index of the first run of [windows] levels at or above [threshold], or None."""
    run = 0
    for i, level in enumerate(levels):
        run = run + 1 if level >= threshold else 0
        if run == windows:
            return i - windows + 1
    return None


def judge_barge_in(levels: list[float], threshold: float, hold: int, gap: int) -> tuple[str, float | None]:
    """Judges the sound after an interruption. The phone passes if it is quiet for [gap] windows before it
    answers: talking from the first moment means it never stopped the old reply."""
    start = first_sustained(levels, threshold, hold)
    if start is None:
        return "stopped, no answer", None
    if start < gap:
        return "kept talking", None
    return "stopped and answered", start / 10


def audio_device_index(listing: str, name: str) -> str:
    """Finds [name] among ffmpeg's AVFoundation audio devices and returns its index."""
    audio = listing.split("audio devices:", 1)[-1]
    devices = re.findall(r"\[(\d+)\] (.+)", audio)
    for index, device in devices:
        if device.strip() == name:
            return index
    raise SystemExit(f"No audio input named {name!r}. Inputs: {[device.strip() for _, device in devices]}")


def bar(level: float, quiet: float, threshold: float, width: int = 40) -> str:
    filled = int(max(0.0, min(1.0, (level - quiet) / max(1.0, 2 * (threshold - quiet)))) * width)
    return ("#" * filled).ljust(width)


def record(device: str, seconds: float, on_window=None) -> list[float]:
    """Streams [seconds] of mono audio from the microphone and returns the loudness of each 100 ms window.

    [on_window] gets each window's loudness and may return True to stop early.
    """
    ffmpeg = subprocess.Popen(
        ["ffmpeg", "-loglevel", "error", "-f", "avfoundation", "-i", f":{device}", "-t", str(seconds),
         "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
        stdout=subprocess.PIPE)
    levels = []
    while chunk := ffmpeg.stdout.read(WINDOW * 2):
        samples = array.array("h", chunk[: len(chunk) // 2 * 2])
        levels.append(dbfs(samples))
        if on_window and on_window(levels[-1]):
            ffmpeg.terminate()
            break
    ffmpeg.wait()
    return levels


def measure_quiet(args, device: str) -> tuple[float, float]:
    quiet = statistics.median(record(device, 1.0))
    threshold = quiet + args.margin
    print(f"\nQuiet room: {quiet:.0f} dB. A reply must stay above {threshold:.0f} dB.")
    return quiet, threshold


def say(args, question: str) -> None:
    spoken = f"{args.wake}, {question}"
    print(f"Mac says:  {spoken}")
    subprocess.run(["say", "-v", args.voice, "-r", "160", spoken], check=True)


def meter(quiet: float, threshold: float, stop_after: int | None = None, hold: int = 3):
    """A window callback that draws the loudness bar. With [stop_after], it stops the recording once sound has
    lasted [hold] windows and then [stop_after] more, so the Mac can talk over a reply that is underway."""
    levels: list[float] = []

    def show(level: float) -> bool:
        levels.append(level)
        sys.stdout.write(f"\rListening  |{bar(level, quiet, threshold)}| {level:5.0f} dB")
        sys.stdout.flush()
        start = first_sustained(levels, threshold, hold) if stop_after is not None else None
        return start is not None and len(levels) >= start + hold + stop_after
    return show


def ask(question: str, args, device: str) -> float | None:
    """Asks once. Returns seconds from the end of the question to the reply, or None for silence."""
    quiet, threshold = measure_quiet(args, device)
    say(args, question)
    levels = record(device, args.listen, meter(quiet, threshold))
    start = first_sustained(levels, threshold, args.hold)
    print()
    if start is None:
        print("Verdict:   SILENCE. The phone did not answer out loud.")
        return None
    seconds = start / 10
    print(f"Verdict:   HEARD a reply {seconds:.1f} s after the question.")
    return seconds


def barge_in(args, device: str) -> bool:
    """Asks for a long answer, talks over it, and checks the phone stopped and answered the new question."""
    print("\nBarge-in: the Mac will talk over the phone's reply.")
    quiet, threshold = measure_quiet(args, device)
    say(args, LONG_QUESTION)
    reply = record(device, args.listen, meter(quiet, threshold, stop_after=15, hold=args.hold))
    print()
    if first_sustained(reply, threshold, args.hold) is None:
        print("Verdict:   SILENCE. The phone never started its reply, so there was nothing to interrupt.")
        return False
    say(args, INTERRUPTION)
    after = record(device, args.answer_wait, meter(quiet, threshold))
    print()
    verdict, seconds = judge_barge_in(after, threshold, args.hold, args.gap)
    if seconds is not None:
        print(f"Verdict:   STOPPED its reply and answered the new question {seconds:.1f} s later.")
    elif verdict == "kept talking":
        print("Verdict:   KEPT TALKING. The phone did not stop when interrupted.")
    else:
        print("Verdict:   STOPPED, but did not answer the new question.")
    return seconds is not None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("questions", nargs="*", default=QUESTIONS)
    parser.add_argument("--wake", default="Computer", help="the assistant's name on the phone")
    parser.add_argument("--device", default="MacBook Pro Microphone")
    parser.add_argument("--voice", default="Samantha")
    parser.add_argument("--listen", type=float, default=8.0, help="seconds to listen after each question")
    parser.add_argument("--margin", type=float, default=12.0, help="dB above the quiet room that counts as sound")
    parser.add_argument("--hold", type=int, default=3, help="100 ms windows the sound must last")
    parser.add_argument("--no-barge-in", dest="barge_in", action="store_false", help="skip the barge-in test")
    parser.add_argument("--answer-wait", type=float, default=15.0,
                        help="seconds to wait for the answer after the barge-in interruption")
    parser.add_argument("--gap", type=int, default=3,
                        help="100 ms windows of quiet after an interruption that show the phone stopped")
    args = parser.parse_args()

    listing = subprocess.run(["ffmpeg", "-f", "avfoundation", "-list_devices", "true", "-i", ""],
                             capture_output=True, text=True).stderr
    device = audio_device_index(listing, args.device)
    heard = [ask(q, args, device) for q in args.questions]
    count = sum(h is not None for h in heard)
    print(f"\n{count} of {len(heard)} heard.")
    if args.barge_in:
        print(f"Barge-in: {'PASS' if barge_in(args, device) else 'FAIL'}")


if __name__ == "__main__":
    main()
