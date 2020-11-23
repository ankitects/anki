from abc import ABC, abstractmethod

from testing.framework.generators.arg_declaration_gen import ArgDeclarationGenerator
from testing.framework.generators.solution_template_gen import SolutionTemplateGenerator
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.generators.user_type_declaration_gen import UserTypeDeclarationGenerator
from testing.framework.langs.java.java_arg_declaration_gen import JavaArgDeclarationGenerator
from testing.framework.langs.java.java_template_gen import JavaTemplateGenerator
from testing.framework.langs.java.java_test_suite_gen import JavaTestSuiteGenerator
from testing.framework.langs.java.java_user_type_gen import JavaUserTypeGenerator
from testing.framework.langs.python.python_arg_declaration_gen import PythonArgDeclarationGenerator
from testing.framework.langs.python.python_template_gen import PythonTemplateGenerator
from testing.framework.langs.python.python_test_suite_gen import PythonTestSuiteGenerator
from testing.framework.langs.python.python_user_type_gen import PythonUserTypeGenerator
from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.java_code_runner import JavaCodeRunner
from testing.framework.runners.python_code_runner import PythonCodeRunner


class AbstractLangFactory(ABC):
    @abstractmethod
    def get_template_generator(self) -> SolutionTemplateGenerator:
        pass

    @abstractmethod
    def get_test_arg_generator(self) -> ArgDeclarationGenerator:
        pass

    @abstractmethod
    def get_test_suite_generator(self) -> TestSuiteGenerator:
        pass

    @abstractmethod
    def get_user_type_generator(self) -> UserTypeDeclarationGenerator:
        pass

    @abstractmethod
    def get_code_runner(self) -> CodeRunner:
        pass


class JavaLangFactory(AbstractLangFactory):
    def get_template_generator(self) -> SolutionTemplateGenerator:
        return JavaTemplateGenerator()

    def get_test_arg_generator(self) -> ArgDeclarationGenerator:
        return JavaArgDeclarationGenerator()

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        return JavaTestSuiteGenerator()

    def get_user_type_generator(self) -> UserTypeDeclarationGenerator:
        return JavaUserTypeGenerator(self.get_test_arg_generator())

    def get_code_runner(self) -> CodeRunner:
        return JavaCodeRunner()


class PythonLangFactory(AbstractLangFactory):
    def get_template_generator(self) -> SolutionTemplateGenerator:
        return PythonTemplateGenerator()

    def get_test_arg_generator(self) -> ArgDeclarationGenerator:
        return PythonArgDeclarationGenerator()

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        return PythonTestSuiteGenerator()

    def get_user_type_generator(self) -> UserTypeDeclarationGenerator:
        return PythonUserTypeGenerator(self.get_test_arg_generator())

    def get_code_runner(self) -> CodeRunner:
        return PythonCodeRunner()


def get_lang_factory(lang: str) -> AbstractLangFactory:
    if lang == 'java':
        return JavaLangFactory()
    elif lang == 'python':
        return PythonLangFactory()
    else:
        raise Exception('language is not supported ' + lang)
