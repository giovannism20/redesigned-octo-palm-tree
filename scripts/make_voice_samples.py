#!/usr/bin/env python3
"""Builds the short voice samples bundled in the app (app/src/main/assets/voices/samples/).

Input: the directory of the offline voice evaluation (docs/design/voices-evaluation.md), which holds
one WAV per voice rendered with sherpa-onnx 2.0.3 from the same text, and its report.json. Those
renders are not committed (they are generated artifacts); this script turns them into the samples
the Voices tab plays before a voice is downloaded:

- only the first sentence is kept (it ends where the report's first generated chunk ends);
- a 20 Hz high-pass removes the DC offset of Kokoro voices, as the app's engine does;
- each voice gets the gain that brings it to the evaluation's common speech level, as the app's
  engine applies it, so the samples sound like the voices will in the app;
- the end fades out over 40 ms, and the result is encoded as mono Ogg Opus at 32 kbit/s.

Usage: python3 -I scripts/make_voice_samples.py <evaluation dir> <output dir>
Needs numpy and an ffmpeg with libopus on the PATH.
"""

import json
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

# Evaluation voice id -> sample name in the app (the neural voice's key).
VOICES = {
    "kokoro-pf_dora": "dora",
    "kokoro-pm_alex": "alex",
    "kokoro-pm_santa": "santa",
    "piper-pt_BR-cadu-medium": "cadu",
    "piper-pt_BR-faber-medium": "faber",
    "piper-pt_BR-jeff-medium": "jeff",
    "piper-pt_BR-dii-high": "dii",
    "piper-pt_BR-miro-high": "miro",
    "piper-pt_BR-edresson-low": "edresson",
}
HIGH_PASS_HZ = 20.0
FADE_SECONDS = 0.04
PEAK_LIMIT = 0.98
BITRATE = "32k"


def read_wav(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path)) as wav:
        if wav.getnchannels() != 1 or wav.getsampwidth() != 2:
            raise ValueError(f"{path.name}: expected mono 16-bit PCM")
        rate = wav.getframerate()
        samples = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2")
    return samples.astype(np.float64) / 32768.0, rate


def high_pass(samples: np.ndarray, rate: int) -> np.ndarray:
    """One-pole DC blocker, the same filter as the app's NeuralAudio."""
    pole = 1.0 - 2.0 * np.pi * HIGH_PASS_HZ / rate
    out = np.empty_like(samples)
    previous_in = previous_out = 0.0
    for index, value in enumerate(samples):
        previous_out = value - previous_in + pole * previous_out
        previous_in = value
        out[index] = previous_out
    return out


def with_gain(samples: np.ndarray, gain_db: float) -> np.ndarray:
    gain = 10.0 ** (gain_db / 20.0)
    peak = float(np.max(np.abs(samples))) or 1.0
    return samples * min(gain, PEAK_LIMIT / peak)


def fade_out(samples: np.ndarray, rate: int) -> np.ndarray:
    length = min(len(samples), int(FADE_SECONDS * rate))
    samples = samples.copy()
    samples[len(samples) - length :] *= np.linspace(1.0, 0.0, length)
    return samples


def encode(samples: np.ndarray, rate: int, target: Path) -> None:
    pcm = (np.clip(samples, -1.0, 1.0) * 32767.0).astype("<i2")
    with tempfile.TemporaryDirectory() as scratch:
        source = Path(scratch) / "sample.wav"
        with wave.open(str(source), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(rate)
            wav.writeframes(pcm.tobytes())
        subprocess.run(
            [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
                "-ac", "1", "-ar", "24000", "-c:a", "libopus", "-b:a", BITRATE,
                "-application", "audio", str(target),
            ],
            check=True,
        )


def main(evaluation: Path, output: Path) -> None:
    report = json.loads((evaluation / "report.json").read_text(encoding="utf-8"))
    gains = {entry["voice_id"]: entry["gain_db"] for entry in report["blind_playlist"]["order"]}
    first_sentence = {
        voice["voice_id"]: voice["timing_cpu"]["threads_1"]["first_chunk_audio_s"] for voice in report["voices"]
    }
    output.mkdir(parents=True, exist_ok=True)
    for voice_id, name in VOICES.items():
        samples, rate = read_wav(evaluation / f"{voice_id}.wav")
        samples = samples[: int(round(first_sentence[voice_id] * rate))]
        samples = fade_out(with_gain(high_pass(samples, rate), gains[voice_id]), rate)
        target = output / f"{name}.ogg"
        encode(samples, rate, target)
        print(f"{target.name}: {len(samples) / rate:.2f} s, {target.stat().st_size} bytes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]))
