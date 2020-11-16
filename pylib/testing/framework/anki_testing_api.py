import json
import re

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
    rows = strip_html_tags(note[model[3]['name']]).split('\n')
    return name, rows

def trim_indent(s: str):
    s = re.sub(r'^\n+', '', s)
    s = re.sub(r'\n+$', '', s)
    spaces = re.findall(r'^ +', s, flags=re.MULTILINE)
    if len(spaces) > 0 and len(re.findall(r'^[^\s]', s, flags=re.MULTILINE)) == 0:
        s = re.sub(r'^%s' % (min(spaces)), '', s, flags=re.MULTILINE)
    return s

def strip_html_tags(html):
    text = re.sub(r'<br>', '\n', html)
    text = text.replace('\\n', '\n')
    text = re.sub(r'<.*?>', '', text)
    text = text.replace('&lt;', '<').replace('&gt;', '>')
    return text.strip()

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
        if row.strip() == '':
            continue
        row_data = []
        for col in row.split(';'):
            row_data.append(json.loads(col))
        ts.test_cases.append(test_case_gen.get_test_case(tree, row_data))
    test_suite_gen = factory.get_test_suite_generator()
    src = test_suite_gen.inject_imports(src, ts)
    test_cases_src = test_suite_gen.generate_test_case_invocations(ts,
       '''Test <span class='passed'>PASSED</span> (%(index)d/%(total)d) - %(duration)s ms''',
       '''Test <span class='failed'>FAILED</span> (%(index)d/%(total)d)\\n" +
           "expected: %(expected)s\\n" + 
           "result: %(result)s''')

    src = test_suite_gen.inject_test_suite_invocation(src, test_cases_src, ts)
    factory.get_code_runner().run(src, logger, '''Compilation Error: <span class='failed'>$error</span>''')
