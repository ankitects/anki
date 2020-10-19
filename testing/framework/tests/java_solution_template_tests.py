import unittest

from testing.framework.codegen import generate_solution_template
from testing.framework.java_codegen import JavaSolutionTemplateGenerator, JavaFunctionArgTypeGenerator, \
    JavaUserTypeGenerator
from testing.framework.syntax_tree import parse_grammar


class CodegenTests(unittest.TestCase):

    def test_java_integers(self):
        csv_tests = '''
int[a];int[b];string
1;1;"test"
'''
        solution_generator = JavaSolutionTemplateGenerator()
        argtype_generator = JavaFunctionArgTypeGenerator()
        usertype_generator = JavaUserTypeGenerator()
        src = generate_solution_template('sum', csv_tests, solution_generator, argtype_generator, usertype_generator)
        self.assertEqual(src, '''


public class Solution {
    String sum(int a, int b) {
        //Add code here
    }
}
''')

    def test_java_nested_class_generation(self):
        csv_tests = '''
object(object(int[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a];int[i]
'''
        solution_generator = JavaSolutionTemplateGenerator()
        argtype_generator = JavaFunctionArgTypeGenerator()
        usertype_generator = JavaUserTypeGenerator()
        src = generate_solution_template('identity', csv_tests, solution_generator, argtype_generator, usertype_generator)
        self.assertEqual(src, '''

public static class TypeB {
   int a;
   public TypeB(int a) {
      this.a = a;
   }
}

public static class TypeA {
   TypeB obj_b;
   int b;
   public TypeA(TypeB obj_b, int b) {
      this.obj_b = obj_b;
      this.b = b;
   }
}

public class Solution {
    int identity(TypeA obj_a) {
        //Add code here
    }
}
''')

    def test_java_array_generation(self):
        csv_tests = '''
object(object(int[a])<TypeB>[obj_b], int[b], array(array(int))[arr])<TypeA>[obj_a];int[i]'''
        solution_generator = JavaSolutionTemplateGenerator()
        argtype_generator = JavaFunctionArgTypeGenerator()
        usertype_generator = JavaUserTypeGenerator()
        src = generate_solution_template('identity', csv_tests, solution_generator, argtype_generator, usertype_generator)
        self.assertEqual(src, '''

public static class TypeB {
   int a;
   public TypeB(int a) {
      this.a = a;
   }
}

public static class TypeA {
   TypeB obj_b;
   int b;
   int[][] arr;
   public TypeA(TypeB obj_b, int b, int[][] arr) {
      this.obj_b = obj_b;
      this.b = b;
      this.arr = arr;
   }
}

public class Solution {
    int identity(TypeA obj_a) {
        //Add code here
    }
}
''')
