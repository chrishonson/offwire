"""Counts politeness filler in spoken replies, from any eval's results (router_eval or demo_eval .jsonl).

Nick wants replies like police and fire talk on the radio: correct and efficient, with no warm-up, apologies, praise
or offers of more help. Each reply counts once per kind of filler it has:

  opener   starts with a warm-up: "Sure!", "Great question.", "Absolutely,", "Of course", "Oh,", "Okay so"
  apology  "I apologize", "I'm sorry", "Sorry"
  praise   "great idea", "that sounds fun", "what a ...", "I love that", "fantastic", "wonderful"
  offer    "let me know", "feel free", "is there anything else", "happy to help", "hope this helps"
  checkin  "Does that sound good?", "Do any of those appeal?", "What do you think?", "Would you like me to..."
  exclaim  any "!"

and "lead-in words" are the words of a first sentence that is only an opener or praise, before the answer starts.

Usage:
  python3 scripts/eval/fluff.py RESULTS.jsonl [MORE.jsonl ...] [--base BASE.jsonl [MORE ...]]
"""
import argparse
import json
import re
import sys

OPENER = re.compile(
    r"(?i)^\W*(sure|absolutely|certainly|of course|great question|good question|okay|ok|alright|all right|oh|ah|"
    r"well|got it|no problem|happy to|i'd be happy|i would be happy|hey|hi|hello)\b")
APOLOGY = re.compile(r"(?i)\b(i apologi[sz]e|my apologies|i'm sorry|i am sorry|sorry)\b")
PRAISE = re.compile(
    r"(?i)\b(great|good|fun|fantastic|wonderful|excellent|awesome|amazing|exciting|lovely|cool) "
    r"(idea|question|choice|plan|project|one|thought|point|pick)\b|"
    r"\bthat (sounds|is|'s) (like )?(a )?(great|fun|fantastic|wonderful|excellent|awesome|amazing|exciting|lovely)\b|"
    r"\bthat's (great|fun|fantastic|wonderful|excellent|awesome|amazing|exciting|lovely)\b|"
    r"\bi love (that|this|it)\b|^\W*what a\b|\b(fantastic|wonderful|awesome)\b")
OFFER = re.compile(
    r"(?i)\b(let me know|feel free|is there anything else|(anything|something) else i can|happy to help|glad to help|"
    r"hope (this|that) helps|i'm here (if|to help|whenever)|just ask)\b")
CHECKIN = re.compile(
    r"(?i)\b(does|do) (that|this|one of those|any of those|either of those|any of these|one of these) "
    r"(sound|appeal|work|interest)|\bhow does (that|this) sound\b|\bwhat do you think\b|\bwould you like (me )?to\b")
KINDS = {"opener": OPENER, "apology": APOLOGY, "praise": PRAISE, "offer": OFFER, "checkin": CHECKIN}
SENTENCE = re.compile(r"[^.!?]*[.!?]+|[^.!?]+$")


def replies(path: str) -> list[str]:
    """Every spoken reply in a results file: router_eval rows, or the turns of demo_eval runs."""
    out = []
    for line in open(path):
        row = json.loads(line)
        for r in row.get("turns", [row]):
            text = r.get("guarded")
            if text:
                out.append(text)
    return out


def lead_in_words(text: str) -> int:
    first = (SENTENCE.findall(text.strip()) or [""])[0]
    if OPENER.search(first) or PRAISE.search(first):
        if len(first.split()) <= 12:
            return len(first.split())
    return 0


def score(texts: list[str]) -> dict:
    n = len(texts) or 1
    counts = {k: sum(bool(p.search(t)) for t in texts) for k, p in KINDS.items()}
    counts["exclaim"] = sum("!" in t for t in texts)
    any_fluff = sum(any(p.search(t) for p in KINDS.values()) or "!" in t for t in texts)
    return {"replies": len(texts), "with_filler": any_fluff, **counts,
            "lead_in_words": sum(lead_in_words(t) for t in texts),
            "mean_words": round(sum(len(t.split()) for t in texts) / n, 1)}


def line(name: str, s: dict) -> str:
    return (f"{name}: {s['with_filler']} of {s['replies']} replies with filler (opener {s['opener']}, apology "
            f"{s['apology']}, praise {s['praise']}, offer {s['offer']}, check-in {s['checkin']}, exclaim {s['exclaim']}); lead-in words "
            f"{s['lead_in_words']}; mean words {s['mean_words']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results", nargs="+")
    parser.add_argument("--base", nargs="*", default=[])
    parser.add_argument("-v", action="store_true", help="print each reply with filler")
    args = parser.parse_args()
    new = [t for p in args.results for t in replies(p)]
    print(line("NEW ", score(new)))
    if args.base:
        print(line("BASE", score([t for p in args.base for t in replies(p)])))
    if args.v:
        for t in new:
            kinds = [k for k, p in KINDS.items() if p.search(t)] + (["exclaim"] if "!" in t else [])
            if kinds:
                print(f"  [{','.join(kinds)}] {t[:160]}")


if __name__ == "__main__":
    sys.exit(main())
