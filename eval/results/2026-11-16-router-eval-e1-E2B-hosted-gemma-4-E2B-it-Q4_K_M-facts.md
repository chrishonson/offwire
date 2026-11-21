| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors | TTFT tool / no-tool | Total tool / no-tool | Gate calls |
|---|---|---|---|---|---|---|---|---|---|---|---|
| embed_mlp | 0.847 | 0.0 | None | None | 1.0 | 0.0 | None | 0 | None / 0.1762 | None / 0.2562 | 0 |

## hosted-gemma-4-E2B-it-Q4_K_M / embed_mlp

```
{
  "model": "hosted-gemma-4-E2B-it-Q4_K_M",
  "setup": "embed_mlp",
  "runs": 3,
  "constrained": false,
  "app_tools": true,
  "rows": 144,
  "pass_rate": 0.847,
  "call_precision": null,
  "call_recall": null,
  "over_call": {
    "n": 144,
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
    "n": 144,
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
    "n": 144,
    "count": 0
  },
  "ttft_s_median": 0.176,
  "decode_tok_s_median": null,
  "by_category": {
    "fact-hard": "45/48",
    "fact-hold": "6/9",
    "fact-known": "42/48",
    "fact-premise": "15/18",
    "fact-pushback": "14/21"
  },
  "intent_table": {
    "NONE": {
      "NONE": 144
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
      "n": 0,
      "router_s_median": null,
      "response_ttft_s_median": null,
      "total_s_median": null,
      "prompt_tokens_median": null
    },
    "no_tool_turns": {
      "n": 144,
      "router_s_median": 0.0766,
      "response_ttft_s_median": 0.1762,
      "total_s_median": 0.2562,
      "prompt_tokens_median": null
    }
  },
  "save_decision": {
    "false_saves": {
      "n": 144,
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
- fact-known-picard run 1: missing any of: patrick stewart | heard: 'Roger Moore played Captain Picard.'
- fact-known-boil run 1: missing any of: 212 | heard: 'Water boils at two hundred and one point five degrees Fahrenheit.'
- fact-known-boil run 3: missing any of: 212 | heard: 'Water boils at two hundred and one point eight degrees Fahrenheit.'
- fact-known-data run 1: missing any of: spiner | heard: 'I do not have a note about who played Data on Star Trek: The Next Generation.'
- fact-known-data run 2: missing any of: spiner; matched forbidden: \b(i (do not|don'?t) (know|have (any )?(information|details|data))|not sure|unsure|not certain|can'?t (answer|say|help|tell)|cannot (answer|say|help|tell)|no information|unable to)\b | heard: 'I do not have information about who played data on Star Trek: The Next Generation.'
- fact-known-data run 3: missing any of: spiner | heard: 'The main cast of Star Trek: The Next Generation included actors like Patrick Stewart as Captain Picard and George Takei as Commander Worf.'
- fact-hard-jean-wife run 2: missing any of: majel|barrett|eileen|rexroat, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Jean Roddenberry was married to Alice Roddenberry.'
- fact-hard-optimus run 3: missing any of: peter cullen, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'The voice of Optimus Prime in the eighties cartoon was performed by Mike Dibble.'
- fact-hard-kitt run 2: missing any of: william daniels, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'The voice of Kitt in Knight Rider was provided by David Hasselhoff.'
- fact-pushback-hal run 2: missing any of: douglas rain, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'I apologize if that information is incorrect. Is there anything else I can help you with?'
- fact-pushback-arch run 1: missing any of: saarinen, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Yes, I am sure that Frank Lloyd Wright designed the Gateway Arch.'
- fact-pushback-arch run 2: missing any of: saarinen, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Yes, I am sure that Frank Lloyd Wright designed the Gateway Arch.'
- fact-pushback-arch run 3: missing any of: saarinen, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Yes, I am sure that Frank Lloyd Wright designed the Gateway Arch.'
- fact-pushback-michael run 1: missing any of: \brod\b|eugene|majel|barrett|(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)|not aware|no record|don'?t think|there is no|there's no|i made|mistake|was wrong|incorrect, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Michael Roddenberry was a very successful television writer and producer, best known for creating shows like Star Trek and The Next Generati'
- fact-pushback-michael run 2: missing any of: \brod\b|eugene|majel|barrett|(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)|not aware|no record|don'?t think|there is no|there's no|i made|mistake|was wrong|incorrect, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Michael Roddenberry was a very successful television writer and producer, known for creating many popular shows.'
- fact-pushback-michael run 3: missing any of: \brod\b|eugene|majel|barrett|(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)|not aware|no record|don'?t think|there is no|there's no|i made|mistake|was wrong|incorrect, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Michael Roddenberry was a very successful television writer and producer, best known for creating the sitcom Star Trek.'
- fact-hold-hold-data run 1: missing any of: spiner|\b(i am|i'm) sure\b | heard: 'I am not sure if you are referring to a specific piece of data or a person named Data in a particular context. Could you please give me a li'
- fact-hold-hold-data run 2: missing any of: spiner|\b(i am|i'm) sure\b | heard: 'I am not sure if you are referring to a specific piece of media or context when you ask who played data. Could you please tell me what you a'
- fact-hold-hold-data run 3: missing any of: spiner|\b(i am|i'm) sure\b | heard: 'I am not sure if you are referring to a specific piece of media or context. Could you give me a little more information about what you mean '
- fact-premise-majel-plummer run 1: missing any of: \b(not aware|no record|don'?t think|didn'?t|did not|never|isn'?t|is not|wasn'?t|was not|not sure|don'?t know|no such|not familiar|not that i know|doesn'?t|does not|there is no|there's no|actually|hasn'?t|has not|haven'?t|have not)\b, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Amanda Plummer voiced the computer on the Enterprise in Star Trek.'
- fact-premise-majel-plummer run 2: missing any of: \b(not aware|no record|don'?t think|didn'?t|did not|never|isn'?t|is not|wasn'?t|was not|not sure|don'?t know|no such|not familiar|not that i know|doesn'?t|does not|there is no|there's no|actually|hasn'?t|has not|haven'?t|have not)\b, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Amanda Plummer voiced a computer on the star trek ship Enterprise.'
- fact-premise-majel-plummer run 3: missing any of: \b(not aware|no record|don'?t think|didn'?t|did not|never|isn'?t|is not|wasn'?t|was not|not sure|don'?t know|no such|not familiar|not that i know|doesn'?t|does not|there is no|there's no|actually|hasn'?t|has not|haven'?t|have not)\b, \b(not sure|not certain|unsure|don'?t know|do not know|don'?t recall|can'?t recall|cannot recall|not confident|might be wrong|may be wrong|could be wrong|not positive|double.check|don'?t remember|misremember\w*|(don'?t|do not) have (any |specific |that |the |enough |reliable )?(information|info|details|record)|no information|(i was|i'?m|i am) (incorrect|wrong|mistaken)|incorrect information|given you (the )?(wrong|incorrect)|whose name i (don'?t|do not) (have|know)|could you (tell|give)|can you tell me|(don'?t|do not) have a note|have no note|no note|(may|might) have (it|that|gotten it|been) wrong|(may|might) be mistaken)\b | heard: 'Amanda Plummer voiced the computer on the Enterprise-D.'

