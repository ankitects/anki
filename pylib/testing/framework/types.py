# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
AnkiCode Types
"""

import re
import tempfile
from typing import TextIO, Any, Optional


class Arg:
    """
    Type for storing a functions arguments information, consisting of:
       - name
       - type
    """

    def __init__(self, arg_name: str, arg_type: str):
        self.name = arg_name
        self.type = arg_type

    def __eq__(self, other):
        if isinstance(other, Arg):
            return other.type == self.type and other.name == self.name
        return False


class ConverterFn:
    """
    Represents a language specific type-conversion function:
       - name (auto-generated, using self-incrementing counter)
       - source code
       - argument's type (it accepts always 1 argument)
       - argument's name
       - return type
    """

    converter_counter = 0

    def __init__(self, prop_name: str, src: str, arg_type: str, ret_type: str = ''):
        ConverterFn.converter_counter += 1
        self.fn_name = 'converter' + str(self.converter_counter)
        self.prop_name = prop_name
        self.arg_type = arg_type
        self.arg_name = 'value'
        self.ret_type = ret_type
        self.src = src

    def __eq__(self, other):
        if isinstance(other, ConverterFn):
            return re.sub(r'\s', '', other.src) == re.sub(r'\s', '', self.src) \
                   and other.prop_name == self.prop_name \
                   and other.arg_type == self.arg_type \
                   and other.ret_type == self.ret_type
        else:
            return False

    def __repr__(self):
        return "fn name: " + self.fn_name + " " + \
               "arg_type: " + self.arg_type + " " + \
               "ret_type: " + self.ret_type + " " + \
               "prop name: " + self.prop_name + " " + \
               "src: " + self.src

    @classmethod
    def reset_counter(cls):
        """
        This method is used only in unit-tests, to fix function names
        """
        cls.converter_counter = 0


class TestSuiteExecOpts:
    """
    Defines additional options which must be applied during tests execution
        - ignore_order - if order of results must be ignored ([1,2,3] and [2,3,1] will be equal if ignore_order
                         is set to True)
        TODO: add execution time constraints
    """

    def __init__(self, opts: str):
        self.ignore_order = True

        if opts:
            opts_dict = {}
            for item in opts.split(','):
                kv = item.split('=')
                opts_dict[kv[0].strip()] = kv[1].strip()
            if 'ignore_order' in opts_dict:
                self.ignore_order = opts_dict['ignore_order'] == 'True'


class TestSuite:
    """
    Test Suite is related a Quiz Card, it contains the following information:
        - description - a quiz description, will be displayed as top comment in the solution's template
        - fn_name - a solution's function name
    """
    description: str
    fn_name: str


class SrcFile:
    """
    Holds file path, its parent directory and the source code
    """

    def __init__(self, text: str, file: TextIO, directory: tempfile.TemporaryDirectory):
        self.text = text
        self.file = file
        self.directory = directory


class TestResponse:
    """
    Defines format of test response
    """

    def __init__(self, duration: int, result: Optional[Any] = None):
        self.result = result
        self.duration = duration

    def __eq__(self, other):
        """Overrides the default implementation"""
        if isinstance(other, TestResponse):
            return self.result == other.result and self.duration == other.duration
        return False
