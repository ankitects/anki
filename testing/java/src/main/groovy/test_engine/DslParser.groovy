package test_engine

import static test_engine.Converters.*

class DslParser {
  static TypeConverter<?> parseDsl(String dslExpression) {
    def binding = new Binding()
    binding.setVariable('expr', builder(null))
    binding.setVariable('_int', builder(IntegerConverter.class))
    binding.setVariable('_double', builder(DoubleConverter.class))
    binding.setVariable('_string', builder(StringConverter.class))
    binding.setVariable('_list', builder(ArrayListConverter.class))
    binding.setVariable('_array', builder(ArrayConverter.class))
    new GroovyShell(this.class.classLoader, binding).evaluate(dslExpression)
  }

  private static def builder(Class<? extends TypeConverter> converterClass) {
    { arg ->
      TypeConverter generic
      if (converterClass != null) {
        converter = converterClass.newInstance()
      }
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
