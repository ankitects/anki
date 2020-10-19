import unittest

from testing.framework.codegen import generate_testing_program
from testing.framework.java_codegen import JavaInvocationArgsGenerator, JavaTestingProgramGenerator


class JavaTestingProgramGeneratorTests(unittest.TestCase):

    def test_sum(self):
        csv_data = '''
int[a];int[b];int
1;1;2
2;2;4
3;3;6
'''
        solution_src = '''
public class Solution {
   int sum(int a, int b) {
      return a + b;
   }
}
'''
        invocations_args_generator = JavaInvocationArgsGenerator()
        testing_src_generator = JavaTestingProgramGenerator()
        src = generate_testing_program(solution_src, 'sum', csv_data, invocations_args_generator, testing_src_generator)
        self.assertEqual(src, '''
public class Solution {
   int sum(int a, int b) {
      return a + b;
   }

   public static void main(String[] args) {
      verify(sum((int)1,(int)1), (int)2);
      verify(sum((int)2,(int)2), (int)4);
      verify(sum((int)3,(int)3), (int)6);
   }
}
''')

    def test_array_of_integers(self):
        csv_data = '''
array(array(int))[a];int
[[1,2,3]];1
'''
        solution_src = '''
public class Solution {
   int fn(int[][] a) {
      return a[0][0];
   }
}
'''
        invocations_args_generator = JavaInvocationArgsGenerator()
        testing_src_generator = JavaTestingProgramGenerator()
        src = generate_testing_program(solution_src, 'fn', csv_data, invocations_args_generator, testing_src_generator)
        self.assertEqual(src, '''
public class Solution {
   int fn(int[][] a) {
      return a[0][0];
   }

   public static void main(String[] args) {
      verify(fn(new int[][]{{(int)1,(int)2,(int)3}}), (int)1);
   }
}
''')

    def test_list_of_integer(self):
        csv_data = '''
list(int)[a];int
[1,2,3];1
'''
        solution_src = '''
public class Solution {
   int fn(List<Integer> a) {
      return a.get(0);
   }
}
'''
        invocations_args_generator = JavaInvocationArgsGenerator()
        testing_src_generator = JavaTestingProgramGenerator()
        src = generate_testing_program(solution_src, 'fn', csv_data, invocations_args_generator, testing_src_generator)
        self.assertEqual(src, '''
public class Solution {
   int fn(List<Integer> a) {
      return a.get(0);
   }

   public static void main(String[] args) {
      verify(fn(List.of((int)1,(int)2,(int)3)), (int)1);
   }
}
''')
