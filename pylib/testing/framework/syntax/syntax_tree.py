from __future__ import annotations

from abc import ABC, abstractmethod

from typing_extensions import Type


class SyntaxTree:
    def __init__(self, parent: SyntaxTree = None, node_type: str = None, is_user_type: bool = False):
        self.parent = parent
        self.node_type = node_type.strip() if node_type is not None else 'root'
        self.name = ''
        self.nodes = []
        self.is_user_type = is_user_type

    def add_child(self, node_type):
        node = SyntaxTree(self, node_type)
        self.nodes.append(node)
        return node

    def first_child(self):
        return self.nodes[0]

    def second_child(self):
        return self.nodes[1]

    def accept(self, visitor: Type[SyntaxTreeVisitor], data_item=None):
        if self.node_type == 'array':
            return visitor.visit_array(self, data_item)
        elif self.node_type == 'list':
            return visitor.visit_list(self, data_item)
        elif self.node_type == 'map':
            return visitor.visit_map(self, data_item)
        elif self.node_type == 'int':
            return visitor.visit_int(self, data_item)
        elif self.node_type == 'bool':
            return visitor.visit_bool(self, data_item)
        elif self.node_type == 'float':
            return visitor.visit_float(self, data_item)
        elif self.node_type == 'string':
            return visitor.visit_string(self, data_item)
        elif self.is_user_type:
            return visitor.visit_obj(self, data_item)

    def is_container_type(self):
        return self.node_type in ['list', 'map']

    def is_array_type(self):
        return self.node_type == 'array'

    def is_primitive_type(self):
        return self.node_type in ['int', 'float', 'string']

    def is_root(self):
        return self.node_type == 'root'

    @staticmethod
    def of(expression_list):
        root = SyntaxTree()
        for expr in expression_list:
            node = root
            buf = ''
            for c in expr:
                if c not in '()[]<>,':
                    buf += c
                    continue
                if buf == '':
                    if c in ',)':
                        node = node.parent
                elif c in '([<':
                    node = node.add_child(buf)
                elif c in ',)':
                    node.add_child(buf)
                elif c == ']':
                    node.name = buf
                elif c == '>':
                    node.node_type = buf
                    node.is_user_type = True
                buf = ''
            if buf != '':
                node.add_child(buf)
        return root

    def to_string(self, buf: str = '\n', ident: int = 0):
        buf += ' ' * ident
        if self.name != '':
            buf += '{}::{}'.format(self.name, self.node_type)
        else:
            buf += self.node_type
        buf += '\n'
        for c in self.nodes:
            buf = c.to_string(buf, ident + 3)
        return buf


class SyntaxTreeVisitor(ABC):

    @abstractmethod
    def visit_array(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_list(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_map(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_int(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_float(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_string(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_bool(self, node: SyntaxTree, data):
        pass

    @abstractmethod
    def visit_obj(self, node: SyntaxTree, data):
        pass

    def render(self, tree: SyntaxTree, data_item=None):
        return tree.accept(self, data_item)
