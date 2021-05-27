# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Test Runner API
"""

import json
import os
import subprocess
import sys
import tempfile
import re
import psutil
import time

from deepdiff import DeepDiff
from abc import abstractmethod, ABC
from os.path import normpath
from typing import List, Optional, Tuple

from testing.framework.io_utils import non_blocking_readlines
from testing.framework.test_suite_gen import START_USER_SRC_MARKER
from testing.framework.types import SrcFile, TestResponse, TestSuiteExecOpts
from testing.framework.console_logger import ConsoleLogger
import pathlib

isMac = sys.platform.startswith("darwin")
isWin = sys.platform.startswith("win32")
isLin = sys.platform.startswith("linux")

ERROR_LINE_OUTPUT_LIMIT = 100


def create_src_file(src: str, name: str) -> SrcFile:
    """
    Stores the source code provided into temporary file
    :param src: target source code
    :param name: name of the file
    :return: Tuple - the file's parent dir and the target file
    """
    workdir = tempfile.TemporaryDirectory()
    src_file = open(os.path.join(workdir.name, name), 'w')
    src_file.write(src)
    src_file.close()
    return SrcFile(src, src_file, workdir)


def get_resource_path():
    """
    Returns the Resource's base path, depending on the current OS
    :return: the path of the Resources folder in the system
    """
    if not isMac and not isWin and not isLin:
        raise OSError('Unsupported OS')
    if isMac and 'RESOURCEPATH' in os.environ:
        result = os.environ['RESOURCEPATH']
    elif (isWin or isLin) and getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
        result = sys._MEIPASS
    else:
        result = os.path.join(pathlib.Path(__file__).parents[3], 'testing')
    return '"' + result + '"'


def compare(obj1, obj2, ignore_order=True) -> bool:
    """
    Performs deep difference between two objects
    :return True - if objects are equal, False otherwise
    """
    return DeepDiff(obj1, obj2, ignore_order=ignore_order, significant_digits=4) == {}


def get_code_offset(src: str, user_src_start_marker: str) -> int:
    """
    Returns number of lines which precede solution src
    :param src: solution src
    :param user_src_start_marker: begin of solution src marker
    """
    start_src_index = src.index(user_src_start_marker)
    return len(src[:start_src_index].split('\n'))


def parse_response(line: str) -> Tuple[Optional[TestResponse], str]:
    """
    Parses a line from stdout, extracts response JSON (if presented) and other text
    :param line: target line
    :return: tuple of parsed test response and a message
    """
    buf = ''
    line = line.strip()
    end = len(line)
    resp = None
    if not line.endswith('}'):
        return None, line
    for i in range(0, len(line)):
        if line[i] == '{':
            try:
                resp = json.loads(line[i:end], object_hook=lambda d: TestResponse(**d))
            except Exception:
                pass
            if resp and isinstance(resp, TestResponse) and resp.duration is not None:
                return resp, buf
        buf += line[i]
    return None, buf


class TestRunner(ABC):
    """
    Base class for language specific test suite runners:
        -submits tests for execution
        -performs error handling
        -parses a test's results and compares them with expected value
    """

    def __init__(self):
        self.pid = None
        self.stopped = False

    def run(self, src_code: str, test_cases: List[str], opts: TestSuiteExecOpts, logger: ConsoleLogger):
        """
        Submits a source code for execution
        :param src_code: source code to run
        :param test_cases: text rows containing a testing data
        :param opts: options which control tests execution
        :param logger: console logger
        """
        if self.pid is not None:
            raise Exception('Another test is already running ' + str(self.pid))

        resource_path = get_resource_path()
        src_file = create_src_file(src_code, self.get_src_file_name())
        test_logger = logger.get_testing_logger(len(test_cases) - 1)
        self.stopped = False

        try:
            compile_cmd = self.get_compile_cmd(src_file, resource_path, isWin)
            if compile_cmd:
                logger.info('Compiling...')
                compile_cmd = normpath(compile_cmd)
                proc = subprocess.Popen(compile_cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, text=True)
                self.pid = proc.pid
                stdout, stderr = proc.communicate()
                if self.check_for_errors(stderr, src_file, logger):
                    return

            logger.info('Running tests...<br/>')
            run_cmd = self.get_run_cmd(src_file, resource_path, isWin)
            run_cmd = normpath(run_cmd)

            proc = subprocess.Popen(run_cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, text=True)
            self.pid = proc.pid
            line_iterator = non_blocking_readlines(proc.stdout)
            error_iterator = non_blocking_readlines(proc.stderr)

            for idx, tc in enumerate(test_cases[1:], start=1):
                splt = re.split('(?<!\\\\);', tc)
                expected_val = json.loads(splt[-1])
                args = '[' + ','.join(splt[:-1]) + ']'

                proc.stdin.write(args + '\n')
                proc.stdin.flush()
                tst_resp: Optional[TestResponse] = None
                error = ''
                while tst_resp is None and not self.stopped:
                    for i, line in enumerate(error_iterator):
                        if not line or self.stopped:
                            break
                        time.sleep(0.2)  # short sleep not to overload CPU
                        error += line.decode('utf-8')
                        if i > ERROR_LINE_OUTPUT_LIMIT:
                            break
                    if error and self.check_for_errors(error, src_file, logger):
                        return
                    error = ''
                    for line in line_iterator:
                        if self.stopped:
                            break
                        if not line:
                            break
                        tst_resp, msg = parse_response(line.decode('utf-8'))
                        if msg:
                            logger.info(msg)
                if self.stopped:
                    test_logger.cancel()
                    return
                assert tst_resp is not None
                if compare(tst_resp.result, expected_val, opts.ignore_order):
                    test_logger.passed(idx, tst_resp.duration)
                else:
                    test_logger.fail(idx, args, expected_val, tst_resp.result)
                    return
            if self.stopped:
                test_logger.cancel()
            else:
                test_logger.log('<br/>All tests <span class="passed">PASSED</span><br/><br/>')
        except BrokenPipeError:
            if self.stopped:
                test_logger.cancel()
        finally:
            self.kill()

    @abstractmethod
    def get_src_file_name(self) -> str:
        """
        Name of a file to be executed
        """
        pass

    @abstractmethod
    def get_run_cmd(self, src_file: SrcFile, resource_path: str, is_win: bool) -> str:
        """
        test's run command
        :param src_file: tatget file to be executed
        :param resource_path: path containing resource files
        :param is_win: platform is windows if true (unix/osx if false)
        """
        pass

    @abstractmethod
    def get_compile_cmd(self, src_file: SrcFile, resource_path: str, is_win: bool) -> str:
        """
        test's compile command
        :param src_file: tatget file to be executed
        :param resource_path: path containing resource files
        :param is_win: platform is windows if true (unix/osx if false)
        """
        pass

    @abstractmethod
    def get_error_message(self, error: str, file_name: str, code_offset: int) -> str:
        """
        Contains logic of parsing stderr, to distinguish between errors and warnings
        and pre-process error messages (setting correct line number, strip file name, etc...)
        :param error: target error message from compiler or interpreter
        :param file_name: a test's file name
        :param code_offset: line offset used to calculate correct line number of an error
        """
        pass

    def check_for_errors(self, error, src_file: SrcFile, logger: ConsoleLogger) -> bool:
        """
        Checks if there are error messages in stderr, if there are errors - logs them to the console
        :param error: target error stream
        :param src_file: target source file
        :param logger: console logger
        :return: true/false - has errors or not
        """
        if not error:
            return False
        offset = get_code_offset(src_file.text, START_USER_SRC_MARKER)
        error_msg = self.get_error_message(error, src_file.file.name, offset)
        if error_msg:
            error_msg = re.sub('\n+', '<br>', error_msg)
            logger.error(error_msg)
            return True
        return False

    def kill(self):
        """
        Stop currently executing processes
        """
        if self.pid is not None:
            try:
                self.stopped = True
                parent = psutil.Process(self.pid)
                children = parent.children(recursive=True)
                for child in children:
                    child.kill()
                psutil.wait_procs(children, timeout=5)
                parent.kill()
            except:
                pass
            finally:
                self.pid = None
