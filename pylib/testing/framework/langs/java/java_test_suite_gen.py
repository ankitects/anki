from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.java.java_converter_gen import JavaConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree

JAVA_USER_SRC_START_MARKER = '//begin_user_src\n'


class JavaTestSuiteGenerator(TestSuiteGenerator):
    """
        Generates test suite's source code in java
    """

    IMPORTS = '''
import static ankitest.Converters.*;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.*;
import java.util.stream.*;
import java.lang.reflect.Method;
import ankitest.TestCase;
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
\t\t\tTestCase tc = TestCase.parseTestCase(converters, line);
\t\t\tlong start = System.nanoTime();
\t\t\tObject result = null;
\t\t\ttry {
\t\t\t\tresult = method.invoke(solution, (Object[]) tc.getArgs());
\t\t\t} catch(Exception exc) {
\t\t\t\tthrow new RuntimeException(exc);
\t\t\t}
\t\t\tlong end = System.nanoTime();
\t\t\tlong duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
\t\t\tMap<String, Object> map = new HashMap<>();
\t\t\tmap.put("expected", tc.getExpected());
\t\t\tmap.put("result", result);
\t\t\tmap.put("args", tc.getArgs());
\t\t\tmap.put("duration", duration);
\t\t\tmap.put("index", index.incrementAndGet());
\t\t\tmap.put("test_case_count", lines.size());
\t\t\tSystem.out.println(getJson(map));
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

    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree) -> str:
        """
        Generate test suite's source code in java

        :param solution_src: input user's solution source code
        :param ts: input test suite
        :param tree: input syntax tree
        :return: test suite source code in java
        """
        solution_src = self.IMPORTS + '\n' + JAVA_USER_SRC_START_MARKER + solution_src
        converters_src = ', '.join([self.converter_generator.render(node) for node in tree.nodes])
        main_src = self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=converters_src,
            function_name=ts.func_name,
            file_path=ts.test_cases_file.replace('\\', '\\\\'))
        i = solution_src.rindex('}')
        return solution_src[:i] + main_src + '\n' + solution_src[i:]
