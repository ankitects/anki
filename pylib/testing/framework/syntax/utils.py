import re
from typing import Dict, Tuple, List

from testing.framework.dto.test_arg import TestArg
from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor

IDENT_TAB_SIZE = 4


def strip_html_tags(html: str) -> str:
    """
    Remove html tags from html, replace <br> with \n, replce &lt; and &gt; by < and >
    :param html: target html
    :return: stripped html
    """
    text = re.sub(r'<br>', '\n', html)
    text = text.replace('\\n', '\n')
    text = re.sub(r'<.*?>', '', text)
    text = text.replace('&lt;', '<').replace('&gt;', '>')
    return text.strip()


def to_snake_case(name: str) -> str:
    """
    Converts camel-cased string to snake-cased, for example helloWorld -> hello_world
    :param name: target name to convert
    :return: snake-cased string
    """
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def to_camel_case(name: str) -> str:
    """
    Converts snake-cased string to camel-cased, for example hello_world -> helloWorld
    :param name: target name to convert
    :return: camel-cased string
    """
    result = ''.join([x[0].upper() + x[1:] for x in name.split('_')])
    return result[0].lower() + result[1:]


def trim_indent(s: str) -> str:
    """
    Replaces new lines from string, replace \t tab char with spaces
    :param s: target string
    :return: trimmed and indented string
    """
    s = re.sub(r'^\n+', '', s)
    s = re.sub(r'\n+$', '', s)
    return s.replace('\t', ' ' * IDENT_TAB_SIZE)


def get_class_declarations(tree: SyntaxTree, type_renderer: SyntaxTreeVisitor) -> Dict[str, str]:
    """
    Generates user type definitions based on the syntax tree provided
    :param type_renderer:
    :param tree: source syntax tree
    :return: dictionary holding type names as keys and source code as values
    """
    classes = {}
    for node in tree.nodes:
        type_renderer.render(node, classes)
    return classes


def get_arg_declarations(tree: SyntaxTree, type_renderer: SyntaxTreeVisitor) -> Tuple[List[TestArg], str]:
    """
    Executes type renderer for the supplied syntax tree, returns list of arguments and the resultant type
    :param tree: target syntax tree
    :param type_renderer: type renderer
    :return: Tuple of testing arguments and name of a solution function's result type
    """
    i = 0
    args = []
    for node in tree.nodes:
        name = node.name
        if name is None or name == '':
            name = 'var' + str(i)
            i += 1
        args.append(TestArg(type_renderer.render(node), name))
    return args[:-1], args[-1].arg_type
