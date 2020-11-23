package test_engine

import groovy.util.logging.Slf4j

import java.lang.reflect.Method
import java.nio.file.Files
import java.util.stream.Stream

import static Converters.*

import java.util.concurrent.TimeUnit

@Slf4j
class TestRunner {

//  static boolean run(Class testClass, String methodName, TestCase tc, int i) {
//    Object obj = testClass.newInstance()
//    Method method = Stream.of(testClass.getDeclaredMethods())
//      .filter(m -> !m.isSynthetic() && m.getName().equals(methodName))
//      .findFirst()
//      .orElseThrow(() -> new IllegalStateException("Cannot find method " + methodName));
//    method.setAccessible(true);
//    def start = System.nanoTime()
//    def result = method.invoke(obj, (Object[])tc.args)
//    def end = System.nanoTime()
//    def msg = "(${i + 1}) ${TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS)} ms - "
//    if (result == tc.expected) {
//      msg += " PASSED"
//      log.info(msg)
//      return true
//    } else {
//      msg += """ FAILED
//        args: ${tc.args}
//        expected: ${tc.expected}
//        received: ${result}
//      """
//      log.error(msg)
//      return false
//    }
//  }
//
//  static void main(String[] args) {
//    File csv = new File('sample.csv')
//    def converters = [
//      new IntegerConverter(),
//      new ArrayConverter(new IntegerConverter()),
//      new DoubleConverter()
//    ]
//    run(Solution.class, 'solve', csv.text, converters)
//  }
}