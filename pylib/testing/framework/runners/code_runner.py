import os
import sys
import tempfile

from abc import ABC, abstractmethod
from typing import Dict

from testing.framework.runners.console_logger import ConsoleLogger
from anki.utils import isMac, isWin


def create_src_file(src, name):
    """
    Stores the source code provided into temporary file
    :param src: target source code
    :param name: name of the file
    :return: Tuple - the file's parent dir and the target file
    """
    workdir = tempfile.TemporaryDirectory()
    javasrc = open(workdir.name + '/' + name, 'w')
    javasrc.write(src)
    javasrc.close()
    return workdir, javasrc


def get_resource_path():
    """
    Returns the Resource's base path, depending on the current OS
    :return: the path of the Resources folder in the system
    """
    if isWin:
        result = sys._MEIPASS
    elif isMac:
        result = os.environ['RESOURCEPATH']
    else:
        raise Exception('not supported OS')

    return '"' + result + '"'


class CodeRunner(ABC):
    """
    Base class for all language specific code runners
    """

    def __init__(self):
        self.pid = None

    def run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        Submits source code for execution
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        """
        if self.pid is not None:
            raise Exception('Another test is already running')
        try:
            self._run(src, logger, messages)
        finally:
            self.stop()

    @abstractmethod
    def _run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        This method is supposed to be overriden in the sub-types, it should contain logic
        of compiling and executing the source code provided
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        :return: void
        """
        pass

    def stop(self):
        """
        Stops the running process abnormally (if pid exists)
        """
        if self.pid is not None:
            try:
                os.kill(self.pid, 9)
            except:
                pass
            finally:
                self.pid = None
