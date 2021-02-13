import os
import sys
import tempfile
from typing import Tuple, List, Callable

from anki.cards import Card
from aqt.utils import run_async
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.lang_factory import get_lang_factory
from testing.framework.runners.console_logger import ConsoleLogger
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import strip_html_tags, get_arg_declarations, get_class_declarations


def parse_anki_card(card: Card) -> Tuple[str, str, List[str]]:
    """
    extracts target function name, test description and test case rows from a card
    :param card: input card
    :return: tuple: function name, description, test case rows
    """
    note = card.note()
    model = card.model()['flds']
    # todo: come up with better solution than indexes
    description = strip_html_tags(note[model[1]['name']])
    fn_name = note[model[2]['name']]
    rows = strip_html_tags(note[model[4]['name']]).split('\n')
    return fn_name, description, rows


def get_solution_template(card: Card, lang: str) -> str:
    """
    Generates test solution template for the given Card and language
    :param card: target card
    :param lang: target programming language
    :return: solution template src
    """
    fn_name, description, rows = parse_anki_card(card)
    factory = get_lang_factory(lang)
    if factory is None:
        raise Exception('Unknown language ' + lang)

    tree = SyntaxTree.of(rows[0].split(';'))
    ts = TestSuite(fn_name)
    ts.description = description
    ts.classes = get_class_declarations(tree, factory.get_class_generator())
    ts.test_args, ts.result_type = get_arg_declarations(tree, factory.get_type_generator())
    return factory.get_template_generator().generate_solution_template(ts)


@run_async
def run_tests(card: Card, src: str, lang: str, logger: ConsoleLogger, fncomplete: Callable):
    """
    Executes tests for a given test suite and user code
    :param card: target card
    :param src: target solution src to be executed
    :param lang: target programming language
    :param logger: console logger
    :param fncomplete: complete callback
    """
    fn_name, _, rows = parse_anki_card(card)
    factory = get_lang_factory(lang)
    if factory is None:
        raise Exception('Unknown language ' + lang)

    tree = SyntaxTree.of(rows[0].split(';'))
    ts = TestSuite(fn_name)
    ts.test_cases_file = os.path.join(tempfile.mkdtemp(), 'data.csv')
    ts.test_case_count = len(rows) - 1
    try:
        file = open(ts.test_cases_file, 'w')
        file.write('\n'.join(rows[1:]))
        file.close()

        test_suite_gen = factory.get_test_suite_generator()
        test_suite_src = test_suite_gen.generate_testing_src(src, ts, tree)

        logger.setTotalCount(ts.test_case_count)
        factory.get_code_runner().run(test_suite_src, logger, dict(
            start_msg='''<span class='info'>Running tests...</span>''',
            passed_msg='''Test <span class='passed'>PASSED</span> (%(index)s/%(total)s) - %(duration)s ms''',
            failed_msg='''Test <span class='failed'>FAILED</span> (%(index)s/%(total)s)<br/>
                &nbsp;&nbsp;&nbsp;&nbsp;args: %(args)s<br/>
                &nbsp;&nbsp;&nbsp;&nbsp;expected: %(expected)s<br/>
                &nbsp;&nbsp;&nbsp;&nbsp;result: %(result)s<br/>''',
            success_msg='''<br/><br/>All tests <span class='info'>PASSED</span>.<br/>''',
            compilation_failed='''Compilation Error: <span class='failed'>$error</span>'''))
    except:
        logger.log("<span class='failed'>Unexpected runtime error: " + str(sys.exc_info()) + "</span>")
    finally:
        os.remove(ts.test_cases_file)
        fncomplete()


def stop_tests(lang: str):
    """
    Stop tests execution
    :param lang: target programming language
    """
    factory = get_lang_factory(lang)
    factory.get_code_runner().stop()
