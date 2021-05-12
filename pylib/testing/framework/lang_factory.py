from abc import ABC

from testing.framework.cpp.cpp_template_gen import CppTemplateGenerator
from testing.framework.cpp.cpp_test_runner import CppTestRunner
from testing.framework.cpp.cpp_test_suite_gen import CppTestSuiteGenerator
from testing.framework.java.java_template_gen import JavaTemplateGenerator
from testing.framework.java.java_test_runner import JavaTestRunner
from testing.framework.java.java_test_suite_gen import JavaTestSuiteGenerator
from testing.framework.js.js_template_gen import JsTemplateGenerator
from testing.framework.js.js_test_runner import JsTestRunner
from testing.framework.js.js_test_suite_gen import JsTestSuiteGenerator
from testing.framework.python.python_template_gen import PythonTemplateGenerator
from testing.framework.python.python_test_runner import PythonTestRunner
from testing.framework.python.python_test_suite_gen import PythonTestSuiteGenerator
from testing.framework.template_gen import TemplateGenerator
from testing.framework.test_runner import TestRunner
from testing.framework.test_suite_gen import TestSuiteGenerator


class AbstractLangFactory(ABC):
    """
    Abstract Factory for code-generators
    """

    def __init__(self, template_gen, test_suite_gen, code_runner):
        self.template_gen = template_gen
        self.test_suite_gen = test_suite_gen
        self.code_runner = code_runner

    def get_template_generator(self) -> TemplateGenerator:
        """
        :return: Returns language specific solution template code generator
        """
        return self.template_gen

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        """
        :return: Returns language specific test suite code generator
        """
        return self.test_suite_gen

    def get_test_runner(self) -> TestRunner:
        """
        :return: Returns language specific code runner
        """
        return self.code_runner


class JavaLangFactory(AbstractLangFactory):
    """
    Java Code-Generators Factory
    """

    def __init__(self):
        super().__init__(JavaTemplateGenerator(),
                         JavaTestSuiteGenerator(),
                         JavaTestRunner())


class PythonLangFactory(AbstractLangFactory):
    """
    Python Code-Generators Factory
    """

    def __init__(self):
        super().__init__(PythonTemplateGenerator(),
                         PythonTestSuiteGenerator(),
                         PythonTestRunner())


class CppLangFactory(AbstractLangFactory):
    """
    C++ Code-Generators Factory
    """

    def __init__(self):
        super().__init__(CppTemplateGenerator(),
                         CppTestSuiteGenerator(),
                         CppTestRunner())


class JsLangFactory(AbstractLangFactory):
    """
    JS Code-Generators Factory
    """

    def __init__(self):
        super().__init__(JsTemplateGenerator(),
                         JsTestSuiteGenerator(),
                         JsTestRunner())


def get_lang_factory(lang: str) -> AbstractLangFactory:
    """
    Depending on input language returns appropriate code-generators factory
    :param lang: target programming language
    :return: instance of AbstractLangFactory
    :raises: Exception if the language is not supported
    """
    if lang == 'java':
        global java_lang_factory
        return java_lang_factory
    elif lang == 'python':
        global python_lang_factory
        return python_lang_factory
    elif lang == 'cpp':
        global cpp_lang_factory
        return cpp_lang_factory
    elif lang == 'js':
        global js_lang_factory
        return js_lang_factory
    else:
        raise Exception('language is not supported ' + lang)


java_lang_factory = JavaLangFactory()
python_lang_factory = PythonLangFactory()
cpp_lang_factory = CppLangFactory()
js_lang_factory = JsLangFactory()
