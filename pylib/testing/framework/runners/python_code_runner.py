import os
import subprocess

from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.console_logger import ConsoleLogger


class PythonCodeRunner(CodeRunner):
    RUN_CMD = '{}/libs/python/python3 {}'

    def run(self, src: str, logger: ConsoleLogger):
        workdir, pythonsrc = self._create_src_file(src, 'test.py')
        resource_path = os.environ['RESOURCEPATH']

        cmd = self.RUN_CMD.format(resource_path, pythonsrc.name)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
