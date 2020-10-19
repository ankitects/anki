import unittest

from testing.framework.codegen import generate_testing_program
from testing.framework.python_codegen import PythonTestingProgramGenerator, PythonInvocationArgsGenerator


class PythonTestingProgramGeneratorTests(unittest.TestCase):

    def test_sum(self):
        csv_data = '''
int[a];int[b];int
1;1;2
2;2;4
3;3;6
'''
        solution_src = '''
def sum(a: int, b: int) -> str:
   return a + b
'''
        invocations_args_generator = PythonInvocationArgsGenerator()
        testing_src_generator = PythonTestingProgramGenerator()
        src = generate_testing_program(solution_src, 'sum', csv_data, invocations_args_generator, testing_src_generator)
        self.assertEqual(src, '''
def sum(a: int, b: int) -> str:
   return a + b

verify(sum(int(1),int(1)) == int(2))
verify(sum(int(2),int(2)) == int(4))
verify(sum(int(3),int(3)) == int(6))''')

    def test_array_of_integers(self):
        csv_data = '''
array(array(int))[a];int
[[1,2,3]];1
'''
        solution_src = '''
def fn(a: List[List[int]]) -> int:
   return a[0][0]
'''
        invocations_args_generator = PythonInvocationArgsGenerator()
        testing_src_generator = PythonTestingProgramGenerator()
        src = generate_testing_program(solution_src, 'fn', csv_data, invocations_args_generator, testing_src_generator)
        self.assertEqual(src, '''
def fn(a: List[List[int]]) -> int:
   return a[0][0]

verify(fn([[int(1),int(2),int(3)]]) == int(1))''')

    def test_list_of_integer(self):
        csv_data = '''
list(int)[a];int
[1,2,3];1
'''
        solution_src = '''
def fn(a: List[int]) -> int:
   return a[0]
'''
        invocations_args_generator = PythonInvocationArgsGenerator()
        testing_src_generator = PythonTestingProgramGenerator()
        src = generate_testing_program(solution_src, 'fn', csv_data, invocations_args_generator, testing_src_generator)
        self.assertEqual(src, '''
def fn(a: List[int]) -> int:
   return a[0]

verify(fn([int(1),int(2),int(3)]) == int(1))''')

