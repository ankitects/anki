from typing import Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.java.java_converter_gen import JavaConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


def _get_context_variables(ts: TestSuite):
    """
    returns variables which are used to generate test suite's source code
    :param ts: target test suite
    :return: key-value dictionary with the variables
    """
    return dict(
        total=ts.test_case_count,
        duration='" + duration + "',
        index='" + index.incrementAndGet() + "',
        expected='" + getJson(tc.getExpected()) + "',
        result='" + getJson(result) + "')


class JavaTestSuiteGenerator(TestSuiteGenerator):
    """
        Generate test suite's source code in java
    """

    IMPORTS = '''
import static test_engine.Verifier.verify;
import static test_engine.Converters.*;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.*;
import java.util.stream.*;
import java.lang.reflect.Method;
import test_engine.TestCaseParser;
import test_engine.TestCase;
import test_engine.Verifier;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;'''

    MAIN_FUNCTION_TEMPLATE = '''
\tprivate static final ObjectMapper mapper; 
\tstatic {
\t\tmapper = new ObjectMapper();
\t\tmapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
\t}

\tpublic static void main(String[] args) throws Exception {
\t\tList<BaseConverter> converters = Arrays.asList(%(converters_src)s);
\t\tAtomicInteger index = new AtomicInteger(0);
\t\tSolution solution = new Solution();
\t\tMethod method = Stream.of(Solution.class.getDeclaredMethods())
\t\t\t.filter(m -> !m.isSynthetic() && m.getName().equals("%(function_name)s"))
\t\t\t.findFirst()
\t\t\t.orElseThrow(() -> new IllegalStateException("Cannot find method %(function_name)s"));
\t\tmethod.setAccessible(true);
\t\tList<String> lines = Files.lines(Path.of("%(file_path)s")).collect(Collectors.toList());
\t\tfor (String line : lines) {
\t\t\tTestCase tc = TestCaseParser.parseTestCase(converters, line);
\t\t\tlong start = System.nanoTime();
\t\t\tObject result = null;
\t\t\ttry {
\t\t\t\tresult = method.invoke(solution, (Object[]) tc.getArgs());
\t\t\t} catch(Exception exc) {
\t\t\t\tthrow new RuntimeException(exc);
\t\t\t}
\t\t\tlong end = System.nanoTime();
\t\t\tlong duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
\t\t\tif (Verifier.verify(result, tc.getExpected())) {
\t\t\t\tSystem.out.println("%(pass_msg)s"); 
\t\t\t} else {
\t\t\t\tSystem.out.println("%(fail_msg)s"); 
\t\t\t\treturn;
\t\t\t}
\t\t}
\t}

\tpublic static String getJson(Object obj) {
\t\ttry {
\t\t\treturn mapper.writeValueAsString(obj);
\t\t} catch(Exception exc) {
\t\t\tthrow new RuntimeException(exc);
\t\t}
\t}'''

    def __init__(self):
        self.converter_generator = JavaConverterGenerator()

    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree, messages: Dict[str, str]) -> str:
        """
        Generate test suite's source code in java

        :param solution_src: input user's solution source code
        :param ts: input test suite
        :param tree: input syntax tree
        :param messages: map containing the messages which will be displayed during the testing
        :return: test suite source code in java
        """
        test_passed_msg = messages['passed_msg'] % _get_context_variables(ts)
        test_failed_msg = messages['failed_msg'] % _get_context_variables(ts)
        solution_src = self.IMPORTS + '\n' + solution_src
        converters_src = ', '.join([self.converter_generator.render(node) for node in tree.nodes])
        main_src = self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=converters_src,
            function_name=ts.func_name,
            file_path=ts.test_cases_file.replace('\\', '\\\\'),
            pass_msg=test_passed_msg,
            fail_msg=test_failed_msg)
        i = solution_src.rindex('}')
        return solution_src[:i] + main_src + '\n' + solution_src[i:]
