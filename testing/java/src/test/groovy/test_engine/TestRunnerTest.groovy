package test_engine

import spock.lang.Specification

class TestRunnerTest extends Specification {
  def 'test sum up two numbers - correct'() {
    given:
    def targetClass = SumUpTwoNumbers.class
    def methodName = 'sumUp'
    def testData = """
      int;int;int
      1;1;2
      2;2;4"""
    when:
    def ok = TestRunner.run(targetClass, methodName, testData)
    then:
    ok
  }

  def 'test sum up two numbers - wrong'() {
    given:
    def targetClass = SumUpTwoNumbers.class
    def methodName = 'sumUp'
    def testData = """
      a int;b int;int
      1;1;2
      2;2;5"""
    when:
    def ok = TestRunner.run(targetClass, methodName, testData)
    then:
    !ok
  }

  def 'test sort int array'() {
    given:
    def targetClass = SortNumbers.class
    def methodName = 'sort'
    def testData = """
      array(int);array(int)
      [1, 5, 3, 2, 4, 6, 7, 10];[1, 2, 3, 4, 5, 6, 7, 10]"""
    when:
    def ok = TestRunner.run(targetClass, methodName, testData)
    then:
    ok
  }

  def 'test sort string array'() {
    given:
    def targetClass = SortStrings.class
    def methodName = 'sort'
    def testData = """
      array(string);array(string)
      ["aaa", "ccc", "bbb", "000"];["000", "aaa", "bbb", "ccc"]"""
    when:
    def ok = TestRunner.run(targetClass, methodName, testData)
    then:
    ok
  }
}
