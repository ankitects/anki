from __future__ import annotations
from abc import ABC, abstractmethod
from typing import List

from typing_extensions import Type


class SyntaxTree:
    """
    Tree structure to store parsed typing expression for a particular test suite
    """
    def __init__(self, parent: SyntaxTree = None, node_type: str = None, is_user_type: bool = False):
        self.parent = parent
        self.node_type = node_type.strip() if node_type is not None else 'root'
        self.name = ''
        self.nodes = []
        self.is_user_type = is_user_type

    def add_child(self, node_type):
        """
        Adds a child node to a particular node
        :param node_type: input node's type name
        :return: child node created
        """
        node = SyntaxTree(self, node_type)
        self.nodes.append(node)
        return node

    def first_child(self):
        """
        Takes a first child from a node
        :return: first child node
        """
        return self.nodes[0]

    def second_child(self):
        """
        Takes a second child from a node
        :return: second child node
        """
        return self.nodes[1]

    def accept(self, visitor: Type[SyntaxTreeVisitor], data=None):
        """
        Invokes the correct visitor's method depending on the node's type
        :param visitor: target visitor
        :param data: data associated with the node
        :return: result of visitor's invocation
        """
        if self.node_type == 'array':
            return visitor.visit_array(self, data)
        elif self.node_type == 'list':
            return visitor.visit_list(self, data)
        elif self.node_type == 'map':
            return visitor.visit_map(self, data)
        elif self.node_type == 'int':
            return visitor.visit_int(self, data)
        elif self.node_type == 'bool':
            return visitor.visit_bool(self, data)
        elif self.node_type == 'float':
            return visitor.visit_float(self, data)
        elif self.node_type == 'string':
            return visitor.visit_string(self, data)
        elif self.is_user_type:
            return visitor.visit_obj(self, data)

    def is_container_type(self) -> bool:
        """
        Returns whether a node's type is of container type or not
        :return: if type is map or list - true, false otherwise
        """
        return self.node_type in ['list', 'map']

    def is_array_type(self) -> bool:
        """
        Returns whether a node's type is of array type or not
        :return: if type is array - true, false otherwise
        """
        return self.node_type == 'array'

    def is_primitive_type(self) -> bool:
        """
        Returns whether a node's type is of primitive type
        :return: if type is int, float, string - true, false otherwise
        """
        return self.node_type in ['int', 'float', 'string']

    def is_root(self) -> bool:
        """
        Returns whether a node is the root node or not
        :return: if root - returns true, otherwise false
        """
        return self.node_type == 'root'

    @staticmethod
    def of(expression_list: List[str]) -> SyntaxTree:
        """
        Parses input expressions and build a SyntaxTree for them
        :param expression_list: source expressions
        :return: resultant syntax tree instance
        """
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
        """
        Returns the stringifed representation of a syntax tree
        :param buf: buffer to append the result
        :param ident: amount of spaces to insert between parent-child
        :return: string buffer holding the textual representation
        """
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
    """
    This is an abstract class which defines the API of handling different types. Depending on the target node's type
    corresponding visitor's method is invoked. This allows to split processing code to a different methods.
    """

    @abstractmethod
    def visit_array(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "array" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_list(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "list" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_map(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "map" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_int(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "int" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_float(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "float" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_string(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "string" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_bool(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "bool" type node
        :param node: target node
        :param data: related data item
        """
        pass

    @abstractmethod
    def visit_obj(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "object" type node
        :param node: target node
        :param data: related data item
        """
        pass

    def visit_void(self, node: SyntaxTree, data):
        """
        This method is invoked then processing "void" type node
        :param node: target node
        :param data: related data item
        """
        return None

    def render(self, tree: SyntaxTree, data=None):
        """
        Pass visitor to a tree, during the invocation one of the vistor methods will be invoked, depending on the
        target node type
        :param tree: target tree node
        :param data: related data item
        """
        return tree.accept(self, data)
