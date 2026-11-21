# vX.Y.Z

Tagged YYYY-MM-DD on commit `<sha>`. Previous release: `<tag>`.

## What changed

One line per change a user or an eval would notice, with the eval number it moved.

## AI architecture at this tag

### Turn flow

How a spoken or typed turn becomes a reply, per platform.

### Models

| Role | Model | File and size | Runs on |
|---|---|---|---|

### Router

Model, head, thresholds, training set size, and which routes lead to which tools.

### Prompts and tools

The system prompt for each route, quoted from `SystemInstruction.kt`, and each tool's schema.

### Generation settings

Sampler, context size, thinking, backend, library versions.

## Evals

| Code | Eval | Model | Result | Change from last release |
|---|---|---|---|---|

Result files, and anything that did not run.

## Known gaps

What this release does not do or was not measured on.

## Acceptance

Checks from `docs/testing/release-acceptance.md`, run on the tagged build.

| Check | Result | Date | Notes |
|---|---|---|---|
