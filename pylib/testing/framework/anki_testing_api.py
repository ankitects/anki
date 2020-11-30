import os
import tempfile

from anki.cards import Card
from aqt.utils import run_async
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.lang_factory import get_lang_factory
from testing.framework.runners.console_logger import ConsoleLogger
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import strip_html_tags


def parse_anki_card(card: Card):
    note = card.note()
    model = card.model()['flds']
    name = note[model[1]['name']]
    rows = strip_html_tags(note[model[3]['name']]).split('\n')
    return name, rows


def get_solution_template(card: Card, lang: str):
    name, rows = parse_anki_card(card)
    factory = get_lang_factory(lang)
    if factory is None:
        raise Exception('Unknown language ' + lang)

    tree = SyntaxTree.of(rows[0].split(';'))
    ts = TestSuite(name)
    ts.user_types = factory.get_user_type_generator().get_user_type_declarations(tree)
    ts.test_args, ts.result_type = factory.get_test_arg_generator().get_arg_declarations(tree)
    return factory.get_template_generator().generate_solution_template(ts)


@run_async
def run_tests(card: Card, src: str, lang: str, logger: ConsoleLogger):
    name, rows = parse_anki_card(card)
    factory = get_lang_factory(lang)
    if factory is None:
        raise Exception('Unknown language ' + lang)

    tree = SyntaxTree.of(rows[0].split(';'))
    ts = TestSuite(name)
    ts.test_cases_file = tempfile.mkdtemp() + '/data.csv'
    try:
        file = open(ts.test_cases_file, 'w')
        file.write('\n'.join(rows[1:]))
        file.close()

        test_suite_gen = factory.get_test_suite_generator()
        test_suite_src = test_suite_gen.generate_testing_src(src, ts, tree, dict(
            passed_msg='''Test <span class='passed'>PASSED</span> (%(index)s/%(total)s) - %(duration)s ms''',
            failed_msg='''Test <span class='failed'>FAILED</span> (%(index)s/%(total)s)\\n" +
                "&nbsp;&nbsp;&nbsp;&nbsp;expected: %(expected)s\\n" +
                "&nbsp;&nbsp;&nbsp;&nbsp;result: %(result)s<br>'''
        ))

        factory.get_code_runner().run(test_suite_src, logger, dict(
            compilation_failed='''Compilation Error: <span class='failed'>$error</span>'''))
    finally:
        os.remove(ts.test_cases_file)

def stop_tests(lang: str):
    factory = get_lang_factory(lang)
    factory.get_code_runner().stop()
