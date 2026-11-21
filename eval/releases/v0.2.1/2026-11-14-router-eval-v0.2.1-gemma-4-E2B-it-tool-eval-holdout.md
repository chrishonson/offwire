| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.75 | 0.1 | 0.909 | 0.714 | 0.833 | 0.077 | 0.0 | 0 | 1.8609 / 0.8784 | 1.9342 / 0.9523 | 0 |

## gemma-4-E2B-it / embed_mlp

```
{
  "model": "gemma-4-E2B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 72,
  "pass_rate": 0.75,
  "call_precision": 0.909,
  "call_recall": 0.714,
  "over_call": {
    "n": 30,
    "rate": 0.1
  },
  "wrong_tool": {
    "n": 42,
    "rate": 0.0
  },
  "bad_args": {
    "n": 30,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 33,
    "rate": 0.0
  },
  "false_claim": {
    "n": 39,
    "rate": 0.077
  },
  "double_confirm_raw": {
    "n": 30,
    "rate": 0.0
  },
  "double_confirm_guarded": {
    "n": 30,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 30,
    "rate": 0.0
  },
  "parse_error": {
    "n": 72,
    "count": 0
  },
  "ttft_s_median": 1.03,
  "decode_tok_s_median": 30.19,
  "by_category": {
    "action": "12/21",
    "no_call": "9/9",
    "over_call": "15/21",
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
      "response_ttft_s_median": 1.8609,
      "total_s_median": 1.9342,
      "prompt_tokens_median": 388.0
    },
    "no_tool_turns": {
      "n": 36,
      "router_s_median": 0.0731,
      "response_ttft_s_median": 0.8784,
      "total_s_median": 0.9523,
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
- ho-remember-comma run 1: missing call: save_note | heard: 'I understand that the recital is scheduled for Friday at 6:30.'
- ho-remember-comma run 2: missing call: save_note | heard: 'I understand that the recital is scheduled for Friday at 6:30.'
- ho-remember-comma run 3: missing call: save_note | heard: 'I will remember that the recital is at 6:30 on Friday.'
- ho-get-to-store run 1: missing call: do_action | heard: "I'm sorry, I don't know where you are right now, so I can't tell you how to get to the hardware store. You might want to use a map applicati"
- ho-get-to-store run 2: missing call: do_action | heard: "I'm sorry, I don't know where you are right now, so I can't tell you how to get to the hardware store. You might want to use a map applicati"
- ho-get-to-store run 3: missing call: do_action | heard: "I don't have access to your current location so I cannot tell you exactly how to get to the hardware store from where you are right now. You"
- ho-navigate-home run 1: missing call: do_action | heard: "I can't directly navigate you home since I don't know where you are or what kind of device you are using. You'll need to use a map applicati"
- ho-navigate-home run 2: missing call: do_action | heard: "I can't directly navigate you home since I don't know where you are or what kind of device you are using. You'll need to use a map applicati"
- ho-navigate-home run 3: missing call: do_action | heard: 'I am sorry, but I cannot navigate.'
- ho-busy-afternoon run 1: missing call: do_action | heard: 'I do not have access to your schedule to tell you if you are busy this afternoon.'
- ho-busy-afternoon run 2: missing call: do_action | heard: 'I do not have access to your schedule to tell you if you are busy this afternoon.'
- ho-busy-afternoon run 3: missing call: do_action | heard: 'I do not have access to your schedule to tell you if you are busy this afternoon.'
- ho-log-cabin run 1: unwanted call: save_note | heard: 'I saved your question about the best wood for log cabins to your notes.'
- ho-log-cabin run 2: unwanted call: save_note | heard: 'I saved your question about the best wood for log cabins to your notes.'
- ho-log-cabin run 3: unwanted call: save_note | heard: 'I have saved a note asking about the best wood for log cabins.'
- ho-maps-history run 1: claims an action without a call | heard: 'Google Maps has a fascinating history! It all started with Google wanting to create a way for people to find places easily. They began by wo'
- ho-maps-history run 2: claims an action without a call | heard: 'Google Maps has a fascinating history! It all started with Google wanting to create a way for people to find places easily. They began by wo'
- ho-maps-history run 3: claims an action without a call | heard: 'Google Maps has a fascinating history! It all started when Google wanted to make information about the world more accessible. They began wor'

