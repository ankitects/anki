from testing.framework.cpp.cpp_test_suite_gen import CppTestSuiteGenerator
from testing.framework.tests.test_utils import GeneratorTestCase
from testing.framework.types import TestSuite, ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class CppTestSuiteGeneratorTests(GeneratorTestCase):

    def setUp(self) -> None:
        self.generator = CppTestSuiteGenerator()
        ConverterFn.reset_counter()

    def test_solution_generation_simple_int(self):
        tc = TestSuite()
        tc.fn_name = 'sum'
        tc.test_cases_file = 'test.txt'
        tree = SyntaxTree.of(['int[a]', 'int[b]', 'int'])

        solution_src = '''
            int solution(int a, int b) {
                return a + b;
            }'''
        testing_src = self.generator.generate_test_suite_src(tc, tree, solution_src)
        self.assertEqualsIgnoreWhiteSpaces('''
            #include <vector>
            #include <array>
            #include "cpp_lib/jute.h"
            #include <functional>
            #include <stdexcept>
            #include<string>
            #include<chrono>
            using namespace std;

            //begin_user_src
            int solution(int a, int b) {
                return a + b;
            }
            int converter1(jute::jValue value) {
                return value.as_int();
            }
            int converter2(jute::jValue value) {
                return value.as_int();
            }

            int converter3(jute::jValue value) {
                return value.as_int();
            }

            jute::jValue converter4(int value) {
                jute::jValue result;
                result.set_type(jute::JNUMBER);
                result.set_string(std::to_string(value));
                return result;
            }
            
            jute::jValue converter5(int value) {
                jute::jValue result;
                result.set_type(jute::JNUMBER);
                result.set_string(std::to_string(value));
                return result;
            }

            jute::jValue converter6(int value) {
                jute::jValue result;
                result.set_type(jute::JNUMBER);
                result.set_string(std::to_string(value));
                return result;
            }

            int main() {
                Solution solution;
                std::string buf;
                while (true) {
                    std::getline(std::cin, buf);
                    jute::jValue row = jute::parser::parse(buf);
                    auto started = std::chrono::high_resolution_clock::now();
                    int result = solution.sum(converter1(row[0]), converter2(row[1]));
                    auto done = std::chrono::high_resolution_clock::now();
                    jute::jValue json_result = converter6(result);
                    jute::jValue response;
                    response.set_type(jute::JOBJECT);
                    response.add_property("result", json_result);
                    jute::jValue duration;
                    duration.set_type(jute::JNUMBER);
                    duration.set_string(std::to_string(
                        std::chrono::duration_cast<std::chrono::milliseconds>(done-started).count()));
                    response.add_property("duration", duration);
                    std::cout << response.to_string() << endl;
                }
                return 0;
            }''', testing_src)
