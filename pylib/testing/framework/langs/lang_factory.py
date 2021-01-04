from abc import ABC

from testing.framework.generators.template_gen import SolutionTemplateGenerator
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.java.java_class_gen import JavaClassGenerator
from testing.framework.langs.java.java_template_gen import JavaTemplateGenerator
from testing.framework.langs.java.java_test_suite_gen import JavaTestSuiteGenerator
from testing.framework.langs.java.java_type_gen import JavaTypeGenerator
from testing.framework.langs.python.python_class_gen import PythonClassGenerator
from testing.framework.langs.python.python_template_gen import PythonTemplateGenerator
from testing.framework.langs.python.python_test_suite_gen import PythonTestSuiteGenerator
from testing.framework.langs.python.python_type_gen import PythonTypeGenerator
from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.java_code_runner import JavaCodeRunner
from testing.framework.runners.python_code_runner import PythonCodeRunner
from testing.framework.syntax.syntax_tree import SyntaxTreeVisitor


class AbstractLangFactory(ABC):
    """
    Abstract Factory for code-generators
    """

    def __init__(self, template_gen, type_gen, test_suite_gen, class_gen, code_runner):
        self.template_gen = template_gen
        self.type_gen = type_gen
        self.test_suite_gen = test_suite_gen
        self.class_gen = class_gen
        self.code_runner = code_runner

    def get_template_generator(self) -> SolutionTemplateGenerator:
        """
        :return: Returns language specific solution template code generator
        """
        return self.template_gen

    def get_type_generator(self) -> SyntaxTreeVisitor:
        """
        :return: Returns language specific argument types code generator
        """
        return self.type_gen

    def get_class_generator(self) -> SyntaxTreeVisitor:
        """
        :return: Returns language specific class code generator
        """
        return self.class_gen

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        """
        :return: Returns language specific test suite code generator
        """
        return self.test_suite_gen

    def get_code_runner(self) -> CodeRunner:
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
                         JavaTypeGenerator(),
                         JavaTestSuiteGenerator(),
                         JavaClassGenerator(),
                         JavaCodeRunner())


class PythonLangFactory(AbstractLangFactory):
    """
    Python Code-Generators Factory
    """

    def __init__(self):
        super().__init__(PythonTemplateGenerator(),
                         PythonTypeGenerator(),
                         PythonTestSuiteGenerator(),
                         PythonClassGenerator(),
                         PythonCodeRunner())


def get_lang_factory(lang: str) -> AbstractLangFactory:
    """
    Depending on input language returns appropriate code-generators factory
    :param lang: target programming language
    :return: instance of AbstractLangFactory
    :raises: Exception if the language is not supported
    """
    if lang == 'java':
        return java_lang_factory
    elif lang == 'python':
        return python_lang_factory
    else:
        raise Exception('language is not supported ' + lang)


java_lang_factory = JavaLangFactory()
python_lang_factory = PythonLangFactory()
