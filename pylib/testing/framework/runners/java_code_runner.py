import os
import subprocess

from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.console_logger import ConsoleLogger


def strip_compile_error(error, file_name):
    lines = error.split(file_name)
    text = '<span class="error">Compilation Error:</span>\n'
    for line in lines[1:]:
        splitted = line.split(':')
        line_number = int(splitted[1]) - 20
        text += str(line_number) + ':' + ''.join(splitted[2:])
    return text.replace('\n', '<br>')


class JavaCodeRunner(CodeRunner):
    COMPILE_CMD = '{}/libs/jdk/bin/javac {} -cp {}/libs/jdk/lib/java.jar'
    RUN_CMD = '{}/libs/jdk/bin/java -classpath {}:{}/libs/jdk/lib/java.jar {}'
    CLASS_NAME = 'Solution'
    PKG_NAME = 'test_engine'
    pid = None

    def _run(self, src: str, logger: ConsoleLogger, compilation_error_template: str):
        workdir, javasrc = self._create_src_file(src, self.CLASS_NAME + '.java')
        resource_path = os.environ['RESOURCEPATH']
        # resource_path = '/opt/dev/dave8/anki/testing'
        logger.log('Compiling...')

        cmd = self.COMPILE_CMD.format(resource_path, javasrc.name, resource_path)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.pid = proc.pid
        out, err = proc.communicate()
        if len(err) != 0:
            logger.log('<span class="error">' + strip_compile_error(err.decode('utf-8'), javasrc.name) + '</span>')
        if len(err) > 0:
            return err

        logger.log('Running Tests...')
        cmd = self.RUN_CMD.format(resource_path, workdir.name, resource_path, self.CLASS_NAME)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.pid = proc.pid
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
        for error in proc.stderr:
            logger.log('<span class="error">error:</span>' + error.decode("utf-8"))

