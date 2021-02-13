import re
import subprocess
from os.path import normpath
from typing import Optional, AnyStr, IO, Dict

from testing.framework.runners.code_runner import CodeRunner, create_src_file, get_resource_path, get_code_offset
from testing.framework.runners.console_logger import ConsoleLogger
from testing.framework.langs.python.python_test_suite_gen import PYTHON_USER_SRC_START_MARKER
from anki.utils import isWin


def log_runtime_error(stderr: Optional[IO[AnyStr]], solution_offset: int, logger: ConsoleLogger) -> bool:
    """
    Parses python error log and extracts the error information together with the correct line number
    :param stderr: processes stderror
    :param solution_offset: number of lines preceding solution src
    :param logger: console logger
    :return bool: has errors or not
    """
    line_number = None
    has_error = False

    for line in stderr:
        line = line.decode('utf-8')
        if line_number is not None:
            line = str(line_number) + ': ' + line
        result = re.search(r'line (\d+)', line)
        if result is not None:
            line_number = int(result[1]) - solution_offset
            continue
        else:
            line_number = None
        logger.log("<span class='failed'>Error</span> " + line)
        has_error = True

    return has_error


class PythonCodeRunner(CodeRunner):
    """
    This class runs source code written in python
    """
    UNIX_RUN_CMD = 'PYTHONPATH={}/libs/python/3.9/lib/python3.9:{}/libs/python/3.9/lib/python3.9/lib-dynload ' \
                   '{}/libs/python/3.9/bin/python3 -u {} '
    WIN_RUN_CMD = 'set PYTHONPATH={}/libs/python && {}/libs/python/Python3.7/python.exe -u {}'

    def _run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        Executes python code
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        """
        workdir, pythonsrc = create_src_file(src, 'test.py')
        resource_path = get_resource_path()
        if isWin:
            cmd = self.WIN_RUN_CMD.format(resource_path, resource_path, pythonsrc.name)
        else:
            cmd = self.UNIX_RUN_CMD.format(resource_path, resource_path, resource_path, pythonsrc.name)
        cmd = normpath(cmd)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self._pid = proc.pid
        for line in proc.stdout:
            if not self._set_result(line.decode("utf-8"), logger, messages):
                return False

        solution_offset = get_code_offset(src, PYTHON_USER_SRC_START_MARKER)
        has_error = log_runtime_error(proc.stderr, solution_offset, logger)
        return not has_error
