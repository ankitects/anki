# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
C++ Test Runner API Implementation
"""

from testing.framework.cpp.cpp_input_converter import CppInputConverter
from testing.framework.cpp.cpp_output_converter import CppOutputConverter
from testing.framework.string_utils import render_template
from testing.framework.test_suite_gen import TestSuiteGenerator, TestSuiteConverters
from testing.framework.types import TestSuite


class CppTestSuiteGenerator(TestSuiteGenerator):
    """
    Generates a test suite source code in C++.
    """

    def __init__(self):
        super().__init__(input_converter=CppInputConverter(), output_converter=CppOutputConverter())

    def get_imports(self):
        """
        :return: string containing C++ includes
        """
        return '''
            \t#include <vector>
            \t#include <array>
            \t#include "cpp_lib/jute.h"
            \t#include <functional>
            \t#include <stdexcept>
            \t#include <string>
            \t#include <chrono>
            \t#include <queue> 
            \t#include <set>
            \tusing namespace std;'''

    def get_testing_src(self, ts: TestSuite, converters: TestSuiteConverters, solution_src: str):
        """
        Generates a source code for an input test suite and a solution function
        :param ts: target test suite
        :param converters: list of type converters
        :param solution_src: source code containing solution
        :return: source code for a test suite
        """
        return render_template('''
            {{src}}
            {% for c in converters.all %}
                {{c.ret_type}} {{c.fn_name}}({{c.arg_type}} {{c.arg_name}}) {
                    {{c.src}}
                }
            {% endfor %}
            int main() {
            \tSolution solution;
            \tstd::string buf;
            \twhile (true) {
            \t\tstd::getline(std::cin, buf);
            \t\tjute::jValue row = jute::parser::parse(buf);
            \t\tauto started = std::chrono::high_resolution_clock::now();
            \t\t{{converters.result.ret_type}} result = solution.{{ts.fn_name}}(
                {% for converter in converters.args %}
                    \t\t{{converter.fn_name}}(row[{{loop.index0}}]){% if not loop.last %}, {% endif %}
                {% endfor %});
            \t\tauto done = std::chrono::high_resolution_clock::now();
            \t\tjute::jValue json_result = {{converters.output.fn_name}}(result);
            \t\tjute::jValue response;
            \t\tresponse.set_type(jute::JOBJECT);
            \t\tresponse.add_property("result", json_result);
            \t\tjute::jValue duration;
            \t\tduration.set_type(jute::JNUMBER);
            \t\tduration.set_string(
                std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(done-started).count()));
            \t\tresponse.add_property("duration", duration);
            \t\tstd::cout << response.to_string() << endl;
            \t}
            \treturn 0;
            }''', ts=ts, src=solution_src, converters=converters)
