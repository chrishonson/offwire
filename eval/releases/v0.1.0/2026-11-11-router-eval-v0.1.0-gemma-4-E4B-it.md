| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.917 | 0.0 | 1.0 | 0.833 | 0.958 | 0.0 | 0.442 | 2 | 3.9609 / 1.524 | 4.0332 / 1.602 | 0 |

## gemma-4-E4B-it / embed_mlp

```
{
  "model": "gemma-4-E4B-it",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "rows": 144,
  "pass_rate": 0.917,
  "call_precision": 1.0,
  "call_recall": 0.833,
  "over_call": {
    "n": 87,
    "rate": 0.0
  },
  "wrong_tool": {
    "n": 57,
    "rate": 0.0
  },
  "bad_args": {
    "n": 49,
    "rate": 0.0
  },
  "repeat_call": {
    "n": 49,
    "rate": 0.0
  },
  "false_claim": {
    "n": 95,
    "rate": 0.0
  },
  "double_confirm_raw": {
    "n": 43,
    "rate": 0.442
  },
  "double_confirm_guarded": {
    "n": 43,
    "rate": 0.0
  },
  "spoke_both_sides": {
    "n": 43,
    "rate": 0.674
  },
  "parse_error": {
    "n": 144,
    "count": 2
  },
  "ttft_s_median": 1.564,
  "decode_tok_s_median": 21.08,
  "by_category": {
    "action": "23/27",
    "cannot": "12/12",
    "multi": "1/3",
    "no_call": "36/36",
    "over_call": "27/27",
    "recall": "12/12",
    "save": "21/27"
  },
  "intent_table": {
    "ACTION": {
      "ACTION": 27
    },
    "NONE": {
      "NONE": 87
    },
    "SAVE": {
      "SAVE": 21,
      "NONE": 6
    },
    "SAVE+ACTION": {
      "SAVE+ACTION": 3
    }
  },
  "intent_match": 0.958,
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
      "n": 51,
      "router_s_median": 0.0727,
      "response_ttft_s_median": 3.9609,
      "total_s_median": 4.0332,
      "prompt_tokens_median": 462
    },
    "no_tool_turns": {
      "n": 93,
      "router_s_median": 0.073,
      "response_ttft_s_median": 1.524,
      "total_s_median": 1.602,
      "prompt_tokens_median": 215
    }
  }
}
```

Failures:
- save-wifi run 1: missing call: save_note | heard: 'The guest wifi password is sunflower42.'
- save-wifi run 2: missing call: save_note | heard: 'The guest wifi password is sunflower42.'
- save-wifi run 3: missing call: save_note | heard: 'The guest wifi password is sunflower42.'
- save-run run 1: missing call: save_note | heard: 'I am unable to log that information for you right now.'
- save-run run 2: missing call: save_note | heard: 'I am unable to log that information for you right now.'
- save-run run 3: missing call: save_note | heard: 'I do not have the ability to log physical activity or save that information for you.'
- act-cal-today run 1: missing any of: dentist | heard: 'I am looking at what is on your calendar for today.'
- act-cal-today run 2: missing any of: dentist | heard: 'I am looking at what is on your calendar for today.'
- act-cal-tomorrow run 1: missing any of: meetup|7 | heard: 'I checked your calendar and there is no information for tomorrow evening.'
- act-cal-tomorrow run 2: missing any of: meetup|7 | heard: 'I checked your calendar and there is no information for tomorrow evening.'
- multi-save-and-timer run 1: parse_error: INVALID_ARGUMENT: Failed to parse tool calls from code block: call:save_note{text: furnace needs a new filter}
full response: <|tool_call>call:save_note{text: furnace needs a new filter}<tool_call|>
e | heard: ''
- multi-save-and-timer run 2: parse_error: INVALID_ARGUMENT: Failed to parse tool calls from code block: call:save_note{text: furnace needs a new filter}
full response: <|tool_call>call:save_note{text: furnace needs a new filter}<tool_call|>
e | heard: ''

