# test-import-check: skip (listen_loop is scripts/listen_loop.py, imported from this test's own directory)
import array

import pytest

from listen_loop import FLOOR_DB, audio_device_index, bar, dbfs, first_sustained, judge_barge_in, meter

LISTING = """[AVFoundation indev @ 0x1] AVFoundation video devices:
[AVFoundation indev @ 0x1] [0] FaceTime HD Camera
[AVFoundation indev @ 0x1] AVFoundation audio devices:
[AVFoundation indev @ 0x1] [0] eqMac
[AVFoundation indev @ 0x1] [1] MacBook Pro Microphone
"""


def test_full_scale_square_wave_is_zero_db_and_silence_is_the_floor():
    assert dbfs(array.array("h", [32767, -32767] * 50)) == pytest.approx(0, abs=0.01)
    assert dbfs(array.array("h", [0] * 100)) == FLOOR_DB
    assert dbfs(array.array("h")) == FLOOR_DB


def test_half_amplitude_is_six_db_down():
    assert dbfs(array.array("h", [16384, -16384] * 50)) == pytest.approx(-6.02, abs=0.01)


def test_a_reply_must_last_the_hold_time():
    levels = [-60, -30, -60, -30, -30, -30, -60]
    assert first_sustained(levels, -40, 3) == 3
    assert first_sustained(levels, -40, 4) is None


def test_finds_the_audio_device_not_the_camera_with_the_same_index():
    assert audio_device_index(LISTING, "MacBook Pro Microphone") == "1"
    assert audio_device_index(LISTING, "eqMac") == "0"


def test_an_unknown_device_names_the_ones_that_exist():
    with pytest.raises(SystemExit, match="MacBook Pro Microphone"):
        audio_device_index(LISTING, "USB Mic")


def test_bar_is_empty_at_the_quiet_level_and_full_well_above_it():
    assert bar(-60, quiet=-60, threshold=-48, width=10) == " " * 10
    assert bar(-20, quiet=-60, threshold=-48, width=10) == "#" * 10


def test_barge_in_passes_when_the_phone_goes_quiet_then_answers():
    levels = [-60, -60, -60, -60, -30, -30, -30]
    assert judge_barge_in(levels, -40, hold=3, gap=3) == ("stopped and answered", 0.4)


def test_barge_in_fails_when_the_old_reply_carries_on():
    levels = [-30, -30, -30, -30, -60]
    assert judge_barge_in(levels, -40, hold=3, gap=3) == ("kept talking", None)


def test_barge_in_notices_silence_after_stopping():
    assert judge_barge_in([-60] * 10, -40, hold=3, gap=3) == ("stopped, no answer", None)


def test_meter_stops_once_a_reply_has_run_long_enough():
    show = meter(quiet=-60, threshold=-48, stop_after=2, hold=3)
    stops = [show(level) for level in [-60, -30, -30, -30, -30, -30]]
    assert stops == [False, False, False, False, False, True]


def test_meter_without_stop_after_never_stops():
    show = meter(quiet=-60, threshold=-48)
    assert not any(show(-30) for _ in range(20))
