# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Java implementation of the Test Suite Generator API
"""

from testing.framework.java.java_input_converter import JavaInputConverter
from testing.framework.java.java_output_converter import JavaOutputConverter
from testing.framework.string_utils import render_template
from testing.framework.test_suite_gen import TestSuiteGenerator, TestSuiteConverters
from testing.framework.types import TestSuite
from testing.framework.string_utils import to_camel_case


class JavaTestSuiteGenerator(TestSuiteGenerator):
    """
    Generates a test suite source code in Java.
    """

    def __init__(self):
        super().__init__(input_converter=JavaInputConverter(), output_converter=JavaOutputConverter())

    def get_imports(self):
        """
        :return: string containing Java imports
        """
        return '''
            import java.io.File;
            import java.io.IOException;
            import java.util.stream.Collectors;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.concurrent.*;
            import java.util.*;
            import java.util.stream.*;
            import java.lang.reflect.Method;
            import com.fasterxml.jackson.annotation.JsonAutoDetect;
            import com.fasterxml.jackson.annotation.PropertyAccessor;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.databind.JsonNode;'''

    def get_testing_src(self, ts: TestSuite, converters: TestSuiteConverters, solution_src: str):
        """
        Generates a source code for an input test suite and a solution function
        :param ts: target test suite
        :param converters: list of type converters
        :param solution_src: source code containing solution
        :return: source code for a test suite
        """
        return render_template('''
            {{solution_src}}
            class Runner {
                \tprivate static final ObjectMapper mapper; 
                \tstatic {
                    \t\tmapper = new ObjectMapper();
                    \t\tmapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                \t}
                {% for c in converters.all %}
                    \tstatic {{c.ret_type}} {{c.fn_name}}({{c.arg_type}} {{c.arg_name}}) {
                        \t\t{{c.src}}
                    \t}
                {% endfor %}
                \tpublic static void main(String[] args) throws Exception {
                \t\tSolution solution = new Solution();
                \t\tMethod method = Stream.of(Solution.class.getDeclaredMethods())
                \t\t\t.filter(m -> !m.isSynthetic() && m.getName().equals("{{fn_name}}"))
                \t\t\t.findFirst()
                \t\t\t.orElseThrow(() -> new IllegalStateException("Cannot find method {{fn_name}}"));
                \t\tmethod.setAccessible(true);
                \t\tScanner scanner = new Scanner(System.in);
                \t\twhile (true) {
                \t\t\tString line = scanner.nextLine();
                \t\t\tJsonNode rows = mapper.readTree(line);
                \t\t\tlong start = System.nanoTime();
                \t\t\t\t{{converters.result.ret_type}} result = solution.{{fn_name}}(
                {% for converter in converters.args %}
                    \t\t\t\t{{converter.fn_name}}(rows.get({{loop.index0}})){% if not loop.last %}, {% endif %}
                {% endfor %});
                \t\t\tlong end = System.nanoTime();
                \t\t\tlong duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
                \t\t\tMap<String, Object> map = new HashMap<>();
                \t\t\tmap.put("result", {{converters.output.fn_name}}(result));
                \t\t\tmap.put("duration", duration);
                \t\t\tSystem.out.println(getJson(map));
                \t\t\tSystem.out.flush();
                \t\t}
                \t}
                \tstatic String getJson(Object obj) {
                \t\ttry {
                \t\t\treturn mapper.writeValueAsString(obj);
                \t\t} catch(Exception exc) {
                \t\t\tthrow new RuntimeException(exc);
                \t\t}
                \t}
        }''', ts=ts, fn_name=to_camel_case(ts.fn_name), solution_src=solution_src, converters=converters)
