from testing.framework.js.js_test_suite_gen import JsTestSuiteGenerator
from testing.framework.tests.test_utils import GeneratorTestCase
from testing.framework.types import TestSuite, ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsTestSuiteGeneratorTests(GeneratorTestCase):

    def setUp(self):
        self.generator = JsTestSuiteGenerator()
        ConverterFn.reset_counter()

    def test_solution_generation_simple_int(self):
        tc = TestSuite()
        tc.fn_name = 'sum'
        tc.test_cases_file = 'test.txt'
        tree = SyntaxTree.of(['int[a]', 'int[b]', 'int'])

        solution_src = '''
            function solution(a, b) {
                return a + b;
            }'''
        testing_src = self.generator.generate_test_suite_src(tc, tree, solution_src)
        self.assertEqualsIgnoreWhiteSpaces('''
            const fs = require('fs');
            const readline = require('readline');
            const rl = readline.createInterface({
              input: process.stdin,
              output: process.stdout
            });

            function converter1(value) {
                return value
            }

            function converter2(value) {
                return value
            }

            function converter3(value) {
                return value
            }

            function converter4(value) {
                return value
            }

            function converter5(value) {
                return value
            }
 
            function converter6(value) {
                return value
            }
 
            //begin_user_src
            function solution(a, b) {
                return a + b;
            }
            rl.on('line', function(line) {
                const start = new Date().getTime();
                const cols = JSON.parse(line)
                
                const args = []
                args.push(converter1(cols[0]));
                args.push(converter2(cols[1]));

                const result = sum(...args);
                const end = new Date().getTime();
                console.log(JSON.stringify({
                    'result': converter6(result),
                    'duration': (end-start)
                }));
            });''', testing_src)
