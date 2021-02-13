from testing.framework.langs.java.java_type_gen import JavaTypeGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaClassGenerator(JavaTypeGenerator):
    """
    Generates custom classes source code in java
    """

    CLASS_TEMPLATE = '''public static class {} {{\n{}\n\tpublic {}({}) {{\n{}\n\t}}\n}}'''

    def visit_obj(self, node: SyntaxTree, registry):
        """
        Generates custom class source code

        :param node: input syntax tree node
        :param registry: dictionary containing type names as keys and implementations as values
        :return: custom type's name
        """
        fields = []
        for child in node.nodes:
            fields.append([self.render(child, registry), child.name])

        fields_src = '\n'.join('\t' + field[0] + ' ' + field[1] + ';' for field in fields)
        args_src = ', '.join(field[0] + ' ' + field[1] for field in fields)
        fields_init_src = '\n'.join('\t\t' + 'this.' + field[1] + ' = ' + field[1] + ';' for field in fields)
        type_src = self.CLASS_TEMPLATE.format(node.node_type, fields_src, node.node_type, args_src, fields_init_src)
        type_name = node.node_type
        registry[type_name] = type_src

        return type_name
