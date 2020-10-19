class TreeRenderer:
    
    def render(self, node, parent_node=None, data=None):
        if node.type == 'array':
            return self.render_array(node, parent_node, data)
        elif node.type == 'list':
            return self.render_list(node, parent_node, data)
        elif node.type == 'map':
            return self.render_map(node, parent_node, data)
        elif node.type == 'int':
            return self.render_int(node, parent_node, data)
        elif node.type == 'float':
            return self.render_float(node, parent_node, data)
        elif node.type == 'string':
            return self.render_string(node, parent_node, data)
        elif node.is_custom_type:
            return self.render_obj(node, parent_node, data)

    def render_array(self, node, parent_node, data):
        pass

    def render_list(self, node, parent_node, data):
        pass

    def render_map(self, node, parent_node, data):
        pass

    def render_int(self, node, parent_node, data):
        pass

    def render_float(self, node, parent_node, data):
        pass

    def render_string(self, node, parent_node, data):
        pass

    def render_obj(self, node, parent_node, data):
        pass

    @staticmethod
    def is_container_type(node):
        return node.type in ['list', 'map']

    @staticmethod
    def is_custom_type(node):
        return node.is_custom_type

    @staticmethod
    def is_array_type(node):
        return node.type == 'array'

    @staticmethod
    def is_simple_type(node):
        return node.type in ['int', 'float', 'string']

    @staticmethod
    def is_root(node):
        return node.type == 'root'