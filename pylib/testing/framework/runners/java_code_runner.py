import os
import subprocess
from os.path import normpath
from typing import Dict

from testing.framework.runners.code_runner import CodeRunner, create_src_file, get_resource_path
from testing.framework.runners.console_logger import ConsoleLogger
from anki.utils import isWin


def strip_compile_error(error, file_name):
    """
    Parses java compile error log and extract the error information together with the correct line number
    :param error: target error
    :param file_name: target source code file name
    :return: text containing error with the correct line number
    """
    lines = error.split(file_name)
    text = '<span class="error">Compilation Error:</span>\n'
    for line in lines[1:]:
        splitted = line.split(':')
        line_number = int(splitted[1]) - 20
        text += str(line_number) + ':' + ''.join(splitted[2:])
    return text.replace('\n', '<br>')


class JavaCodeRunner(CodeRunner):
    """
    This class compiles and runs source code written in java
    """
    COMPILE_CMD = '{}/libs/jdk/bin/javac {} -cp {}/libs/jdk/lib/java.jar'
    WIN_RUN_CMD = '{}/libs/jdk/bin/java -Xss10m -classpath {};{}/libs/jdk/lib/java.jar {}'
    UNIX_RUN_CMD = '{}/libs/jdk/bin/java -Xss10m -classpath {}:{}/libs/jdk/lib/java.jar {}'
    CLASS_NAME = 'Solution'
    PKG_NAME = 'test_engine'
    pid = None

    def _run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        Compiles and executes java code
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        """
        workdir, javasrc = create_src_file(src, self.CLASS_NAME + '.java')
        resource_path = get_resource_path()
        # resource_path = '/opt/dev/dave8/anki/testing'
        logger.log('Compiling...')

        cmd = normpath(self.COMPILE_CMD.format(resource_path, javasrc.name, resource_path))
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.pid = proc.pid
        out, err = proc.communicate()
        if len(err) != 0:
            logger.log('<span class="error">' + strip_compile_error(err.decode('utf-8'), javasrc.name) + '</span>')
        if len(err) > 0:
            return err

        logger.log('Running Tests...')
        if isWin:
            cmd = self.WIN_RUN_CMD
        else:
            cmd = self.UNIX_RUN_CMD
        cmd = normpath(cmd.format(resource_path, workdir.name, resource_path, self.CLASS_NAME))
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.pid = proc.pid
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
        for error in proc.stderr:
            logger.log('<span class="error">error:</span>' + error.decode("utf-8"))
