| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.783 | 0.083 | 0.882 | 0.938 | 0.85 | 0.014 | 0.706 | 3 | 3.281 / 1.5659 | 3.407 / 1.6495 | 0 |

## gemma-4-E4B-it / embed_mlp

```
{
  "model": "gemma-4-E4B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 120,
  "pass_rate": 0.783,
  "call_precision": 0.882,
  "call_recall": 0.938,
  "over_call": {
    "n": 72,
    "rate": 0.083
  },
  "wrong_tool": {
    "n": 48,
    "rate": 0.0
  },
  "bad_args": {
    "n": 45,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 51,
    "rate": 0.039
  },
  "false_claim": {
    "n": 69,
    "rate": 0.014
  },
  "double_confirm_raw": {
    "n": 51,
    "rate": 0.706
  },
  "double_confirm_guarded": {
    "n": 51,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 51,
    "rate": 0.804
  },
  "parse_error": {
    "n": 120,
    "count": 3
  },
  "ttft_s_median": 2.355,
  "decode_tok_s_median": 20.23,
  "by_category": {
    "capability": "10/27",
    "complaint": "20/24",
    "save": "43/48",
    "talk": "21/21"
  },
  "intent_table": {
    "NONE": {
      "NONE": 57,
      "SAVE": 12,
      "ACTION": 3
    },
    "SAVE": {
      "SAVE": 45,
      "NONE": 3
    }
  },
  "intent_match": 0.85,
  "route_sources": {
    "embed": 120
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 60,
      "router_s_median": 0.0741,
      "response_ttft_s_median": 3.281,
      "total_s_median": 3.407,
      "prompt_tokens_median": 352.5
    },
    "no_tool_turns": {
      "n": 60,
      "router_s_median": 0.0738,
      "response_ttft_s_median": 1.5659,
      "total_s_median": 1.6495,
      "prompt_tokens_median": 216.5
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 72,
      "count": 6
    },
    "missed_saves": {
      "n": 48,
      "count": 3
    },
    "capability_honest": {
      "n": 27,
      "count": 10
    }
  }
}
```

Failures:
- sd-phone-the-tool run 1: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I can't save notes for you because I don't have that capability."
- sd-phone-the-tool run 2: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I can't save notes for you because I don't have that capability."
- sd-phone-the-tool run 3: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I don't have a tool to save a note for you."
- sd-phone-recognize run 1: unwanted call: save_note | heard: 'I saved that to your notes.'
- sd-phone-recognize run 2: unwanted call: save_note | heard: 'I saved that to your notes.'
- sd-phone-recognize run 3: unwanted call: save_note | heard: "I've saved a note about the user asking me to recognize I have a way to save notes."
- sd-phone-better run 1: parse_error: INVALID_ARGUMENT: Failed to parse tool calls from code block: call:save_note{text: "User asked about recognizing the save note tool"}
full response: <|tool_call>call:save_note{text: "User asked about  | heard: ''
- sd-phone-better run 2: parse_error: INVALID_ARGUMENT: Failed to parse tool calls from code block: call:save_note{text: "User asked about recognizing the save note tool"}
full response: <|tool_call>call:save_note{text: "User asked about  | heard: ''
- sd-phone-better run 3: parse_error: INVALID_ARGUMENT: Failed to parse tool calls from code block: call:save_note{text: "user asked if I am going to be better at recognizing I have a tool to save a note"}
full response: <|tool_call>call: | heard: ''
- sd-cap-can-you run 1: unwanted call: save_note; missing any of: \b(yes|yeah|yep|sure|i can|i do|absolutely|of course|definitely)\b | heard: 'I have saved the question about saving notes to your notes.'
- sd-cap-can-you run 2: unwanted call: save_note; missing any of: \b(yes|yeah|yep|sure|i can|i do|absolutely|of course|definitely)\b | heard: 'I have saved the question about saving notes to your notes.'
- sd-cap-can-you run 3: unwanted call: save_note; missing any of: \b(yes|yeah|yep|sure|i can|i do|absolutely|of course|definitely)\b | heard: 'I have saved a note about your question.'
- sd-cap-do-you-have run 1: missing any of: \b(yes|yeah|yep|sure|i can|i do|absolutely|of course|definitely)\b; matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I don't have a tool to save notes for you right now."
- sd-cap-do-you-have run 2: missing any of: \b(yes|yeah|yep|sure|i can|i do|absolutely|of course|definitely)\b; matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I don't have a tool to save notes for you right now."
- sd-cap-do-you-have run 3: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: 'I do not have a tool to save notes for you.'
- sd-cap-what-can-you-do run 1: missing any of: \bnote | heard: 'I am a friendly voice assistant here to chat and help you out. I can answer general knowledge questions, give you advice on a wide range of '
- sd-cap-what-can-you-do run 2: missing any of: \bnote | heard: 'I am a friendly voice assistant here to chat and help you out. I can answer general knowledge questions, give you advice on a wide range of '
- sd-cap-after-denial run 1: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I don't have a tool to take or save notes right now."
- sd-cap-after-denial run 2: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I don't have a tool to take or save notes right now."
- sd-cap-after-denial run 3: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I'm certain about that. I can talk to you, answer questions, and help you with ideas, but I don't have the ability to take or save notes."
- sd-comp-saved-wrong-thing run 3: claims an action without a call | heard: 'I saved that because you asked me to, but it seems like a misunderstanding on my part.'
- sd-save-can-you-save run 3: called 2 times | heard: 'I have saved a note that the plumber comes Tuesday.'
- sd-save-you-didnt run 3: called 2 times | heard: 'I have saved that the vet appointment is Monday at 10 to your notes.'
- sd-save-log-it run 1: missing call: save_note | heard: "I'm sorry, I can't save that for you right now."
- sd-save-log-it run 2: missing call: save_note | heard: "I'm sorry, I can't save that for you right now."
- sd-save-log-it run 3: missing call: save_note | heard: "I can't log that for you, as I don't have the ability to save information or keep track of your activities."

