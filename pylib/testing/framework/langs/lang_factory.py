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
    def __init__(self, template_gen, arg_declaration_gen, test_suite_gen, user_type_gen, code_runner):
        self.template_gen = template_gen
        self.arg_declaration_gen = arg_declaration_gen
        self.test_suite_gen = test_suite_gen
        self.user_type_gen = user_type_gen
        self.code_runner = code_runner

    def get_template_generator(self) -> SolutionTemplateGenerator:
        return self.template_gen

    def get_test_arg_generator(self) -> ArgDeclarationGenerator:
        return self.arg_declaration_gen

    def get_test_suite_generator(self) -> TestSuiteGenerator:
        return self.test_suite_gen

    def get_user_type_generator(self) -> UserTypeDeclarationGenerator:
        return self.user_type_gen

    def get_code_runner(self) -> CodeRunner:
        return self.code_runner


class JavaLangFactory(AbstractLangFactory):
    def __init__(self):
        super().__init__(JavaTemplateGenerator(),
                         JavaArgDeclarationGenerator(),
                         JavaTestSuiteGenerator(),
                         JavaUserTypeGenerator(JavaArgDeclarationGenerator()),
                         JavaCodeRunner())


class PythonLangFactory(AbstractLangFactory):
    def __init__(self):
        super().__init__(PythonTemplateGenerator(),
                         PythonArgDeclarationGenerator(),
                         PythonTestSuiteGenerator(),
                         PythonUserTypeGenerator(PythonArgDeclarationGenerator()),
                         PythonCodeRunner())


java_lang_factory = JavaLangFactory()
python_lang_factory = PythonLangFactory()


def get_lang_factory(lang: str) -> AbstractLangFactory:
    if lang == 'java':
        return java_lang_factory
    elif lang == 'python':
        return python_lang_factory
    else:
        raise Exception('language is not supported ' + lang)
