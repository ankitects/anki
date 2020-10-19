package test_engine

import groovy.json.JsonSlurper

class TestCaseParser {

  static class TestCase {
    List args
    def expected
  }

  static def parseHeader(String header) {
    header.split(';').collect {
      def type = it.split(/\s/).last()
      DslParser.parseDsl(('expr(' + type + ')').replaceAll('\\(', '\\(_'))
    }
  }

  static List<TestCase> parseTestCases(String testData) {
    def lines = testData.split('\n').collect { it.trim() }.findAll { it }
    def json = new JsonSlurper()
    def converters = parseHeader(lines[0])
    def rows = lines[1..-1].collect { line ->
      line.split(";").collect {
        json.parseText(it)
      }
    }

    if (!rows) {
      throw new IllegalArgumentException("not test cases found")
    }
    if (converters.size() != rows.first().size()) {
      throw new IllegalArgumentException("column size mismatch")
    }
    rows.collect { List row ->
      def cols = row.withIndex().collect { col, i ->
        converters[i].convert(col)
      }
      new TestCase(args: cols[0..-2], expected: cols.last())
    }
  }
}
