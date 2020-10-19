package test_engine

import spock.lang.Specification

import static test_engine.Converters.*

class DslParserTest extends Specification {

  def 'test dsl'() {
    when:
    def converter = DslParser.parseDsl("expr(_list(_int))")
    then:
    converter instanceof ArrayListConverter
    converter.generic instanceof IntegerConverter
  }

  def 'test int conversion'() {
    given:
    def converter = DslParser.parseDsl("expr(_int)")
    when:
    int result = converter.convert(2)
    then:
    result == 2
  }

  def 'test int array conversion'() {
    given:
    def converter = DslParser.parseDsl("expr(_array(_int))")
    when:
    def result = converter.convert([2, 4, 5, 6])
    then:
    result == [2, 4, 5, 6]
  }

  def 'test double array conversion'() {
    given:
    def converter = DslParser.parseDsl("expr(_array(_double))")
    when:
    double[] result = converter.convert([2, 4, 5, 6])
    then:
    result == [2.0, 4.0, 5.0, 6.0]
  }

  def 'test convert to list of arrays of double'() {
    given:
    def converter = DslParser.parseDsl("expr(_list(_array(_double)))")
    def solution = new ListDoubleArraySolution()
    when:
    def result = solution.solution(converter.convert([[2, 4, 5, 6]]))
    then:
    result == 1
  }
}
