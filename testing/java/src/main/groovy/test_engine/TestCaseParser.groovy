package test_engine

import groovy.json.JsonSlurper
import static Converters.*

class TestCaseParser {

  static TestCase parseTestCase(List<BaseConverter> converters, String row) {
    def json = new JsonSlurper()
    def cols = row.split(';').collect {
      json.parseText(it)
    }.withIndex().collect { col, int i ->
      converters[i].convert(col)
    }
    new TestCase(args: cols[0..-2], expected: cols.last())
  }
}
