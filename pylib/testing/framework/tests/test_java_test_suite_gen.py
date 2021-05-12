from testing.framework.java.java_test_suite_gen import JavaTestSuiteGenerator
from testing.framework.tests.test_utils import GeneratorTestCase
from testing.framework.types import TestSuite, ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestSuiteGeneratorTests(GeneratorTestCase):

    def setUp(self) -> None:
        self.generator = JavaTestSuiteGenerator()
        ConverterFn.reset_counter()

    def test_solution_generation_simple_int(self):
        tc = TestSuite()
        tc.fn_name = 'sum'
        tc.test_cases_file = 'test.txt'
        tree = SyntaxTree.of(['int[a]', 'int[b]', 'int'])

        solution_src = '''
            public class Solution {
                int solution(int a, int b) {
                    return a + b;
                }
            }'''
        testing_src = self.generator.generate_test_suite_src(tc, tree, solution_src)
        self.assertEqualsIgnoreWhiteSpaces('''
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
            import com.fasterxml.jackson.databind.JsonNode;
 
            //begin_user_src
            public class Solution {
                int solution(int a, int b) {
                    return a + b;
                }
            }
 
            class Runner {
                private static final ObjectMapper mapper; 
                static {
                    mapper = new ObjectMapper();
                    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                }
 
                static int converter1(JsonNode value) {
                    return value.asInt();
                }

                static int converter2(JsonNode value) {
                    return value.asInt();
                }

                static int converter3(JsonNode value) {
                    return value.asInt();
                }

                static int converter4(int value) {
                    return value;
                }

                static int converter5(int value) {
                    return value;
                }

                static int converter6(int value) {
                    return value;
                }

                public static void main(String[] args) throws Exception {
                    Solution solution = new Solution();
                    Method method = Stream.of(Solution.class.getDeclaredMethods())
                        .filter(m -> !m.isSynthetic() && m.getName().equals("sum"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Cannot find method sum"));
                    method.setAccessible(true);
                    Scanner scanner = new Scanner(System.in);
                    while (true) {
                        String line = scanner.nextLine();
                        JsonNode rows = mapper.readTree(line);
                        long start = System.nanoTime();
                        int result = solution.sum(converter1(rows.get(0)), converter2(rows.get(1)));
                        long end = System.nanoTime();
                        long duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
                        Map<String, Object> map = new HashMap<>();
                        map.put("result", converter6(result));
                        map.put("duration", duration);
                        System.out.println(getJson(map));
                        System.out.flush();
                    }
                }
                static String getJson(Object obj) {
                    try {
                        return mapper.writeValueAsString(obj);
                    } catch(Exception exc) {
                        throw new RuntimeException(exc);
                    }
                }
            }
            ''', testing_src)
