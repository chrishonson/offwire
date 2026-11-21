| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.897 | 0.0 | None | None | 1.0 | 0.0 | None | 0 | None / 0.1325 | None / 0.2045 | 0 |

## hosted-gemma-4-E2B-it-Q4_K_M / embed_mlp

```
{
  "model": "hosted-gemma-4-E2B-it-Q4_K_M",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": true,
  "rows": 39,
  "pass_rate": 0.897,
  "call_precision": null,
  "call_recall": null,
  "over_call": {
    "n": 39,
    "rate": 0.0
  },
  "wrong_tool": {
    "n": 0,
    "rate": null
  },
  "bad_args": {
    "n": 0,
    "rate": null
  },
  "repeat_call": {
    "n": 0,
    "rate": null
  },
  "false_claim": {
    "n": 39,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 0,
    "rate": null
  },
  "double_confirm_guarded": {
    "n": 0,
    "rate": null
  },
  "spoke_both_sides": {
    "n": 0,
    "rate": null
  },
  "parse_error": {
    "n": 39,
    "count": 0
  },
  "ttft_s_median": 0.132,
  "decode_tok_s_median": null,
  "by_category": {
    "brainstorm": "35/39"
  },
  "intent_table": {
    "NONE": {
      "NONE": 39
    }
  },
  "intent_match": 1.0,
  "route_sources": {
    "embed": 39
  },
  "gate": {
    "calls": 0,
    "parse_errors": 0,
    "prompt_tokens_median": null,
    "router_s_median": null
  },
  "timing": {
    "tool_turns": {
      "n": 0,
      "router_s_median": null,
      "response_ttft_s_median": null,
      "total_s_median": null,
      "prompt_tokens_median": null
    },
    "no_tool_turns": {
      "n": 39,
      "router_s_median": 0.0754,
      "response_ttft_s_median": 0.1325,
      "total_s_median": 0.2045,
      "prompt_tokens_median": null
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 39,
      "count": 0
    },
    "missed_saves": {
      "n": 0,
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
- lt-not-that-one run 2: missing any of: dead zone|back road|no signal|airplane|on.device|privacy|leaves the phone | heard: 'Are you referring to the idea of how the app decides when to save a note?'
- lt-not-the-demo run 1: missing any of: save|router|near miss|note | heard: 'You are referring to the live demo idea. We could show how the phone processes a request and how the system decides if it needs to use the o'
- lt-not-the-demo run 2: missing any of: save|router|near miss|note | heard: 'Are you referring to the live demonstration idea, or the "why on device matters" part we were just discussing?'
- lt-not-the-demo run 3: missing any of: save|router|near miss|note | heard: 'You are referring to the live demonstration idea. We can focus the demo on showing how the on-device processing handles a slightly more comp'

