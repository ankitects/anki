# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: skip-file

"""
PyQt5-only audio code
"""

import wave
from concurrent.futures import Future
from typing import cast

import aqt

from . import *  # isort:skip
from ..sound import Recorder  # isort:skip
from ..utils import showWarning  # isort:skip


class QtAudioInputRecorder(Recorder):
    def __init__(self, output_path: str, mw: aqt.AnkiQt, parent: QWidget) -> None:
        super().__init__(output_path)

        self.mw = mw
        self._parent = parent

        from PyQt5.QtMultimedia import (  # type: ignore
            QAudioDeviceInfo,
            QAudioFormat,
            QAudioInput,
        )

        format = QAudioFormat()
        format.setChannelCount(1)
        format.setSampleRate(44100)
        format.setSampleSize(16)
        format.setCodec("audio/pcm")
        format.setByteOrder(QAudioFormat.LittleEndian)
        format.setSampleType(QAudioFormat.SignedInt)

        device = QAudioDeviceInfo.defaultInputDevice()
        if not device.isFormatSupported(format):
            format = device.nearestFormat(format)
            print("format changed")
            print("channels", format.channelCount())
            print("rate", format.sampleRate())
            print("size", format.sampleSize())
        self._format = format

        self._audio_input = QAudioInput(device, format, parent)

    def start(self, on_done: Callable[[], None]) -> None:
        self._iodevice = self._audio_input.start()
        self._buffer = bytearray()
        qconnect(self._iodevice.readyRead, self._on_read_ready)
        super().start(on_done)

    def _on_read_ready(self) -> None:
        self._buffer.extend(cast(bytes, self._iodevice.readAll()))

    def stop(self, on_done: Callable[[str], None]) -> None:
        def on_stop_timer() -> None:
            # read anything remaining in buffer & stop
            self._on_read_ready()
            self._audio_input.stop()

            if err := self._audio_input.error():
                showWarning(f"recording failed: {err}")
                return

            def write_file() -> None:
                # swallow the first 300ms to allow audio device to quiesce
                wait = int(44100 * self.STARTUP_DELAY)
                if len(self._buffer) <= wait:
                    return
                self._buffer = self._buffer[wait:]

                # write out the wave file
                wf = wave.open(self.output_path, "wb")
                wf.setnchannels(self._format.channelCount())
                wf.setsampwidth(self._format.sampleSize() // 8)
                wf.setframerate(self._format.sampleRate())
                wf.writeframes(self._buffer)
                wf.close()

            def and_then(fut: Future) -> None:
                fut.result()
                Recorder.stop(self, on_done)

            self.mw.taskman.run_in_background(write_file, and_then)

        # schedule the stop for half a second in the future,
        # to avoid truncating the end of the recording
        self._stop_timer = t = QTimer(self._parent)
        t.timeout.connect(on_stop_timer)  # type: ignore
        t.setSingleShot(True)
        t.start(500)
