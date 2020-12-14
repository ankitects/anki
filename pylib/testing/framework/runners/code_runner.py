import os
import tempfile
import multiprocessing as mp

from abc import ABC, abstractmethod
from typing import Dict

from testing.framework.runners.console_logger import ConsoleLogger


class CodeRunner(ABC):

    def __init__(self):
        self.pid = None

    def _create_src_file(self, src, name):
        workdir = tempfile.TemporaryDirectory()
        javasrc = open(workdir.name + '/' + name, 'w')
        javasrc.write(src)
        javasrc.close()
        return workdir, javasrc

    def run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        if self.pid is not None:
            raise Exception('Another test is already running')
        try:
            self._run(src, logger, messages)
        finally:
            self.stop()

    @abstractmethod
    def _run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        pass

    def stop(self):
        if self.pid is not None:
            try:
                os.kill(self.pid, 9)
            except:
                pass
            finally:
                self.pid = None

