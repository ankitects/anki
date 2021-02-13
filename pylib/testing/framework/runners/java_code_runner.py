import subprocess
from os.path import normpath
from typing import Dict

from testing.framework.runners.code_runner import CodeRunner, create_src_file, get_resource_path, get_code_offset
from testing.framework.runners.console_logger import ConsoleLogger
from testing.framework.langs.java.java_test_suite_gen import JAVA_USER_SRC_START_MARKER
from anki.utils import isWin


def strip_compile_error(error: str, file_name: str, solution_offset: int):
    """
    Parses java compile error log and extract the error information together with the correct line number
    :param error: target error
    :param file_name: target source code file name
    :param solution_offset: number of lines preceding solution src
    :return: text containing error with the correct line number
    """
    lines = error.split(file_name)
    text = '<span class="error">Compilation Error:</span>\n'
    for line in lines[1:]:
        splitted = line.split(':')
        line_number = int(splitted[1]) - solution_offset
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
    PKG_NAME = 'ankitest'
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
        logger.log("<span class='info'>Compiling...</span>")

        solution_offset = get_code_offset(src, JAVA_USER_SRC_START_MARKER)

        cmd = normpath(self.COMPILE_CMD.format(resource_path, javasrc.name, resource_path))
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self._pid = proc.pid
        out, error = proc.communicate()
        if len(error) != 0:
            error_text = strip_compile_error(error.decode('utf-8'), javasrc.name, solution_offset)
            logger.log("<span class='failed'>" + error_text + '</span>')
        if len(error) > 0:
            return False
        if isWin:
            cmd = self.WIN_RUN_CMD
        else:
            cmd = self.UNIX_RUN_CMD
        cmd = normpath(cmd.format(resource_path, workdir.name, resource_path, self.CLASS_NAME))
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self._pid = proc.pid
        for line in proc.stdout:
            if not self._set_result(line.decode("utf-8"), logger, messages):
                return False
        for error in proc.stderr:
            logger.log("<span class='failed'>error:</span>" + error.decode("utf-8"))
            return False
        return True
