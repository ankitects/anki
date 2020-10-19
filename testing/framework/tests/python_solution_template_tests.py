import unittest

from testing.framework.codegen import generate_solution_template
from testing.framework.java_codegen import JavaSolutionTemplateGenerator, JavaFunctionArgTypeGenerator, \
    JavaUserTypeGenerator
from testing.framework.python_codegen import PythonSolutionTemplateGenerator, PythonFunctionArgTypeGenerator, \
    PythonUserTypeGenerator
from testing.framework.syntax_tree import parse_grammar


class CodegenTests(unittest.TestCase):

    def test_integers(self):
        csv_tests = '''
int[a];int[b];string
1;1;"test"
'''
        solution_generator = PythonSolutionTemplateGenerator()
        argtype_generator = PythonFunctionArgTypeGenerator()
        usertype_generator = PythonUserTypeGenerator()
        src = generate_solution_template('sum', csv_tests, solution_generator, argtype_generator, usertype_generator)
        self.assertEqual(src, '''
        
def sum(a: int, b: int) -> str:
    #Add code here
''')

    def test_java_nested_class_generation(self):
        csv_tests = '''
object(object(int[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a];int[i]
'''
        solution_generator = PythonSolutionTemplateGenerator()
        argtype_generator = PythonFunctionArgTypeGenerator()
        usertype_generator = PythonUserTypeGenerator()
        src = generate_solution_template('identity', csv_tests, solution_generator, argtype_generator, usertype_generator)
        self.assertEqual(src, '''
class TypeB:
   def __init__(self, a: int):
      self.a = a

class TypeA:
   def __init__(self, obj_b: TypeB, b: int):
      self.obj_b = obj_b
      self.b = b
        
def identity(obj_a: TypeA) -> int:
    #Add code here
''')

    def test_java_array_generation(self):
        csv_tests = '''
object(object(int[a])<TypeB>[obj_b], int[b], array(array(int))[arr])<TypeA>[obj_a];int[i]'''
        solution_generator = PythonSolutionTemplateGenerator()
        argtype_generator = PythonFunctionArgTypeGenerator()
        usertype_generator = PythonUserTypeGenerator()
        src = generate_solution_template('identity', csv_tests, solution_generator, argtype_generator, usertype_generator)
        self.assertEqual(src, '''
class TypeB:
   def __init__(self, a: int):
      self.a = a

class TypeA:
   def __init__(self, obj_b: TypeB, b: int, arr: List[List[int]]):
      self.obj_b = obj_b
      self.b = b
      self.arr = arr
        
def identity(obj_a: TypeA) -> int:
    #Add code here
''')
