| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.991 | 0.0 | 1.0 | 1.0 | 1.0 | 0.0 | 0.0 | 0 | 1.9703 / 0.1681 | 2.0369 / 0.2512 | 0 |

## hosted-gemma-4-E2B-it-Q4_K_M / embed_mlp

```
{
  "model": "hosted-gemma-4-E2B-it-Q4_K_M",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": true,
  "rows": 114,
  "pass_rate": 0.991,
  "call_precision": 1.0,
  "call_recall": 1.0,
  "over_call": {
    "n": 87,
    "rate": 0.0
  },
  "wrong_tool": {
    "n": 27,
    "rate": 0.0
  },
  "bad_args": {
    "n": 27,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 27,
    "rate": 0.0
  },
  "false_claim": {
    "n": 87,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 27,
    "rate": 0.0
  },
  "double_confirm_guarded": {
    "n": 27,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 27,
    "rate": 0.0
  },
  "parse_error": {
    "n": 114,
    "count": 0
  },
  "ttft_s_median": 0.193,
  "decode_tok_s_median": null,
  "by_category": {
    "cannot": "12/12",
    "no_call": "36/36",
    "over_call": "27/27",
    "recall": "11/12",
    "save": "27/27"
  },
  "intent_table": {
    "NONE": {
      "NONE": 87
    },
    "SAVE": {
      "SAVE": 27
    }
  },
  "intent_match": 1.0,
  "route_sources": {
    "embed": 114
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 27,
      "router_s_median": 0.0766,
      "response_ttft_s_median": 1.9703,
      "total_s_median": 2.0369,
      "prompt_tokens_median": null
    },
    "no_tool_turns": {
      "n": 87,
      "router_s_median": 0.0761,
      "response_ttft_s_median": 0.1681,
      "total_s_median": 0.2512,
      "prompt_tokens_median": null
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 87,
      "count": 0
    },
    "missed_saves": {
      "n": 27,
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
- recall-missing run 2: missing any of: \b(no|not|don't|didn't|couldn't|nothing|any)\b | heard: 'You have a note that says brake pads have three millimeters left.'

