import json

from testing.framework.syntax_tree import parse_grammar


class CodegenContext:
    pass


def generate_solution_template(func_name, csv_tests, solution_generator, argtype_generator, usertype_generator):
    args = []
    rows = csv_tests.strip().split('\n')
    syntax_tree = parse_grammar(rows[0].split(';'))
    user_types = usertype_generator.get_types_src(syntax_tree)
    for node in syntax_tree.nodes[:-1]:
        args.append([argtype_generator.render(node, syntax_tree), node.name])
    result_type = argtype_generator.render(syntax_tree.nodes[-1], syntax_tree)
    return solution_generator.generate_src(func_name, args, result_type, user_types)


def generate_function_test_invocations(rows, syntax_tree, invocation_args_generator):
    invocations = []
    for row in rows:
        invocation_args = []
        cols = row.split(';')
        if len(syntax_tree.nodes) != len(cols):
            raise Exception('Invalid row column count')
        for i, col in enumerate(cols):
            data = json.loads(col)
            invocation_args.append(invocation_args_generator.render_args(syntax_tree.nodes[i], syntax_tree, data))
        invocations.append(invocation_args)
    return invocations


def generate_testing_program(solution_src, func_name, csv_tests, invocations_args_generator, testing_src_generator):
    rows = csv_tests.strip().split('\n')
    syntax_tree = parse_grammar(rows[0].split(';'))
    invocations = generate_function_test_invocations(rows[1:], syntax_tree, invocations_args_generator)
    src = testing_src_generator.generate_testing_src(solution_src, func_name, invocations)
    return src
