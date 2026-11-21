#!/usr/bin/env python3
"""Bake a reference clip into the stand-in Pocket TTS encoder the Android app ships.

The reference encoder in the sherpa-onnx Pocket TTS export was traced with a one second
input and returns the first 13 frames of any clip, so cloning on the phone loses the voice.
This script runs the full Mimi encoder from mlx-audio (the same path the desktop
ships-computer demo uses) over the whole clip and writes the resulting conditioning as a
constant ONNX graph with the export's encoder interface: input "audio" [1, 1, audio_len],
output "latents" [1, frames, 1024]. sherpa-onnx runs it in place of encoder.onnx and the
rest of the model consumes the conditioning unchanged.

Needs Apple silicon with mlx-audio and onnx installed, for example from the ships-computer
checkout: `uv run --with onnx python scripts/bake-voice-conditioning.py caro_davy.wav \
    core/src/androidMain/assets/voices/caro-davy-conditioning.onnx`. A personal voice goes to the
gitignored core/src/androidMain/assets/voices/local/conditioning.onnx instead.
"""

import sys

import numpy as np
import onnx
from mlx_audio.tts.utils import load_model
from onnx import TensorProto, helper, numpy_helper

MODEL = "mlx-community/pocket-tts"
OPSET = 17
IR_VERSION = 8


def main(clip, out):
    model = load_model(MODEL)
    conditioning = np.array(model._encode_audio(model._load_audio(clip))).astype(np.float32)
    const = numpy_helper.from_array(conditioning, name="voice_conditioning")
    audio = helper.make_tensor_value_info("audio", TensorProto.FLOAT, [1, 1, "audio_len"])
    latents = helper.make_tensor_value_info("latents", TensorProto.FLOAT, list(conditioning.shape))
    node = helper.make_node("Identity", inputs=["voice_conditioning"], outputs=["latents"])
    graph = helper.make_graph([node], "baked_voice", [audio], [latents], initializer=[const])
    onnx_model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", OPSET)], producer_name="gemma-voice")
    onnx_model.ir_version = IR_VERSION
    onnx.checker.check_model(onnx_model)
    onnx.save(onnx_model, out)
    print(f"{conditioning.shape[1]} frames of conditioning written to {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
