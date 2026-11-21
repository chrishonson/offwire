| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.942 | 0.062 | 0.875 | 1.0 | 0.87 | 0.0 | 0.0 | 0 | 2.2106 / 0.1767 | 2.2919 / 0.2884 | 0 |

## hosted-gemma-4-E2B-it-Q4_K_M / embed_mlp

```
{
  "model": "hosted-gemma-4-E2B-it-Q4_K_M",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": true,
  "rows": 69,
  "pass_rate": 0.942,
  "call_precision": 0.875,
  "call_recall": 1.0,
  "over_call": {
    "n": 48,
    "rate": 0.062
  },
  "wrong_tool": {
    "n": 21,
    "rate": 0.0
  },
  "bad_args": {
    "n": 21,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 24,
    "rate": 0.0
  },
  "false_claim": {
    "n": 45,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 24,
    "rate": 0.0
  },
  "double_confirm_guarded": {
    "n": 24,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 24,
    "rate": 0.042
  },
  "parse_error": {
    "n": 69,
    "count": 0
  },
  "ttft_s_median": 0.222,
  "decode_tok_s_median": null,
  "by_category": {
    "brainstorm": "29/30",
    "near_miss": "15/18",
    "save": "21/21"
  },
  "intent_table": {
    "NONE": {
      "NONE": 39,
      "SAVE": 6,
      "ACTION": 3
    },
    "SAVE": {
      "SAVE": 21
    }
  },
  "intent_match": 0.87,
  "route_sources": {
    "embed": 69
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 30,
      "router_s_median": 0.0759,
      "response_ttft_s_median": 2.2106,
      "total_s_median": 2.2919,
      "prompt_tokens_median": null
    },
    "no_tool_turns": {
      "n": 39,
      "router_s_median": 0.0781,
      "response_ttft_s_median": 0.1767,
      "total_s_median": 0.2884,
      "prompt_tokens_median": null
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 48,
      "count": 3
    },
    "missed_saves": {
      "n": 21,
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
- bs-ref-deeper run 2: missing any of: flight|plane|offline|airplane | heard: 'You could start by asking everyone if they have ever thought about how powerful a small device can be, perhaps drawing a parallel to how eas'
- bs-ref-favorite run 1: unwanted call: save_note | heard: 'I have saved that you like the idea of a talk opener about how phones could run five years ago.'
- bs-ref-favorite run 2: unwanted call: save_note | heard: "Okay, I'll save that idea for you."
- bs-ref-favorite run 3: unwanted call: save_note | heard: 'I have saved that you like the second opening idea for your AI meetup talk about running Gemma on a phone.'

