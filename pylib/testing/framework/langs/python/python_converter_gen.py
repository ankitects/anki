from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class PythonConverterGenerator(SyntaxTreeVisitor):
    def visit_array(self, node: SyntaxTree, data):
        return 'ListConverter(' + self.render(node.first_child()) + ')'

    def visit_list(self, node: SyntaxTree, data):
        return 'ListConverter(' + self.render(node.first_child()) + ')'

    def visit_map(self, node: SyntaxTree, data):
        pass

    def visit_int(self, node: SyntaxTree, data):
        return 'IntegerConverter()'

    def visit_float(self, node: SyntaxTree, data):
        return 'FloatConverter()'

    def visit_string(self, node: SyntaxTree, data):
        return 'StringConverter()'

    def visit_obj(self, node: SyntaxTree, data):
        return 'UserTypeConverter([' + ', '.join([self.render(node) for node in node.nodes]) + '])'

    def render(self, tree: SyntaxTree, data_item=None):
        return tree.accept(self, data_item)

    def generate_initializers(self, tree: SyntaxTree) -> str:
        initializers = []
        for node in tree.nodes:
            initializers.append(self.render(node))
        return ', '.join(initializers)

