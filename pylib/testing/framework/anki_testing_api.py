import json

from anki.cards import Card
from aqt.utils import run_async
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.lang_factory import get_generator_factory
from testing.framework.runners.console_logger import ConsoleLogger
from testing.framework.syntax.syntax_tree import SyntaxTree

def parse_card(card: Card):
    note = card.note()
    model = card.model()['flds']
    name = note[model[1]['name']]
    rows = note[model[2]['name']]\
        .replace('<br>', '\n')\
        .replace('&lt;', '<') \
        .replace('&gt;', '>')\
        .strip()\
        .split('\n')
    return name, rows


def get_solution_template(card: Card, lang: str):
    name, rows = parse_card(card)
    factory = get_generator_factory(lang)
    if factory is None:
        raise Exception('Unknown language ' + lang)
    types_info = rows[0].split(';')
    tree = SyntaxTree.of(types_info)
    ts = TestSuite(name)
    ts.user_types = factory.get_user_type_generator().get_user_type_definitions(tree)
    ts.test_args, ts.result_type = factory.get_test_arg_generator().get_args(tree)
    return factory.get_template_generator().generate_template_src(ts)


@run_async
def test_solution(card: Card, src: str, lang: str, logger: ConsoleLogger):
    name, rows = parse_card(card)
    factory = get_generator_factory(lang)
    if factory is None:
        raise Exception('Unknown language ' + lang)
    types_info = rows[0].split(';')
    tree = SyntaxTree.of(types_info)
    ts = TestSuite(name)
    test_case_gen = factory.get_test_case_generator()
    for row in rows[1:]:
        row_data = []
        for col in row.split(';'):
            row_data.append(json.loads(col))
        ts.test_cases.append(test_case_gen.get_test_case(tree, row_data))
    test_suite_gen = factory.get_test_suite_generator()
    src = test_suite_gen.inject_imports(src, ts)
    test_cases_src = test_suite_gen.generate_test_case_invocations(ts,
       '''Test <span class='passed'>PASSED</span> ($index/$total) - $duration ms''',
       '''Test <span class='failed'>FAILED</span> ($index/$total)\\n" +
           "expected: $expected\\n" + 
           "result: $result''')

    src = test_suite_gen.inject_test_suite_invocation(src, test_cases_src, ts)
    factory.get_code_runner().run(src, logger)
