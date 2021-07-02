from testing.framework.python.python_type_mapper import PythonTypeMapper
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.tests.test_utils import GeneratorTestCase


class PythonTypeMappingsGeneratorTests(GeneratorTestCase):

    def setUp(self):
        self.type_mapper = PythonTypeMapper()

    def test_array_of_integers(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('List[List[int]]', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_list_of_integer(self):
        tree = SyntaxTree.of(['list(int)[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('List[int]', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_simple_int(self):
        tree = SyntaxTree.of(['int'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('int', args[0].type)

    def test_class(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Edge', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class Edge:\n\tdef __init__(self, a: int, b: int):\n\t\tself.a = a\n\t\tself.b = b
        ''', type_defs['Edge'])

    def test_object_list(self):
        tree = SyntaxTree.of(['list(object(int[a],int[b])<Edge>)[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('List[Edge]', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class Edge:\n\tdef __init__(self, a: int, b: int):\n\t\tself.a = a\n\t\tself.b = b
        ''', type_defs['Edge'])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, list(object(int[a],int[b])<Edge>))[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Dict[str, List[Edge]]', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class Edge:\n\tdef __init__(self, a: int, b: int):\n\t\tself.a = a\n\t\tself.b = b
        ''', type_defs['Edge'])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(int)'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('ListNode[int]', args[0].type)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            T = TypeVar('T')
            class ListNode(Generic[T]):
                def __init__(self, data: Optional[Type[T]]=None):
                    self.data = data
                    self.next = None
        ''', type_defs['linked_list'])

    def test_binary_tree(self):
        tree = SyntaxTree.of(['binary_tree(int)'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('BinaryTreeNode[int]', args[0].type)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            T = TypeVar('T')
            class BinaryTreeNode(Generic[T]):
                def __init__(self, data: Optional[Type[T]]=None, left=None, right=None):
                    self.data = data
                    self.left = left
                    self.right = right
        ''', type_defs['binary_tree'])
