package test_engine

import spock.lang.Specification

class TestCaseParserTest extends Specification {

  def 'parse simple int test cases'() {
    given:
    def testData = """
      int;int;int
      1;1;2
      2;2;4"""
    when:
    def testCases = TestCaseParser.parseTestCases(testData)
    then:
    testCases.size() == 2
    testCases.first().args[0].class == Integer.class
    testCases.first().args[1].class == Integer.class
    testCases.first().expected.class == Integer.class
  }

  def 'parse simple double test cases'() {
    given:
    def testData = """
      double;double;int
      1;1;2
      2;2;4"""
    when:
    def testCases = TestCaseParser.parseTestCases(testData)
    then:
    testCases.size() == 2
    testCases.first().args[0].class == Double.class
    testCases.first().args[1].class == Double.class
    testCases.first().expected.class == Integer.class
  }

  def 'parse list double test cases'() {
    given:
    def testData = """
      list(double);int
      [1, 2, 3];1
      [3, 4, 5];1"""
    when:
    def testCases = TestCaseParser.parseTestCases(testData)
    then:
    testCases.size() == 2
    testCases.first().args[0].class == ArrayList.class
    testCases.first().args[0][0].class == Double.class
    testCases.first().expected.class == Integer.class
  }

  def 'parse array string test cases'() {
    given:
    def testData = """
      array(string);int
      ["1", "2", "3"];1
      ["3", "4", "5"];1"""
    when:
    def testCases = TestCaseParser.parseTestCases(testData)
    then:
    testCases.size() == 2
    testCases.first().args[0].class == String[].class
    testCases.first().args[0][0].class == String.class
    testCases.first().expected.class == Integer.class
  }
}
