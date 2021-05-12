import unittest

from testing.framework.syntax.syntax_tree import SyntaxTree


class SyntaxTreeTests(unittest.TestCase):

    def test_int(self):
        tree = SyntaxTree.of(['int'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   int
''')

    def test_several_args(self):
        tree = SyntaxTree.of(['int', 'int'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   int
   int
''')

    def test_int_with_name(self):
        tree = SyntaxTree.of(['int[i]'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   i::int
''')

    def test_array(self):
        tree = SyntaxTree.of(['array(int)'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   array
      int
''')

    def test_array_with_name(self):
        tree = SyntaxTree.of(['array(int)[arr]'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   arr::array
      int
''')

    def test_object(self):
        tree = SyntaxTree.of(['object(int, string, int)'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   object
      int
      string
      int
''')

    def test_object_with_names(self):
        tree = SyntaxTree.of(['object(int[i], string[str], int[j])'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   object
      i::int
      str::string
      j::int
''')

    def test_deep_nested_objects(self):
        tree = SyntaxTree.of(['array(array(array(array(array(int)))))'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   array
      array
         array
            array
               array
                  int
''')

    def test_parse_grammer_nested_objects(self):
        tree = SyntaxTree.of(['object(array(int)[arr], int[i], object(array(int)[arr])[obj])[obj]'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   obj::object
      arr::array
         int
      i::int
      obj::object
         arr::array
            int
''')

    def test_parse_grammer_typed_nested_objects(self):
        tree = SyntaxTree.of(['object(array(int)[arr], int[i], object(array(int)[arr])[obj]<SubNode>)<Node>'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   Node
      arr::array
         int
      i::int
      obj::SubNode
         arr::array
            int
''')

    def test_parse_grammer_double_typed_nested_objects(self):
        tree = SyntaxTree.of(['object(object(array(int)[arr])[obj]<SubNode>)[obj1]<Node>', 'Node[obj2]'])
        result = tree.to_string()
        self.assertEqual(result, '''
root
   obj1::Node
      obj::SubNode
         arr::array
            int
   obj2::Node
''')
