"""
NOTE: this file was forked from the following repo (Apache2)
https://github.com/bazelbuild/rules_nodejs/blob/c47b770a122e9614516df2e3fdca6fe0bf6e3420/packages/esbuild/helpers.bzl

Local changes not in upstream:
https://github.com/bazelbuild/rules_nodejs/pull/2563

Utility helper functions for the esbuild rule
"""

load("@build_bazel_rules_nodejs//third_party/github.com/bazelbuild/bazel-skylib:lib/paths.bzl", "paths")

def strip_ext(f):
    "Strips the extension of a file."
    return f.short_path[:-len(f.extension) - 1]

def resolve_js_input(f, inputs):
    """Find a corresponding javascript entrypoint for a provided file

    Args:
        f: The file where its basename is used to match the entrypoint
        inputs: The list of files where it should take a look at

    Returns:
        Returns the file that is the corresponding entrypoint
    """
    if f.extension == "js" or f.extension == "mjs":
        return f

    no_ext = strip_ext(f)
    for i in inputs:
        if i.extension == "js" or i.extension == "mjs":
            if strip_ext(i) == no_ext:
                return i
    fail("Could not find corresponding javascript entry point for %s. Add the %s.js to your deps." % (f.path, no_ext))

def filter_files(input, endings = [".js"]):
    """Filters a list of files for specific endings

    Args:
        input: The depset or list of files
        endings: The list of endings that should be filtered for

    Returns:
        Returns the filtered list of files
    """

    # Convert input into list regardles of being a depset or list
    input_list = input.to_list() if type(input) == "depset" else input
    filtered = []

    for file in input_list:
        for ending in endings:
            if file.path.endswith(ending):
                filtered.append(file)
                continue

    return filtered

def generate_path_mapping(package_name, path):
    """Generate a path alias mapping for a jsconfig.json

    For example: {"@my-alias/*": [ "path/to/my-alias/*" ]},

    Args:
        package_name: The module name
        path: The base path of the package
    """

    pkg = {}

    # entry for the barrel files favor mjs over normal as it results
    # in smaller bundles
    pkg[package_name] = [
        path + "/index.mjs",
        path,
    ]

    # A glob import for deep package imports
    pkg[package_name + "/*"] = [path + "/*"]

    return pkg

def write_jsconfig_file(ctx, path_alias_mappings):
    """Writes the js config file for the path alias mappings.

    Args:
        ctx: The rule context
        path_alias_mappings: Dict with the mappings

    Returns:
        File object reference for the jsconfig file
    """

    # The package path, including an "external/repo_name/" prefix if the package is in
    # an external repo.
    rule_path = paths.join(ctx.label.workspace_root, paths.dirname(ctx.build_file_path))

    # Replace all segments in the path with .. join them with "/" and postfix
    # it with another / to get a relative path from the build file dir
    # to the workspace root.
    if len(rule_path) == 0:
        base_url_path = "."
    else:
        base_url_path = "/".join([".." for segment in rule_path.split("/")]) + "/"

    # declare the jsconfig_file
    jsconfig_file = ctx.actions.declare_file("%s.config.json" % ctx.attr.name)

    # write the config file
    ctx.actions.write(
        output = jsconfig_file,
        content = struct(compilerOptions = struct(
            rootDirs = ["."],
            baseUrl = base_url_path,
            paths = path_alias_mappings,
        )).to_json(),
    )

    return jsconfig_file
