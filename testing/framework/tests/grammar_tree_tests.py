import unittest

from testing.framework.syntax_tree import parse_grammar, tree_to_str


class GrammarTests(unittest.TestCase):

    def test_int(self):
        src = 'int'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   int
''')

    def test_several_args(self):
        src = ['int', 'int']
        tree = parse_grammar(src)
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   int
   int
''')

    def test_int_with_name(self):
        src = 'int[i]'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   i::int
''')

    def test_array(self):
        src = 'array(int)'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   array
      int
''')

    def test_array_with_name(self):
        src = 'array(int)[arr]'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   arr::array
      int
''')

    def test_object(self):
        src = 'object(int, string, int)'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   object
      int
      string
      int
''')

    def test_object_with_names(self):
        src = 'object(int[i], string[str], int[j])'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
        self.assertEqual(result, '''
root
   object
      i::int
      str::string
      j::int
''')

    def test_deep_nested_objects(self):
        src = 'array(array(array(array(array(int)))))'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
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
        src = 'object(array(int)[arr], int[i], object(array(int)[arr])[obj])[obj]'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
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
        src = 'object(array(int)[arr], int[i], object(array(int)[arr])[obj]<SubNode>)<Node>'
        tree = parse_grammar([src])
        result = tree_to_str(tree)
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
