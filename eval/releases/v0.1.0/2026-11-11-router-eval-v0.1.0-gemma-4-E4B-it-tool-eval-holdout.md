| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.833 | 0.2 | 0.857 | 0.857 | 0.833 | 0.0 | 0.556 | 0 | 3.6977 / 1.5242 | 3.7703 / 1.5963 | 0 |

## gemma-4-E4B-it / embed_mlp

```
{
  "model": "gemma-4-E4B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 72,
  "pass_rate": 0.833,
  "call_precision": 0.857,
  "call_recall": 0.857,
  "over_call": {
    "n": 30,
    "rate": 0.2
  },
  "wrong_tool": {
    "n": 42,
    "rate": 0.0
  },
  "bad_args": {
    "n": 36,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 42,
    "rate": 0.0
  },
  "false_claim": {
    "n": 30,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 36,
    "rate": 0.556
  },
  "double_confirm_guarded": {
    "n": 36,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 36,
    "rate": 0.694
  },
  "parse_error": {
    "n": 72,
    "count": 0
  },
  "ttft_s_median": 3.011,
  "decode_tok_s_median": 21.01,
  "by_category": {
    "action": "18/21",
    "no_call": "9/9",
    "over_call": "15/21",
    "save": "18/21"
  },
  "intent_table": {
    "ACTION": {
      "ACTION": 18,
      "NONE": 3
    },
    "NONE": {
      "SAVE": 3,
      "ACTION": 3,
      "NONE": 24
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
      "n": 42,
      "router_s_median": 0.0726,
      "response_ttft_s_median": 3.6977,
      "total_s_median": 3.7703,
      "prompt_tokens_median": 408.0
    },
    "no_tool_turns": {
      "n": 30,
      "router_s_median": 0.0722,
      "response_ttft_s_median": 1.5242,
      "total_s_median": 1.5963,
      "prompt_tokens_median": 215.0
    }
  }
}
```

Failures:
- ho-remember-comma run 1: missing call: save_note | heard: 'I understand that the recital is at 6:30 on Friday.'
- ho-remember-comma run 2: missing call: save_note | heard: 'I understand that the recital is at 6:30 on Friday.'
- ho-remember-comma run 3: missing call: save_note | heard: 'I do not have any notes yet.'
- ho-navigate-home run 1: missing call: do_action | heard: "I'm sorry, I don't know how to do that."
- ho-navigate-home run 2: missing call: do_action | heard: "I'm sorry, I don't know how to do that."
- ho-navigate-home run 3: missing call: do_action | heard: 'I am a voice assistant and do not have control over your physical device or applications, so I cannot navigate you home.'
- ho-remember-movie run 1: unwanted call: save_note | heard: "I saved that to your notes so I can help you with it later. I don't have any specific memory of that movie, but if you can tell me more deta"
- ho-remember-movie run 2: unwanted call: save_note | heard: "I saved that to your notes so I can help you with it later. I don't have any specific memory of that movie, but if you can tell me more deta"
- ho-remember-movie run 3: unwanted call: save_note | heard: 'I have saved your request about the movie with the talking dog in your notes.'
- ho-microwave-timer run 1: unwanted call: do_action | heard: 'I set a reminder for you about how microwave timers work.\n\nMicrowave timers work by using the same principle as the microwave oven itself, w'
- ho-microwave-timer run 2: unwanted call: do_action | heard: 'I set a reminder for you about how microwave timers work.\n\nMicrowave timers work by using the same principle as the microwave oven itself, w'
- ho-microwave-timer run 3: unwanted call: do_action | heard: 'To start with, a microwave oven timer works by using electronic circuits to control the power delivery to the magnetron, which is the part t'

