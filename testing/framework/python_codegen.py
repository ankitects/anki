from testing.framework.tree_renderer import TreeRenderer


class PythonSolutionTemplateGenerator:
    def generate_src(self, func_name, args, result_type, user_types):
        args_src = ', '.join([arg[1] + ': ' + arg[0] for arg in args])
        types_src = ''
        for user_type in user_types:
            if user_type != '':
                types_src += user_type + '\n'
        return '''{}
        
def {}({}) -> {}:
    #Add code here
'''.format('\n'.join(user_types), func_name, args_src, result_type)


class PythonTestingProgramGenerator:
    def generate_testing_src(self, solution_src, func_name, invocations):
        testing_src = ''
        for arg in invocations:
            testing_src += '\nverify({}({}) == {})'.format(func_name, ','.join(arg[:-1]), arg[-1])
        return solution_src + testing_src


class PythonFunctionArgTypeGenerator(TreeRenderer):
    def render_array(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('Array inner-type must be 1')
        return 'List[' + self.render(node.nodes[0], node) + ']'

    def render_list(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('List inner-type must be 1')
        return 'List[' + self.render(node.nodes[0], node) + ']'

    def render_map(self, node, parent_node, data):
        if len(node.nodes) != 2:
            raise Exception('Map inner-type must be 2')
        return 'Dict[' + self.render(node.nodes[0], node) + ', ' + self.render(node.nodes[1], node) + ']'

    def render_int(self, node, parent_node, data):
        return 'int'

    def render_float(self, node, parent_node, data):
        return 'float'

    def render_string(self, node, parent_node, data):
        return 'str'

    def render_obj(self, node, parent_node, data):
        return node.type


class PythonInvocationArgsGenerator(TreeRenderer):
    def render_args(self, node, parent_node, data):
        result = self.render(node, parent_node, data)
        return result[0] + result[1]

    def render_array(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('Array cannot contain more than 1 inner-type')
        init_src = '[' + ','.join(self.render(node.nodes[0], node, item)[1] for item in data) + ']'
        return ['', init_src]

    def render_list(self, node, parent_node, data):
        if len(node.nodes) != 1:
            raise Exception('List cannot contain more than 1 inner-type')
        init_src = '[' + ','.join(self.render(node.nodes[0], node, item)[1] for item in data) + ']'
        return ['', init_src]

    def render_map(self, node, parent_node, data):
        if len(node.nodes) != 2:
            raise Exception('Map cannot contain more than 2 sub-nodes')
        entries = []
        for item in data:
            if not self.is_simple_type(node.nodes[0]):
                raise Exception('Unhashable type as a dict key')
            result = self.render(node.nodes[0], node, item[0])
            src = result[0] + result[1]
            src += ':'
            result = self.render(node.nodes[1], node, item[1])
            src += result[0] + result[1]
            entries.append(src)
        init_src = '{' + ', '.join(entries) + '}'
        return ['', init_src]

    def render_int(self, node, parent_node, data):
        init_src = 'int(' + str(data) + ')'
        return ['', init_src]

    def render_float(self, node, parent_node, data):
        init_src = 'float(' + str(data) + ')'
        return ['', init_src]

    def render_string(self, node, parent_node, data):
        init_src = 'str("' + str(data) + '")'
        return ['', init_src]

    def render_obj(self, node, parent_node, data):
        init_src = []
        for i, child in enumerate(node.nodes):
            result = self.render(child, node, data[i])
            init_src.append(result[0] + result[1])
        return ['', node.type + '(' + ','.join(init_src) + ')']


class PythonUserTypeGenerator(PythonFunctionArgTypeGenerator):
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
class {}:
   def __init__(self, {}):
{}'''.format(node_type,
         ', '.join(field[1] + ': ' + field[0] for field in fields),
         '\n'.join('      self.' + field[1] + ' = ' + field[1] for field in fields))
