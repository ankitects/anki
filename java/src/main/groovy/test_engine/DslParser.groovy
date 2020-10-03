package test_engine

import static test_engine.Converters.*

class DslParser {
  static TypeConverter<?> parseDsl(String dslExpression) {
    def binding = new Binding()
    binding.setVariable('expr', builder(null))
    binding.setVariable('_int', builder(new IntegerConverter()))
    binding.setVariable('_double', builder(new DoubleConverter()))
    binding.setVariable('_string', builder(new StringConverter()))
    binding.setVariable('_list', builder(new ArrayListConverter()))
    binding.setVariable('_array', builder(new ArrayConverter()))
    new GroovyShell(this.class.classLoader, binding).evaluate(dslExpression)
  }

  private static def builder(TypeConverter converter) {
    { arg ->
      TypeConverter generic
      if (arg == null) {
        return converter
      } else if (arg instanceof TypeConverter) {
        generic = arg
      } else {
        generic = arg()
      }
      if (converter) {
        converter.setGeneric(generic)
        return converter
      } else {
        return generic
      }
    }
  }
}
