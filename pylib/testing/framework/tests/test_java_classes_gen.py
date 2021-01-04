import unittest

from testing.framework.langs.java.java_class_gen import JavaClassGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaUserTypeGeneratorTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_type):
        tree = SyntaxTree.of([type_expression])
        generator = JavaClassGenerator()
        classes = {}
        for node in tree.nodes:
            generator.render(node, classes)
        self.assertEqual(len(classes), 2)
        self.assertEqual(expected_type[0], classes['TypeB'])
        self.assertEqual(expected_type[1], classes['TypeA'])

    def test_nested_objects(self):
        self.evaluate_generator(
            'object(object(int[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a]', ['''public static class TypeB {
\tint a;
\tpublic TypeB(int a) {
\t\tthis.a = a;
\t}
}''', '''public static class TypeA {
\tTypeB obj_b;
\tint b;
\tpublic TypeA(TypeB obj_b, int b) {
\t\tthis.obj_b = obj_b;
\t\tthis.b = b;
\t}
}'''])
