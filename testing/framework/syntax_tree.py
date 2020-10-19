class TreeNode:
    def __init__(self, parent=None, node_type=None):
        self.name = ''
        self.type = ''
        self.nodes = []
        self.parent = parent
        self.is_custom_type = False
        if node_type is not None:
            self.type = node_type.strip()


def add_child_node(parent, node_type):
    n = TreeNode(parent, node_type)
    parent.nodes.append(n)
    return n


def parse_grammar(expression_list):
    root = TreeNode(None, 'root')
    for expr in expression_list:
        parent = root
        node = root
        buf = ''
        for c in expr:
            if c in '()[]<>,':
                if buf != '':
                    if c == '(':
                        node = add_child_node(parent, buf)
                        parent = node
                    elif c == ')':
                        add_child_node(parent, buf)
                    elif c == '[':
                        node = add_child_node(parent, buf)
                    elif c == ',':
                        add_child_node(parent, buf)
                        node = parent
                    elif c == '<':
                        node = add_child_node(parent, buf)
                    elif c == '>':
                        node.type = buf
                        node.is_custom_type = True
                    elif c == ']':
                        node.name = buf
                    elif c == '>':
                        node.type = buf
                    buf = ''
                if c == ')':
                    node = parent
                    parent = node.parent
            else:
                buf += c
        if buf != '':
            node = add_child_node(parent, buf)

    return root


def tree_to_str(node, s='\n', ident=0):
    s += ' ' * ident
    if node.name != '':
        s += '{}::{}'.format(node.name, node.type)
    else:
        s += node.type
    s += '\n'

    for c in node.nodes:
        s = tree_to_str(c, s, ident + 3)
    return s
