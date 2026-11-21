| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 1.0 | 0.0 | 1.0 | 1.0 | 1.0 | 0.0 | 0.0 | 0 | 3.1185 / 0.1384 | 3.1887 / 0.229 | 0 |

## hosted-gemma-4-E2B-it-Q4_K_M / embed_mlp

```
{
  "model": "hosted-gemma-4-E2B-it-Q4_K_M",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": true,
  "rows": 36,
  "pass_rate": 1.0,
  "call_precision": 1.0,
  "call_recall": 1.0,
  "over_call": {
    "n": 30,
    "rate": 0.0
  },
  "wrong_tool": {
    "n": 6,
    "rate": 0.0
  },
  "bad_args": {
    "n": 6,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 6,
    "rate": 0.0
  },
  "false_claim": {
    "n": 30,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 6,
    "rate": 0.0
  },
  "double_confirm_guarded": {
    "n": 6,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 6,
    "rate": 0.0
  },
  "parse_error": {
    "n": 36,
    "count": 0
  },
  "ttft_s_median": 0.162,
  "decode_tok_s_median": null,
  "by_category": {
    "brainstorm": "18/18",
    "near_miss": "12/12",
    "save": "6/6"
  },
  "intent_table": {
    "NONE": {
      "NONE": 30
    },
    "SAVE": {
      "SAVE": 6
    }
  },
  "intent_match": 1.0,
  "route_sources": {
    "embed": 36
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 6,
      "router_s_median": 0.0759,
      "response_ttft_s_median": 3.1185,
      "total_s_median": 3.1887,
      "prompt_tokens_median": null
    },
    "no_tool_turns": {
      "n": 30,
      "router_s_median": 0.0767,
      "response_ttft_s_median": 0.1384,
      "total_s_median": 0.229,
      "prompt_tokens_median": null
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 30,
      "count": 0
    },
    "missed_saves": {
      "n": 6,
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

