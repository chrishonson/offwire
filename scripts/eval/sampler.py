"""The app's sampling settings, shared by the LiteRT-LM and hosted runners. No dependencies, so a cloud session
can import it without litert_lm. GV_TEMPERATURE overrides the temperature for an experiment."""
import os

SAMPLER = dict(top_k=64, top_p=0.95, temperature=float(os.environ.get("GV_TEMPERATURE", "1.0")))
