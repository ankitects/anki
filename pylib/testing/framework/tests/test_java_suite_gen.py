import unittest

from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.java.java_test_suite_gen import JavaTestSuiteGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestSuiteGeneratorTests(unittest.TestCase):

    def test_solution_generation(self):
        test_suite = TestSuite('solution')
        test_suite.test_case_count = 2
        test_suite.test_cases_file = 'tmp.txt'
        type_expression = ['int[a]', 'int[b]']

        solution_src = '''
public class Solution {
\tint solution(int a, int b) {
\t\treturn a + b;
\t}   
}'''

        tree = SyntaxTree.of(type_expression)
        generator = JavaTestSuiteGenerator()
        result = generator.generate_testing_src(solution_src, test_suite, tree, dict(
            passed_msg='''passed''',
            failed_msg='''failed'''
        ))

        self.assertEqual('''
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
import java.util.concurrent.atomic.AtomicInteger;

public class Solution {
	int solution(int a, int b) {
		return a + b;
	}   

	private static final ObjectMapper mapper; 
	static {
		mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
	}

	public static void main(String[] args) throws Exception {
		List<BaseConverter> converters = Arrays.asList(new IntegerConverter(), new IntegerConverter());
		AtomicInteger index = new AtomicInteger(0);
		Solution solution = new Solution();
		Method method = Stream.of(Solution.class.getDeclaredMethods())
			.filter(m -> !m.isSynthetic() && m.getName().equals("solution"))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Cannot find method solution"));
		method.setAccessible(true);
		List<String> lines = Files.lines(Path.of("tmp.txt")).collect(Collectors.toList());
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
				System.out.println("passed"); 
			} else {
				System.out.println("failed"); 
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
}''', result)
