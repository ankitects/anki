from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class JavaConverterGenerator(SyntaxTreeVisitor):
    def visit_array(self, node: SyntaxTree, data):
        return 'new ArrayConverter(' + self.render(node.first_child()) + ')'

    def visit_list(self, node: SyntaxTree, data):
        return 'new ArrayListConverter(' + self.render(node.first_child()) + ')'

    def visit_map(self, node: SyntaxTree, data):
        pass

    def visit_int(self, node: SyntaxTree, data):
        return 'new IntegerConverter()'

    def visit_float(self, node: SyntaxTree, data):
        return 'new DoubleConverter()'

    def visit_bool(self, node: SyntaxTree, data):
        return 'new BoolConverter()'

    def visit_string(self, node: SyntaxTree, data):
        return 'new StringConverter()'

    def visit_obj(self, node: SyntaxTree, data):
        return 'new UserTypeConverter(Arrays.asList(' + ', '.join([self.render(node) for node in node.nodes]) + \
               '),' + node.node_type + '.class)'

    def render(self, tree: SyntaxTree, data_item=None):
        return tree.accept(self, data_item)

    def generate_initializers(self, tree: SyntaxTree) -> str:
        initializers = []
        for node in tree.nodes:
            initializers.append(self.render(node))
        return ', '.join(initializers)

