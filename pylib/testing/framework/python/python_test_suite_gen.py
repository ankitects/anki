# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Python implementation of the Test Suite Generator API
"""

from testing.framework.python.python_input_converter import PythonInputConverter
from testing.framework.python.python_output_converter import PythonOutputConverter
from testing.framework.test_suite_gen import TestSuiteGenerator, TestSuiteConverters
from testing.framework.types import TestSuite
from testing.framework.string_utils import render_template
from testing.framework.string_utils import to_snake_case


class PythonTestSuiteGenerator(TestSuiteGenerator):
    """
    Generates a test suite source code in Python.
    """

    def __init__(self):
        super().__init__(line_comment_char='#',
                         input_converter=PythonInputConverter(),
                         output_converter=PythonOutputConverter())

    def get_imports(self) -> str:
        """
        :return: string containing Python imports
        """
        return '''
            import datetime
            import json
            from typing import *'''

    def get_testing_src(self, ts: TestSuite, converters: TestSuiteConverters, solution_src: str) -> str:
        """
        Generates a source code for an input test suite and a solution function
        :param ts: target test suite
        :param converters: list of type converters
        :param solution_src: source code containing solution
        :return: source code for a test suite
        """
        return solution_src + render_template('''
            {% for converter in converters.all %}
            def {{converter.fn_name}}({{converter.arg_name}}) -> {{converter.ret_type}}:
            {{converter.src}}
            {% endfor %}
            while True:
            \tline = input()
            \tvalues = json.loads(line)
            \targs = []
            {% for c in converters.args %}
            \targs.append({{c.fn_name}}(values[{{loop.index0}}]))
            {% endfor %}
            \tstart = datetime.datetime.now()
            \tresult = {{fn_name}}(*args)
            \tend = datetime.datetime.now()
            \ttime_diff = (end - start)
            \tduration = (time_diff.days * 86400000) + (time_diff.seconds * 1000) + (time_diff.microseconds / 1000)
            \tprint(json.dumps({
            \t\t'result': {{converters.output.fn_name}}(result),
            \t\t'duration': duration }, default=lambda obj: obj.__dict__))
            ''', ts=ts, converters=converters, fn_name=to_snake_case(ts.fn_name), retab=True)
