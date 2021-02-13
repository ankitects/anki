from typing import Dict

from testing.framework.langs.python.python_type_gen import PythonTypeGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import trim_indent


class PythonClassGenerator(PythonTypeGenerator):
    """
    Generates custom class source code in python
    """

    TEMPLATE_SRC = '''class {}:
\tdef __init__(self, {}):
{}'''

    def visit_obj(self, node: SyntaxTree, registry: Dict[str, str]):
        """
        Generates custom class source code

        :param node: input syntax tree node
        :param registry: dictionary containing type names as keys and implementations as values
        :return: custom type's name
        """
        fields = []
        for child in node.nodes:
            fields.append([self.render(child, registry), child.name])

        args_src = ', '.join(field[1] + ': ' + field[0] for field in fields)
        init_src = '\n'.join('\t\tself.' + field[1] + ' = ' + field[1] for field in fields)
        registry[node.node_type] = trim_indent(self.TEMPLATE_SRC.format(node.node_type, args_src, init_src))

        return node.node_type
