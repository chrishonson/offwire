| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.979 | 0.0 | 1.0 | 1.0 | 1.0 | 0.0 | 0.0 | 0 | 2.0604 / 1.1644 | 2.0813 / 1.1712 | 0 |

## gemma-4-E2B-it / embed_mlp

```
{
  "model": "gemma-4-E2B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": false,
  "rows": 144,
  "pass_rate": 0.979,
  "call_precision": 1.0,
  "call_recall": 1.0,
  "over_call": {
    "n": 87,
    "rate": 0.0
  },
  "wrong_tool": {
    "n": 57,
    "rate": 0.0
  },
  "bad_args": {
    "n": 57,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 57,
    "rate": 0.0
  },
  "false_claim": {
    "n": 87,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 51,
    "rate": 0.0
  },
  "double_confirm_guarded": {
    "n": 51,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 51,
    "rate": 0.0
  },
  "parse_error": {
    "n": 144,
    "count": 0
  },
  "ttft_s_median": 1.169,
  "decode_tok_s_median": 30.42,
  "by_category": {
    "action": "27/27",
    "cannot": "12/12",
    "multi": "3/3",
    "no_call": "36/36",
    "over_call": "27/27",
    "recall": "9/12",
    "save": "27/27"
  },
  "intent_table": {
    "ACTION": {
      "ACTION": 27
    },
    "NONE": {
      "NONE": 87
    },
    "SAVE": {
      "SAVE": 27
    },
    "SAVE+ACTION": {
      "SAVE+ACTION": 3
    }
  },
  "intent_match": 1.0,
  "route_sources": {
    "embed": 144
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 57,
      "router_s_median": 0.0063,
      "response_ttft_s_median": 2.0604,
      "total_s_median": 2.0813,
      "prompt_tokens_median": 525
    },
    "no_tool_turns": {
      "n": 87,
      "router_s_median": 0.0068,
      "response_ttft_s_median": 1.1644,
      "total_s_median": 1.1712,
      "prompt_tokens_median": 398
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 114,
      "count": 0
    },
    "missed_saves": {
      "n": 30,
      "count": 0
    },
    "capability_honest": {
      "n": 0,
      "count": 0
    }
  }
}
```

Failures:
- recall-meetup run 1: missing any of: 24 | heard: 'The AI meetup has been moved to the twenty-fourth.'
- recall-meetup run 2: missing any of: 24 | heard: 'The AI meetup has been moved to the twenty-fourth.'
- recall-meetup run 3: missing any of: 24 | heard: 'The AI meetup has been moved to the twenty-fourth.'

