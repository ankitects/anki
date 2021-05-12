# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
JS implementation of the Test Suite Generator API
"""
from testing.framework.js.js_input_converter import JsInputConverter
from testing.framework.js.js_output_converter import JsOutputConverter
from testing.framework.string_utils import render_template
from testing.framework.test_suite_gen import TestSuiteGenerator
from testing.framework.types import TestSuite


class JsTestSuiteGenerator(TestSuiteGenerator):
    """
    Generates a test suite source code in Python.
    """

    def __init__(self):
        super().__init__(input_converter=JsInputConverter(), output_converter=JsOutputConverter())

    def get_imports(self):
        """
        :return: Empty imports fo JS
        """
        return ''

    def get_testing_src(self, ts: TestSuite, converters: TestSuiteGenerator, solution_src: str):
        """
        Generates a source code for an input test suite and a solution function
        :param ts: target test suite
        :param converters: list of type converters
        :param solution_src: source code containing solution
        :return: source code for a test suite
        """
        return render_template('''
            const fs = require('fs');
            const readline = require('readline');
            const rl = readline.createInterface({
              input: process.stdin,
              output: process.stdout
            });
            {% for converter in converters.all %}
            function {{converter.fn_name}}({{converter.arg_name}}) {
            {{converter.src}}
            }
            {% endfor %}
            {{solution_src}}
            rl.on('line', function(line) {
            \tconst start = new Date().getTime();
            \tconst cols = JSON.parse(line)
            \tconst args = []
            {% for converter in converters.args %}
            \targs.push({{converter.fn_name}}(cols[{{loop.index0}}]));
            {% endfor %}
            \tconst result = {{ts.fn_name}}(...args);
            \tconst end = new Date().getTime();
            \tconsole.log(JSON.stringify({
            \t\t'result': {{converters.output.fn_name}}(result),
            \t\t'duration': (end-start)
            \t}));
            });
            ''', ts=ts, converters=converters, solution_src=solution_src)
