from abc import ABC, abstractmethod

from testing.framework.generators.template_gen import TemplateGenerator
from testing.framework.generators.test_arg_gen import TestArgGenerator
from testing.framework.generators.test_case_gen import TestCaseGenerator
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.generators.user_type_gen import UserTypeGenerator
from testing.framework.langs.java.java_template_gen import JavaTemplateGenerator
from testing.framework.langs.java.java_test_arg_gen import JavaTestArgGenerator
from testing.framework.langs.java.java_test_case_gen import JavaTestCaseGenerator
from testing.framework.langs.java.java_test_suite_gen import JavaTestSuiteGenerator
from testing.framework.langs.java.java_user_type_gen import JavaUserTypeGenerator
from testing.framework.langs.python.python_template_gen import PythonTemplateGenerator
from testing.framework.langs.python.python_test_arg_gen import PythonTestArgGenerator
from testing.framework.langs.python.python_test_case_gen import PythonTestCaseGenerator
from testing.framework.langs.python.python_test_suite_gen import PythonTestSuiteGenerator
from testing.framework.langs.python.python_user_type_gen import PythonUserTypeGenerator
from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.java_code_runner import JavaCodeRunner
from testing.framework.runners.python_code_runner import PythonCodeRunner


class AbstractLangFactory(ABC):
    @abstractmethod
    def get_template_generator(self) -> TemplateGenerator:
        pass

    @abstractmethod
    def get_test_arg_generator(self) -> TestArgGenerator:
        pass

    @abstractmethod
    def get_test_case_generator(self) -> TestCaseGenerator:
        pass

    @abstractmethod
    def get_test_suite_generator(self) -> TestSuiteGenerator:
        pass

    @abstractmethod
    def get_user_type_generator(self) -> UserTypeGenerator:
        pass

    @abstractmethod
    def get_code_runner(self) -> CodeRunner:
        pass


class JavaLangFactory(AbstractLangFactory):
    def get_template_generator(self) -> TemplateGenerator:
        return JavaTemplateGenerator()

    def get_test_arg_generator(self) -> TestArgGenerator:
        return JavaTestArgGenerator()

    def get_test_case_generator(self) -> TestCaseGenerator:
        return JavaTestCaseGenerator()

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        return JavaTestSuiteGenerator()

    def get_user_type_generator(self) -> UserTypeGenerator:
        return JavaUserTypeGenerator(self.get_test_arg_generator())

    def get_code_runner(self) -> CodeRunner:
        return JavaCodeRunner()


class PythonLangFactory(AbstractLangFactory):
    def get_template_generator(self) -> TemplateGenerator:
        return PythonTemplateGenerator()

    def get_test_arg_generator(self) -> TestArgGenerator:
        return PythonTestArgGenerator()

    def get_test_case_generator(self) -> TestCaseGenerator:
        return PythonTestCaseGenerator()

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        return PythonTestSuiteGenerator()

    def get_user_type_generator(self) -> UserTypeGenerator:
        return PythonUserTypeGenerator(self.get_test_arg_generator())

    def get_code_runner(self) -> CodeRunner:
        return PythonCodeRunner()


def get_generator_factory(lang: str) -> AbstractLangFactory:
    if lang == 'java':
        return JavaLangFactory()
    elif lang == 'python':
        return PythonLangFactory()
    else:
        raise Exception('language is not supported ' + lang)
