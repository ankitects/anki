from typing import Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.java.java_converter_gen import JavaConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestSuiteGenerator(TestSuiteGenerator):
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
    import com.fasterxml.jackson.databind.ObjectMapper;
    import java.util.concurrent.atomic.AtomicInteger;'''

    MAIN_FUNCTION_TEMPLATE = '''
    private static final ObjectMapper mapper = new ObjectMapper(); 
    
    public static void main(String[] args) throws Exception {
        List<BaseConverter> converters = Arrays.asList(%(converters_src)s);
        AtomicInteger index = new AtomicInteger(0);
        Solution solution = new Solution();
        Method method = Stream.of(Solution.class.getDeclaredMethods())
           .filter(m -> !m.isSynthetic() && m.getName().equals("%(function_name)s"))
           .findFirst()
           .orElseThrow(() -> new IllegalStateException("Cannot find method %(function_name)s"));
        method.setAccessible(true);
        
        ObjectMapper mapper = new ObjectMapper();
        
        List<String> lines = Files.lines(Path.of("%(file_path)s")).collect(Collectors.toList());

        for (String line : lines) {
            TestCase tc = TestCaseParser.parseTestCase(converters, line);
            long start = System.nanoTime();
            Object result = null;
            try {
                result = method.invoke(solution, (Object[]) tc.getArgs());
            } catch(Exception exc) {
                throw new RuntimeException(exc);
            }
            long end = System.nanoTime();
            long duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
            if (Verifier.verify(result, tc.getExpected())) {
                System.out.println("%(pass_msg)s"); 
            } else {
                System.out.println("%(fail_msg)s"); 
                return;
            }
        }
    }
    
    public static String getJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch(Exception exc) {
            throw new RuntimeException(exc);
        }
    }
    '''

    def __init__(self):
        self.converter_generator = JavaConverterGenerator()

    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree, messages: Dict[str, str]) -> str:
        test_passed_msg = messages['passed_msg'] % dict(
            index='" + index.incrementAndGet() + "',
            total=ts.test_case_count,
            duration='" + duration + "')
        test_failed_msg = messages['failed_msg'] % dict(
            index='" + index.incrementAndGet() + "',
            total="total",
            expected='" + getJson(tc.getExpected()) + "',
            result='" + getJson(result) + "')
        solution_src = self.IMPORTS + '\n' + solution_src
        main_src = self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=self.converter_generator.generate_initializers(tree),
            function_name=ts.func_name,
            file_path=ts.test_cases_file,
            pass_msg=test_passed_msg,
            fail_msg=test_failed_msg)
        i = solution_src.rindex('}')
        return solution_src[:i] + main_src + solution_src[i:]
