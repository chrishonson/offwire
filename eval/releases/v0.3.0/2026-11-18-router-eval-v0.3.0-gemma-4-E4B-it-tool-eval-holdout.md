| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.819 | 0.1 | 0.917 | 0.786 | 0.833 | 0.0 | 0.5 | 0 | 4.149 / 3.049 | 4.2213 / 3.122 | 0 |

## gemma-4-E4B-it / embed_mlp

```
{
  "model": "gemma-4-E4B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": false,
  "rows": 72,
  "pass_rate": 0.819,
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
    "rate": 0.028
  },
  "false_claim": {
    "n": 36,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 30,
    "rate": 0.5
  },
  "double_confirm_guarded": {
    "n": 30,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 30,
    "rate": 0.567
  },
  "parse_error": {
    "n": 72,
    "count": 0
  },
  "ttft_s_median": 3.741,
  "decode_tok_s_median": 20.44,
  "by_category": {
    "action": "15/21",
    "no_call": "9/9",
    "over_call": "18/21",
    "save": "17/21"
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
      "router_s_median": 0.0726,
      "response_ttft_s_median": 4.149,
      "total_s_median": 4.2213,
      "prompt_tokens_median": 466.0
    },
    "no_tool_turns": {
      "n": 36,
      "router_s_median": 0.0724,
      "response_ttft_s_median": 3.049,
      "total_s_median": 3.122,
      "prompt_tokens_median": 497.0
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
- ho-put-a-note run 2: called 2 times | heard: 'I saved a note that the gutter guy comes Thursday.'
- ho-remember-comma run 1: missing call: save_note | heard: 'I can remember that the recital is at six-thirty on Friday.'
- ho-remember-comma run 2: missing call: save_note | heard: "I've got that the recital is on Friday at 6:30."
- ho-remember-comma run 3: missing call: save_note | heard: 'I remember the recital is at 6:30 on Friday.'
- ho-get-to-store run 1: missing call: do_action | heard: "I can't give you directions because I do not know where here is."
- ho-get-to-store run 2: missing call: do_action | heard: 'I do not know your current location, so I cannot tell you how to get to the hardware store.'
- ho-get-to-store run 3: missing call: do_action | heard: 'I do not have access to your current location to give you directions to a hardware store.'
- ho-navigate-home run 1: missing call: do_action | heard: "I am a voice assistant and do not have control over your device's navigation."
- ho-navigate-home run 2: missing call: do_action | heard: 'I can talk, answer questions, and save notes for you.'
- ho-navigate-home run 3: missing call: do_action | heard: "I can't navigate for you. I am a voice assistant running on your phone."
- ho-log-cabin run 1: unwanted call: save_note | heard: 'I saved a note about the best wood for log cabins.\n\nAs for the best wood for log cabins, several woods are popular and offer different benef'
- ho-log-cabin run 2: unwanted call: save_note | heard: 'The notes have been saved. As for the best wood for a log cabin, it really depends on where you are located and what style you are going for'
- ho-log-cabin run 3: unwanted call: save_note | heard: 'I saved that you asked about the best wood for log cabins. Generally, cedar, pine, and redwood are popular choices for log cabins because th'

