package test_engine

class TestCase {
  private Object[] args;
  private def expected

  Object[] getArgs() {
    return args
  }

  void setArgs(Object[] args) {
    this.args = args
  }

  def getExpected() {
    return expected
  }

  void setExpected(expected) {
    this.expected = expected
  }
}
