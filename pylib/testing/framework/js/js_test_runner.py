# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
JS Test Runner API Implementation
"""

from testing.framework.string_utils import get_line_number_prefix
from testing.framework.test_runner import TestRunner
from testing.framework.types import SrcFile


class JsTestRunner(TestRunner):
    """
    Executes JS test code, processes STDERR
    """

    def get_src_file_name(self) -> str:
        """
        :return: Name of the source file
        """
        return 'test.js'

    def get_compile_cmd(self, src_file: SrcFile, resource_path: str, is_win: bool) -> str:
        """
        No compilation
        """
        pass

    def get_run_cmd(self, src_file: SrcFile, resource_path: str, is_win: bool) -> str:
        """
        Builds shell command to execute source file using embedded Node interpreter

        :param src_file: target source file to execute
        :param resource_path: AnkiCode resource path
        :param is_win: True - if windows, False if Unix/MacOS
        :return: shell command to execute a source file
        """
        if is_win:
            return f'{resource_path}/libs/node/node.exe {src_file.file.name}'
        else:
            return f'cd {resource_path}/libs/node && {resource_path}/libs/node/bin/node {src_file.file.name}'

    def get_error_message(self, error: str, file_name: str, code_offset: int) -> str:
        """
        Processes STDERR from node process, sets a correct line number with error in the solution src
        :param error: decoded STDERR
        :param file_name: target source file name
        :param code_offset: user solution code offset
        :return: error message
        """
        text = ''
        for error_line in error.split('\n'):
            lines = error_line.split(file_name)
            if error_line.strip().startswith('at '):
                continue
            if len(lines) > 1:
                for line in lines[1:]:
                    splitted = line.split(':')
                    line_prefix = get_line_number_prefix(splitted[1], code_offset)
                    text += (line_prefix + ' ' if line_prefix else '') + ''.join(splitted[2:])
            else:
                text += error_line + '\n'
        return text
