package test_engine

import org.apache.commons.lang.ArrayUtils

import java.lang.reflect.Array

class Converters {

  static class BaseConverter<T> {
    Class<T> type
    BaseConverter(Class<T> type) {
      this.type = type
    }
    Object convert(Object obj) {
      obj.asType(type)
    }
  }

  abstract static class InnerTypeConverter<T> extends BaseConverter<T> {
    List<BaseConverter> innerTypeConverters
    InnerTypeConverter(BaseConverter innerTypeConverter, Class<T> type) {
      super(type)
      this.innerTypeConverters = [innerTypeConverter]
    }
    InnerTypeConverter(List<BaseConverter> innerTypeConverters, Class<T> type) {
      super(type)
      this.innerTypeConverters = innerTypeConverters
    }
  }

  static class PrimitiveConverter<T> extends BaseConverter<T> {
    PrimitiveConverter(Class<T> type) {
      super(type)
    }
  }

  static class IntegerConverter extends PrimitiveConverter<Integer> {
    IntegerConverter() {
      super(Integer.class)
    }
  }

  static class BoolConverter extends PrimitiveConverter<Boolean> {
    BoolConverter() {
      super(Boolean.class)
    }
  }

  static class DoubleConverter extends PrimitiveConverter<Double> {
    DoubleConverter() {
      super(Double.classjava.jar)
    }
  }

  static class StringConverter extends BaseConverter<String> {
    StringConverter() {
      super(String.class)
    }
  }

  static class ArrayListConverter extends InnerTypeConverter<ArrayList> {
    ArrayListConverter(BaseConverter genericConverter) {
      super(genericConverter, ArrayList.class)
    }

    @Override
    Object convert(Object obj) {
      if (!(obj instanceof List)) {
        throw new IllegalArgumentException("cannot deserialize non-list value to list")
      }
      BaseConverter innerConverter = innerTypeConverters.first()
      List list = obj as List
      list.collect {
        innerConverter.convert(it)
      }
    }
  }

  static class UserTypeConverter extends InnerTypeConverter {
    UserTypeConverter(List<BaseConverter> converters, Class type) {
      super(converters, type)
    }

    @Override
    Object convert(Object obj) {
      if (!(obj instanceof List)) {
        throw new IllegalArgumentException("cannot deserialize non-list value to list")
      }
      List list = obj as List
      List params = []
      list.eachWithIndex { item, i ->
        params << innerTypeConverters[i].convert(item)
      }
      return type.newInstance(params.toArray())
    }
  }

  static class ArrayConverter extends InnerTypeConverter<Array> {
    ArrayConverter(BaseConverter innerTypeConverter) {
      super(innerTypeConverter, Array.class)
    }
    @Override
    Object convert(Object obj) {
      if (!(obj instanceof List)) {
        throw new IllegalArgumentException("cannot deserialize non-list value to list")
      }
      BaseConverter innerConverter = innerTypeConverters.first()
      List list = (obj as List).collect {
        innerConverter.convert(it)
      }
      def array = list.toArray(Array.newInstance(innerConverter.type, list.size()))
      if (innerConverter instanceof PrimitiveConverter) {
        ArrayUtils.toPrimitive(array)
      } else {
        array
      }
    }
  }
}
