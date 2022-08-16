load("@npm//@bazel/esbuild:index.bzl", "esbuild")
load("@npm//jest-cli:index.bzl", _jest_test = "jest_test")

def jest_test(deps, name = "jest", protobuf = False, env = "node", debug = False):
    ts_sources = native.glob(["**/*.test.ts"])

    # bundle each test file up with its dependencies for jest
    bundled_srcs = []
    esbuild_extra_args = {}
    esbuild_extra_srcs = []
    if protobuf:
        esbuild_extra_args["inject"] = ["ts/protobuf-no-long.js"]
        esbuild_extra_srcs.append(
            "//ts:protobuf-no-long.js",
        )

    for ts_src in ts_sources:
        base = ts_src.replace(".test.ts", "")
        bundle_name = base + ".bundle.test"
        bundled_srcs.append(bundle_name)
        esbuild(
            name = bundle_name,
            args = dict(
                esbuild_extra_args,
                platform = "node",
                keepNames = True,
            ),
            entry_point = ts_src,
            output = bundle_name + ".js",
            srcs = esbuild_extra_srcs,
            deps = deps,
            # the code shaking saves close to a second off the deckoptions/lib.test.ts test
            minify = not debug,
            target_compatible_with = select({
                "@platforms//os:osx": [],
                "@platforms//os:linux": [],
                "//conditions:default": ["@platforms//os:linux"],
            }),
        )

    # then test them
    optional_jsdom_deps = [
        "@npm//jest-environment-jsdom",
    ] if env == "jsdom" else []

    # After starting Jest, open the url "chrome://inspect" in
    # a Chrome browser and inspect as remote target.
    debug_args = [
        "--run-in-band",
        "--node_options=--inspect-brk",
    ] if debug else []

    _jest_test(
        name = name,
        args = [
            "--no-cache",
            "--no-watchman",
            "--ci",
            "--colors",
            "--config",
            "$(location //ts:jest.config.js)",
            "--env=" + env,
        ] + debug_args,
        data = deps + bundled_srcs + [
            "//ts:jest.config.js",
        ] + optional_jsdom_deps,
        target_compatible_with = select({
            "@platforms//os:osx": [],
            "@platforms//os:linux": [],
            "//conditions:default": ["@platforms//os:linux"],
        }),
    )
