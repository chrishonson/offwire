import os
from pathlib import Path

import pytest

from model import DEFAULT_MODEL_PATH, ask, load_engine

MODEL_PATH = Path(os.environ.get("GEMMA_MODEL", DEFAULT_MODEL_PATH))

pytestmark = [
    pytest.mark.model,
    pytest.mark.skipif(not MODEL_PATH.exists(), reason="model file missing"),
]


@pytest.fixture(scope="module")
def engine():
    return load_engine()


def test_names_drug_from_label_text(engine):
    reply = ask(engine, "Reply with the drug name only.",
                "LISINOPRIL 10MG TAB TAKE 1 TABLET BY MOUTH DAILY QTY 30")
    assert "lisinopril" in reply.lower()


def test_history_is_seen(engine):
    reply = ask(engine, "Answer in one word.", "What colour did I say?",
                history=[{"role": "user", "text": "My favourite colour is green."},
                         {"role": "assistant", "text": "Noted."}])
    assert "green" in reply.lower()


def test_image_is_seen(engine, tmp_path):
    import struct
    import zlib

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    size = 64
    rows = b"".join(b"\0" + b"\xff\x00\x00" * size for _ in range(size))
    png = tmp_path / "red.png"
    png.write_bytes(b"\x89PNG\r\n\x1a\n"
                    + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
                    + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))
    reply = ask(engine, "Answer in one word.", "What colour is the image?", image=png)
    assert "red" in reply.lower()
