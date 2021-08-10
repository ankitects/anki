# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Java Test Runner API Implementation
"""
from testing.framework.string_utils import get_line_number_prefix
from testing.framework.test_runner import TestRunner, LIBS_FOLDER
from testing.framework.types import SrcFile


class JavaTestRunner(TestRunner):
    """
    Executes Java test code, processes STDERR
    """

    LIBS = [
        f'/{LIBS_FOLDER}/java/lib/jackson-databind-2.11.3.jar',
        f'/{LIBS_FOLDER}/java/lib/jackson-core-2.11.3.jar',
        f'/{LIBS_FOLDER}/java/lib/jackson-annotations-2.11.3.jar'
    ]

    def get_src_file_name(self) -> str:
        """
        :return: Name of the source file
        """
        return 'Solution.java'

    def get_run_cmd(self, src_file: SrcFile, resource_path: str, is_win: bool) -> str:
        """
        Builds a java execute command.

        :param src_file: target source file containing a test suite
        :param resource_path: AnkiCode resource path
        :param is_win: True - if windows, False - if Unix/MacOS
        :return: shell command to execute a test suite
        """
        class_paths = (';' if is_win else ':').join([f'{resource_path}{path}' for path in self.LIBS] +
                                                    [src_file.directory.name])
        return f'{resource_path}/{LIBS_FOLDER}/java/bin/java -Xss10m -cp {class_paths} Runner'

    def get_compile_cmd(self, src_file: SrcFile, resource_path: str, is_win: bool) -> str:
        """
        Builds a java compile command.

        :param src_file: target source file containing a test suite
        :param resource_path: AnkiCode resource path
        :param is_win: True - if windows, False - if Unix/MacOS
        :return: shell command to compile a source file
        """
        class_paths = (';' if is_win else ':').join([f'{resource_path}{path}' for path in self.LIBS])
        return f'{resource_path}/{LIBS_FOLDER}/java/bin/javac -cp {class_paths} {src_file.file.name}'

    def get_error_message(self, error: str, file_name: str, code_offset: int) -> str:
        """
        Processes STDERR from JVM compiler, sets a correct line number with error in the solution src
        :param error: decoded STDERR
        :param file_name: target source file name
        :param code_offset: user solution code offset
        :return: error message
        """
        text = ''
        for error_line in error.split('\n'):
            if error_line.strip() == '':
                continue
            lines = error_line.split(file_name)
            if lines[0].startswith('Note:'):
                continue
            if file_name in error_line:
                for line in lines[1:]:
                    splitted = line.split(':')
                    line_prefix = get_line_number_prefix(splitted[1], code_offset)
                    text += (line_prefix + ' ' if line_prefix else '') + ''.join(splitted[2:])
                text += '\n'
            else:
                text += error_line + '\n'
        return text
