package test_engine

import org.apache.commons.lang3.ArrayUtils

import java.lang.reflect.Array

class Converters {
  private static abstract class TypeConverter<T> {
    Class<T> clazz
    TypeConverter<?> generic
    boolean primitive
    TypeConverter(Class<T> clazz, boolean primitive = false) {
      this.clazz = clazz
      this.primitive = primitive
    }
    Object convert(Object obj) {
      obj.asType(clazz)
    }
  }
  private abstract static class PrimitiveConverter<T> extends TypeConverter<T> {
    PrimitiveConverter(Class<T> clazz) {
      super(clazz, true)
    }
    void setGeneric(Object generic) {
      throw new IllegalArgumentException("cannot set generic to a primitive type")
    }
  }
  private static class IntegerConverter extends PrimitiveConverter<Integer> {
    IntegerConverter() {
      super(Integer.class)
    }
  }
  private static class DoubleConverter extends PrimitiveConverter<Double> {
    DoubleConverter() {
      super(Double.class)
    }
  }
  private static class StringConverter extends TypeConverter<String> {
    StringConverter() {
      super(String.class)
    }
  }
  abstract static class ListConverter<T> extends TypeConverter<T> {
    ListConverter(Class<T> clazz) {
      super(clazz)
    }

    @Override
    Object convert(Object obj) {
      if (!(obj instanceof List)) {
        throw new IllegalArgumentException("cannot deserialize to list")
      }
      List list = (List)obj
      list.collect {
        generic.convert(it)
      }
    }
  }
  static class ArrayListConverter extends ListConverter<ArrayList> {
    ArrayListConverter(Class<ArrayList> clazz) {
      super(clazz)
    }
  }
  static class ArrayConverter extends ListConverter<Array> {
    ArrayConverter(Class<Array> clazz) {
      super(clazz)
    }
    @Override
    Object convert(Object obj) {
      def list = (List) super.convert(obj)
      def array = list.toArray(Array.newInstance(generic.clazz, list.size()))
      if (generic.primitive) {
        ArrayUtils.toPrimitive(array)
      } else {
        array
      }
    }
  }
}
