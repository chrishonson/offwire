| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.967 | 0.0 | 1.0 | 1.0 | 1.0 | 0.014 | 0.0 | 0 | 1.6264 / 0.8884 | 1.7748 / 0.9636 | 0 |

## gemma-4-E2B-it / embed_mlp

```
{
  "model": "gemma-4-E2B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 120,
  "pass_rate": 0.967,
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
    "rate": 0.0
  },
  "false_claim": {
    "n": 72,
    "rate": 0.014
  },
  "double_confirm_raw": {
    "n": 48,
    "rate": 0.0
  },
  "double_confirm_guarded": {
    "n": 48,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 48,
    "rate": 0.0
  },
  "parse_error": {
    "n": 120,
    "count": 0
  },
  "ttft_s_median": 0.896,
  "decode_tok_s_median": 30.47,
  "by_category": {
    "capability": "24/27",
    "complaint": "23/24",
    "save": "48/48",
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
      "router_s_median": 0.0743,
      "response_ttft_s_median": 1.6264,
      "total_s_median": 1.7748,
      "prompt_tokens_median": 367.0
    },
    "no_tool_turns": {
      "n": 72,
      "router_s_median": 0.0741,
      "response_ttft_s_median": 0.8884,
      "total_s_median": 0.9636,
      "prompt_tokens_median": 266.5
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
      "count": 24
    }
  }
}
```

Failures:
- sd-phone-failure run 3: claims an action without a call | heard: "I'm designed to remember the information we have during our current conversation so I can follow along. But if you're talking about informat"
- sd-cap-after-denial run 1: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: 'I do not have a tool to save notes at this moment.'
- sd-cap-after-denial run 2: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: 'I do not have a tool to save notes at this moment.'
- sd-cap-after-denial run 3: matched forbidden: \b(don't|do not|can't|cannot|can not)\s+(have|save|keep|take|write|store) | heard: 'I understand why you might think that, but right now I do not have the ability to save notes for you.'

