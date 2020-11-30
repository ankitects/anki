import re
import subprocess

from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.console_logger import ConsoleLogger

def log_runtime_error(error_log, logger):
    line_number = None
    for line in error_log:
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
    RUN_CMD = 'PYTHONPATH={} {}/pyenv/bin/python {}'

    def _run(self, src: str, logger: ConsoleLogger, compilation_error_template: str):
        workdir, pythonsrc = self._create_src_file(src, 'test.py')
        # resource_path = os.environ['RESOURCEPATH']
        resource_path = '/opt/dev/dave8/anki'

        cmd = self.RUN_CMD.format(resource_path, resource_path, pythonsrc.name)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.pid = proc.pid
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
        log_runtime_error(proc.stderr, logger)
