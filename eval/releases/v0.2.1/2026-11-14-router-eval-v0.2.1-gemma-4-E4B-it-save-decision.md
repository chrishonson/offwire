| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.975 | 0.0 | 1.0 | 1.0 | 1.0 | 0.0 | 0.562 | 0 | 3.9436 / 2.2621 | 4.134 / 2.3395 | 0 |

## gemma-4-E4B-it / embed_mlp

```
{
  "model": "gemma-4-E4B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 120,
  "pass_rate": 0.975,
  "call_precision": 1.0,
  "call_recall": 1.0,
  "over_call": {
    "n": 72,
    "rate": 0.0
  },
  "wrong_tool": {
    "n": 48,
    "rate": 0.0
  },
  "bad_args": {
    "n": 48,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 48,
    "rate": 0.021
  },
  "false_claim": {
    "n": 72,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 48,
    "rate": 0.562
  },
  "double_confirm_guarded": {
    "n": 48,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 48,
    "rate": 0.667
  },
  "parse_error": {
    "n": 120,
    "count": 0
  },
  "ttft_s_median": 2.284,
  "decode_tok_s_median": 20.46,
  "by_category": {
    "capability": "25/27",
    "complaint": "24/24",
    "save": "47/48",
    "talk": "21/21"
  },
  "intent_table": {
    "NONE": {
      "NONE": 72
    },
    "SAVE": {
      "SAVE": 48
    }
  },
  "intent_match": 1.0,
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
      "n": 48,
      "router_s_median": 0.074,
      "response_ttft_s_median": 3.9436,
      "total_s_median": 4.134,
      "prompt_tokens_median": 388.0
    },
    "no_tool_turns": {
      "n": 72,
      "router_s_median": 0.0735,
      "response_ttft_s_median": 2.2621,
      "total_s_median": 2.3395,
      "prompt_tokens_median": 305.5
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 72,
      "count": 0
    },
    "missed_saves": {
      "n": 48,
      "count": 0
    },
    "capability_honest": {
      "n": 27,
      "count": 25
    }
  }
}
```

Failures:
- sd-cap-after-denial run 1: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I am sure that I can't save notes."
- sd-cap-after-denial run 2: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: "I am sure that I can't save notes."
- sd-save-can-you-save run 3: called 2 times | heard: 'I have saved a note that the plumber comes Tuesday.'

