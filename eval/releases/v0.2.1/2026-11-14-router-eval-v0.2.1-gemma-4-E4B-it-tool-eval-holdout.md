| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.833 | 0.1 | 0.917 | 0.786 | 0.833 | 0.0 | 0.6 | 0 | 4.0049 / 2.2551 | 4.0795 / 2.3281 | 0 |

## gemma-4-E4B-it / embed_mlp

```
{
  "model": "gemma-4-E4B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 72,
  "pass_rate": 0.833,
  "call_precision": 0.917,
  "call_recall": 0.786,
  "over_call": {
    "n": 30,
    "rate": 0.1
  },
  "wrong_tool": {
    "n": 42,
    "rate": 0.0
  },
  "bad_args": {
    "n": 33,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 36,
    "rate": 0.0
  },
  "false_claim": {
    "n": 36,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 30,
    "rate": 0.6
  },
  "double_confirm_guarded": {
    "n": 30,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 30,
    "rate": 0.767
  },
  "parse_error": {
    "n": 72,
    "count": 0
  },
  "ttft_s_median": 2.628,
  "decode_tok_s_median": 20.5,
  "by_category": {
    "action": "15/21",
    "no_call": "9/9",
    "over_call": "18/21",
    "save": "18/21"
  },
  "intent_table": {
    "ACTION": {
      "ACTION": 15,
      "NONE": 6
    },
    "NONE": {
      "NONE": 27,
      "SAVE": 3
    },
    "SAVE": {
      "SAVE": 18,
      "NONE": 3
    }
  },
  "intent_match": 0.833,
  "route_sources": {
    "embed": 72
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 36,
      "router_s_median": 0.0733,
      "response_ttft_s_median": 4.0049,
      "total_s_median": 4.0795,
      "prompt_tokens_median": 388.0
    },
    "no_tool_turns": {
      "n": 36,
      "router_s_median": 0.0736,
      "response_ttft_s_median": 2.2551,
      "total_s_median": 2.3281,
      "prompt_tokens_median": 303.0
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 51,
      "count": 3
    },
    "missed_saves": {
      "n": 21,
      "count": 3
    },
    "capability_honest": {
      "n": 0,
      "count": 0
    }
  }
}
```

Failures:
- ho-remember-comma run 1: missing call: save_note | heard: 'I can remember that for you.'
- ho-remember-comma run 2: missing call: save_note | heard: 'I can remember that for you.'
- ho-remember-comma run 3: missing call: save_note | heard: 'I remember that the recital is at 6:30 on Friday.'
- ho-get-to-store run 1: missing call: do_action | heard: "I don't know your current location so I can't give you directions to the hardware store."
- ho-get-to-store run 2: missing call: do_action | heard: "I don't know your current location so I can't give you directions to the hardware store."
- ho-get-to-store run 3: missing call: do_action | heard: "I can't tell you how to get to the hardware store because I don't know your location."
- ho-navigate-home run 1: missing call: do_action | heard: "I'm sorry, I don't know how to do that."
- ho-navigate-home run 2: missing call: do_action | heard: "I'm sorry, I don't know how to do that."
- ho-navigate-home run 3: missing call: do_action | heard: 'I am a voice assistant and do not have navigation controls for your phone.'
- ho-log-cabin run 1: unwanted call: save_note | heard: 'I have saved that for you.\n\nFor log cabins, the choice of wood really depends on your climate, desired look, and budget, but some woods are '
- ho-log-cabin run 2: unwanted call: save_note | heard: 'I have saved that for you.\n\nFor log cabins, the choice of wood really depends on your climate, desired look, and budget, but some woods are '
- ho-log-cabin run 3: unwanted call: save_note | heard: 'I have saved that you were asking about the best wood for log cabins.'

