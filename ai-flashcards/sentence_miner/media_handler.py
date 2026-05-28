"""Audio download (yt-dlp) and slicing (ffmpeg) helpers."""
from __future__ import annotations

import logging
import subprocess
import sys
from pathlib import Path

log = logging.getLogger(__name__)


def download_audio(url: str, output_dir: Path) -> Path:
    """Download the best audio stream from *url* into *output_dir*.

    Returns the path of the downloaded file.  Uses yt-dlp's ``%(id)s``
    template so the filename is always predictable.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    template = str(output_dir / "%(id)s.%(ext)s")
    cmd = [
        "yt-dlp",
        "--no-playlist",
        "--extract-audio",
        "--audio-format", "m4a",
        "--audio-quality", "0",
        "--output", template,
        "--quiet",
        url,
    ]
    log.info("Downloading audio: %s", url)
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"[ERROR] yt-dlp failed:\n{result.stderr}", file=sys.stderr)
        raise RuntimeError("yt-dlp download failed")

    # Find the single file written by yt-dlp
    files = list(output_dir.glob("*.m4a"))
    if not files:
        # yt-dlp may have kept a different extension despite --audio-format m4a
        files = [f for f in output_dir.iterdir() if f.is_file()]
    if not files:
        raise RuntimeError("yt-dlp produced no output file")
    return max(files, key=lambda p: p.stat().st_mtime)


def slice_audio(source: Path, start: float, duration: float, output: Path) -> Path:
    """Copy *duration* seconds of *source* starting at *start* into *output*.

    Uses ``-c copy`` (no re-encoding) for speed.  Input-side ``-ss`` is
    used for fast seeking; a tiny seek inaccuracy (up to one audio frame)
    is acceptable for sentence-level clips.
    """
    output.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg",
        "-y",                       # overwrite without prompting
        "-i", str(source),
        "-ss", str(start),          # output-side seeking for sample-accurate cuts
        "-t", str(duration),
        "-c:a", "aac",              # re-encode; stream-copy with AAC can corrupt clips
        "-loglevel", "error",
        str(output),
    ]
    log.debug("Slicing %s [%.2f + %.2fs] → %s", source.name, start, duration, output.name)
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg slice failed: {result.stderr.strip()}")
    return output
