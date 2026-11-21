#!/bin/zsh
# Runs the evals a release is stamped with (docs/RELEASING.md). Usage: scripts/release-evals.sh v0.2.0
# Models come from GEMMA_DIR (default ~/models/gemma4). Results go to eval/results, tagged with the version.
set -e
VERSION=${1:?usage: scripts/release-evals.sh <version, for example v0.2.0>}
cd "$(dirname "$0")/.."
PY=.venv/bin/python
GEMMA_DIR=${GEMMA_DIR:-$HOME/models/gemma4}
HOLDOUT=eval/fixtures/tool-eval-holdout.json
# The setup that matches the app: the embedding router, then Gemma with one tool or none.
SETUP=embed_mlp

# R1: the router alone on the held-out test half. No Gemma, runs in seconds.
$PY scripts/eval/router_heldout.py --split test --setups $SETUP --json "eval/results/$VERSION-router-heldout-test.json"

for MODEL in gemma-4-E4B-it gemma-4-E2B-it; do
    PATH_TO_MODEL="$GEMMA_DIR/$MODEL.litertlm"
    # R2 and R3: the router and Gemma together, on the main set and the holdout set.
    $PY scripts/eval/router_eval.py --setups $SETUP --model "$PATH_TO_MODEL" --runs 3 --tag "$VERSION"
    $PY scripts/eval/router_eval.py --setups $SETUP --model "$PATH_TO_MODEL" --runs 3 --tag "$VERSION" --fixture $HOLDOUT
    # R4: the prompt with no tools, which every iOS turn and every Android chat turn uses.
    GEMMA_MODEL="$PATH_TO_MODEL" $PY scripts/eval/notes.py --no-tools --runs 3
done

# eval/results is not tracked. The release's own results are, so the tag carries its evidence.
RELEASE_DIR="eval/releases/$VERSION"
mkdir -p "$RELEASE_DIR"
cp eval/results/*"$VERSION"* eval/results/"$(date +%F)"-notes-no-tools*.jsonl "$RELEASE_DIR"/
