import os
import subprocess

from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.console_logger import ConsoleLogger


class PythonCodeRunner(CodeRunner):
    # RUN_CMD = '{}/libs/python/python3 {}'
    RUN_CMD = '{}/libs/python/python3 {}'
    # todo: uncomment it for debug
    # DEBUG_RUN_CMD = 'PYTHONPATH=/opt/dev/dave8/anki /opt/dev/dave8/anki/pyenv/bin/python {}'

    def run(self, src: str, logger: ConsoleLogger, compilation_error_template: str):
        workdir, pythonsrc = self._create_src_file(src, 'test.py')
        resource_path = os.environ['RESOURCEPATH']

        # todo: uncomment it for debug
        # cmd = self.RUN_CMD.format(pythonsrc.name)
        cmd = self.RUN_CMD.format(resource_path, pythonsrc.name)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
