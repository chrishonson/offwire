| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.967 | 0.0 | 1.0 | 1.0 | 1.0 | 0.056 | 0.0 | 0 | 1.8297 / 0.8784 | 1.9773 / 0.9529 | 0 |

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
    "rate": 0.056
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
  "ttft_s_median": 0.884,
  "decode_tok_s_median": 31.29,
  "by_category": {
    "capability": "27/27",
    "complaint": "23/24",
    "save": "48/48",
    "talk": "18/21"
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
      "router_s_median": 0.0741,
      "response_ttft_s_median": 1.8297,
      "total_s_median": 1.9773,
      "prompt_tokens_median": 388.0
    },
    "no_tool_turns": {
      "n": 72,
      "router_s_median": 0.0733,
      "response_ttft_s_median": 0.8784,
      "total_s_median": 0.9529,
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
      "count": 27
    }
  }
}
```

Failures:
- sd-comp-saved-wrong-thing run 3: claims an action without a call | heard: 'I saved that because you asked me to and the prompt indicated a message that asks for a save.'
- sd-talk-what-did-i-save run 1: claims an action without a call | heard: 'You saved that the dentist is Friday at 3.'
- sd-talk-what-did-i-save run 2: claims an action without a call | heard: 'You saved that the dentist is Friday at 3.'
- sd-talk-what-did-i-save run 3: claims an action without a call | heard: 'You saved that the dentist is on Friday at 3.'

