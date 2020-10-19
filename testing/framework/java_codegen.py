from testing.framework.tree_renderer import TreeRenderer


class JavaSolutionTemplateGenerator:
    def generate_src(self, func_name, args, result_type, user_types):
        args_src = ', '.join([arg[0] + ' ' + arg[1] for arg in args])
        types_src = ''
        for user_type in user_types:
            if user_type != '':
                types_src += user_type + '\n'
        return '''
{}

public class Solution {{
    {} {}({}) {{
        //Add code here
    }}
}}
'''.format('\n'.join(user_types), result_type, func_name, args_src)


class JavaTestingProgramGenerator:
    def generate_testing_src(self, solution_src, func_name, invocations):
        tests_src = ''
        for arg in invocations:
            tests_src += '\n      verify({}({}), {});'.format(func_name, ','.join(arg[:-1]), arg[-1])
        main_src = '''   public static void main(String[] args) {{{}
   }}'''.format(tests_src)
        i = solution_src.rindex('}')
        return solution_src[:i] + '\n' + main_src + '\n' + solution_src[i:]


class JavaFunctionArgTypeGenerator(TreeRenderer):
    def render_array(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('Array inner-type must be 1')
        return self.render(node.nodes[0], node) + '[]'

    def render_list(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('List inner-type must be 1')
        return 'List<' + self.render(node.nodes[0], node) + '>'

    def render_map(self, node, parent_node, data):
        if len(node.nodes) != 2:
            raise Exception('Map inner-type must be 2')
        return 'Map<' + self.render(node.nodes[0], node) + ', ' + self.render(node.nodes[1], node) + '>'

    def render_int(self, node, parent_node, data):
        if self.is_root(parent_node) or self.is_array_type(parent_node) or self.is_custom_type(parent_node):
            return 'int'
        elif self.is_container_type(parent_node):
            return 'Integer'
        raise Exception('Not supported parent type')

    def render_float(self, node, parent_node, data):
        if self.is_array_type(parent_node) or self.is_custom_type(parent_node):
            return 'float'
        elif self.is_container_type(parent_node):
            return 'Float'
        raise Exception('Not supported parent type')

    def render_string(self, node, parent_node, data):
        return 'String'

    def render_obj(self, node, parent_node, data):
        return node.type


class JavaUserTypeGenerator(JavaFunctionArgTypeGenerator):
    def get_types_src(self, syntax_tree):
        type_registry = []
        for node in syntax_tree.nodes:
            self.render(node, syntax_tree, type_registry)
        return type_registry

    def render_obj(self, node, parent_node, data):
        fields = []
        for child in node.nodes:
            fields.append([self.render(child, node, data), child.name])
        data.append(self.get_user_type_src(node.type, fields))
        return node.type

    @staticmethod
    def get_user_type_src(node_type, fields):
        return '''
public static class {} {{
{}
   public {}({}) {{
{}
   }}
}}'''.format(node_type,
             '\n'.join(['   ' + field[0] + ' ' + field[1] + ';' for field in fields]),
             node_type,
             ', '.join(field[0] + ' ' + field[1] for field in fields),
             '\n'.join('      this.' + field[1] + ' = ' + field[1] + ';' for field in fields))


class JavaInvocationArgsGenerator(TreeRenderer):
    def render_args(self, node, parent_node, data):
        result = self.render(node, parent_node, data)
        return result[0] + result[1]

    def render_array(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('Array cannot contain more than 1 inner-type')
        expr_src = ''
        if self.is_root(parent_node) or self.is_container_type(parent_node):
            expr_src += 'new '
        expr_src += self.render(node.nodes[0], node, data[0])[0] + '[]'
        init_src = '{' + ','.join(self.render(node.nodes[0], node, item)[1] for item in data) + '}'
        if self.is_container_type(parent_node):
            expr_src += '[]'
            init_src = '{' + init_src + '}'
        return [expr_src, init_src]

    def render_list(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('List cannot contain more than 1 inner-type')
        expr_src = ''
        if self.is_array_type(parent_node):
            expr_src = 'List'
        init_src = self.render(node.nodes[0], node, data)[0]
        init_src += ','.join([self.render(node.nodes[0], node, item)[1] for item in data])
        return [expr_src, 'List.of({})'.format(init_src)]

    def render_map(self, node, parent_node, data):
        if len(node.nodes) != 2:
            raise Exception('Map cannot contain more than 2 sub-nodes')
        expr_src = ''
        if self.is_array_type(parent_node):
            expr_src = 'Map'
        entries = []
        for item in data:
            result = self.render(node.nodes[0], node, item[0])
            src = result[0] + result[1]
            src += ','
            result = self.render(node.nodes[1], node, item[1])
            src += result[0] + result[1]
            entries.append('''Map.entry({})'''.format(src))
        init_src = '''Map.ofEntries({})'''.format(','.join(entries))
        return [expr_src, init_src]

    def render_int(self, node, parent_node, data):
        expr_src = ''
        if self.is_array_type(parent_node):
            expr_src = 'int'
        init_src = '(int)' + str(data)
        return [expr_src, init_src]

    def render_float(self, node, parent_node, data):
        super().render_float(node, parent_node, data)

    def render_string(self, node, parent_node, data):
        super().render_string(node, parent_node, data)

    def render_obj(self, node, parent_node, data):
        expr_src = ''
        if self.is_array_type(parent_node):
            expr_src = node.type
        init_src = []
        for i, child in enumerate(node.nodes):
            result = self.render(child, node, data[i])
            init_src.append(result[0] + result[1])
        return [expr_src, 'new ' + node.type + '(' + ','.join(init_src) + ')']
