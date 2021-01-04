import os
import re
import subprocess
from os.path import normpath
from typing import Optional, AnyStr, IO, Dict

from testing.framework.runners.code_runner import CodeRunner, create_src_file, get_resource_path
from testing.framework.runners.console_logger import ConsoleLogger
from anki.utils import isWin


def log_runtime_error(stderr: Optional[IO[AnyStr]], logger: ConsoleLogger):
    """
    Parses python error log and extracts the error information together with the correct line number
    :param stderr: processes stderror
    :param logger: console logger
    """
    line_number = None

    for line in stderr:
        line = line.decode('utf-8')
        if line_number is not None:
            line = str(line_number) + ': ' + line
        result = re.search(r'line (\d+)', line)
        if result is not None:
            line_number = int(result[1]) - 2
            continue
        else:
            line_number = None
        logger.log('<span class="error">Error</span> ' + line)


class PythonCodeRunner(CodeRunner):
    """
    This class runs source code written in python
    """
    UNIX_RUN_CMD = 'PYTHONPATH={}/libs/python {}/libs/python/python3 {}'
    WIN_RUN_CMD = 'set PYTHONPATH={}/libs/python && {}/libs/python/python3 {}'

    def _run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        Executes python code
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        """
        workdir, pythonsrc = create_src_file(src, 'test.py')
        resource_path = get_resource_path()
        # resource_path = '/opt/dev/dave8/anki'
        if isWin:
            run_cmd = self.WIN_RUN_CMD
        else:
            run_cmd = self.UNIX_RUN_CMD
        cmd = normpath(run_cmd.format(resource_path, resource_path, pythonsrc.name))
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.pid = proc.pid
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
        log_runtime_error(proc.stderr, logger)
