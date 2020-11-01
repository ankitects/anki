import unittest

from testing.framework.langs.java.java_test_arg_gen import JavaTestArgGenerator
from testing.framework.langs.java.java_user_type_gen import JavaUserTypeGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaUserTypeGeneratorTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_type):
        tree = SyntaxTree.of([type_expression])
        generator = JavaUserTypeGenerator(JavaTestArgGenerator())
        user_types = generator.get_user_type_definitions(tree)
        self.assertEquals(len(user_types), 2)
        self.assertEquals(expected_type[0], user_types['TypeB'])
        self.assertEquals(expected_type[1], user_types['TypeA'])

    def test_array_of_integers(self):
        self.evaluate_generator(
            'object(object(int[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a]', ['''public static class TypeB {
   int a;
   public TypeB(int a) {
      this.a = a;
   }
}''', '''public static class TypeA {
   TypeB obj_b;
   int b;
   public TypeA(TypeB obj_b, int b) {
      this.obj_b = obj_b;
      this.b = b;
   }
}'''])
