package test_engine

import groovy.util.logging.Slf4j
import test_engine.TestCaseParser.TestCase

import java.util.concurrent.TimeUnit

@Slf4j
class TestRunner {

  static boolean run(Class testClass, String methodName, String testData) {
    def obj = testClass.newInstance()
    def method = testClass.declaredMethods.find { !it.synthetic && it.name == methodName }
    if (!method) {
      throw new IllegalArgumentException("cant find method " + methodName + " in the tested class")
    }
    def testCases = TestCaseParser.parseTestCases(testData)
    for (int i = 0; i < testCases.size(); i++) {
      TestCase tc = testCases.get(i)
      def start = System.nanoTime()
      def result = method.invoke(obj, (Object[])tc.args)
      def end = System.nanoTime()
      def msg = "(${i + 1}/${testCases.size()}) ${TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS)} ms - "
      if (result == tc.expected) {
        msg += " PASSED"
        log.info(msg)
      } else {
        msg += """ FAILED
          args: ${tc.args}
          expected: ${tc.expected}
          received: ${result}
        """
        log.error(msg)
        return false
      }
    }
    true
  }
}