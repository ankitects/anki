# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Syntax Tree API
"""

from __future__ import annotations
from abc import ABC, abstractmethod
from typing import List

from typing_extensions import Type


def validate_list(node: SyntaxTree):
    """
    Validates a node with List type
    :param node: target node
    :raises Exception if size of inner nodes is not 1 (list can have exactly only 1 inner type)
    """
    if len(node.nodes) != 1:
        raise Exception('Array/List can have only 1 inner-type')


def validate_map(node: SyntaxTree):
    """
    Validates a node with Map type
    :param node: target node
    :raises Exception if size of inner nodes is not 2 (maps can have exactly only 2 inner type: key and value)
    """
    if len(node.nodes) != 2:
        raise Exception('Map must have 2 inner-types: key and value')


def validate_primitive(node: SyntaxTree):
    """
    Validates a node with non-container type (int, long, string, float, boolean)
    :param node: target node
    :raises Exception if size of inner nodes is not empty
    """
    if len(node.nodes) > 0:
        raise Exception('Primitive type cannot have inner-types')


def is_primitive_type(node: SyntaxTree):
    return node.parent.is_root() or node.parent.is_array_type() or node.parent.user_type


class SyntaxTree:
    """
    Tree structure to store parsed typing expression for a particular test suite
    """

    def __init__(self, parent: SyntaxTree = None, node_type: str = None, user_type: bool = False):
        self.parent = parent
        self.node_type = node_type.strip() if node_type is not None else 'root'
        self.name = ''
        self.nodes = []
        self.user_type = user_type

    def add_child(self, node_type, user_type: bool = False):
        """
        Adds a child node to a particular node
        :param node_type: input node's type name
        :param user_type: if a node's type is a custom user type (class, struct)
        :return: child node created
        """
        node = SyntaxTree(self, node_type, user_type)
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

    def result_subtree(self) -> SyntaxTree:
        """
        Takes a second child from a node
        :return: second child node
        """
        sub_tree = SyntaxTree()
        sub_tree.node_type = 'root'
        sub_tree.nodes = [self.nodes[-1]]
        return sub_tree

    def accept(self, visitor: Type[SyntaxTreeVisitor], context=None):
        """
        Invokes the correct visitor's method depending on the node's type
        :param visitor: target visitor
        :param context: data associated with the node
        :return: result of visitor's invocation
        """
        if self.node_type == 'array':
            validate_list(self)
            return visitor.visit_array(self, context)
        elif self.node_type == 'list':
            validate_list(self)
            return visitor.visit_list(self, context)
        elif self.node_type == 'map':
            validate_map(self)
            return visitor.visit_map(self, context)
        elif self.node_type == 'int':
            validate_primitive(self)
            return visitor.visit_int(self, context)
        elif self.node_type == 'long':
            validate_primitive(self)
            return visitor.visit_long(self, context)
        elif self.node_type == 'bool':
            validate_primitive(self)
            return visitor.visit_bool(self, context)
        elif self.node_type == 'float':
            validate_primitive(self)
            return visitor.visit_float(self, context)
        elif self.node_type == 'string':
            validate_primitive(self)
            return visitor.visit_string(self, context)
        elif self.user_type:
            return visitor.visit_obj(self, context)

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
        user_types = []
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
                elif c in '(<':
                    node = node.add_child(buf)
                elif c == '[':
                    node = node.add_child(buf, buf in user_types)
                elif c in ',)':
                    node.add_child(buf)
                elif c == ']':
                    node.name = buf
                elif c == '>':
                    node.node_type = buf
                    node.user_type = True
                    user_types.append(buf)
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
    def visit_array(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "array" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_list(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "list" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_map(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "map" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_int(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "int" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_long(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "long" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_float(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "float" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_string(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "string" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_bool(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "bool" type node
        :param node: target node
        :param context: related data item
        """
        pass

    @abstractmethod
    def visit_obj(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "object" type node
        :param node: target node
        :param context: related data item
        """
        pass

    def visit_void(self, node: SyntaxTree, context):
        """
        This method is invoked then processing "void" type node
        :param node: target node
        :param context: related data item
        """
        return ''

    def render(self, tree: SyntaxTree, context):
        """
        Pass visitor to a tree, during the invocation one of the vistor methods will be invoked, depending on the
        target node type
        :param tree: target tree node
        :param context: related data item
        """
        return tree.accept(self, context)
