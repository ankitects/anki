load("@npm//@bazel/typescript:index.bzl", "ts_library")
load("@esbuild_toolchain//:esbuild.bzl", esbuild = "esbuild_macro")
load("@npm//jest-cli:index.bzl", _jest_test = "jest_test")

def jest_test(deps, name = "jest"):
    "Build *.test.ts into a library, then test it with Jest."

    ts_sources = native.glob(["*.test.ts"])

    # compile sources for type checking
    ts_library(
        name = name + "_lib",
        srcs = ts_sources,
        tsconfig = "//ts:tsconfig.json",
        deps = deps + [
            "@npm//@types/jest",
        ],
    )

    # bundle each test file up with its dependencies for jest
    bundled_srcs = []
    for ts_src in ts_sources:
        base = ts_src.replace(".test.ts", "")
        bundle_name = base + ".bundle.test"
        bundled_srcs.append(bundle_name)
        esbuild(
            name = bundle_name,
            args = [
                "--resolve-extensions=.mjs,.js",
                "--log-level=warning",
                "--platform=node",
                "--external:protobufjs",
                "--keep-names",
            ],
            entry_point = ts_src,
            output = bundle_name + ".js",
            deps = [
                name + "_lib",
            ] + deps,
            # the code shaking saves close to a second off the deckconfig/lib.test.ts test
            minify = True,
        )

    # then test them
    _jest_test(
        name = name,
        args = [
            "--no-cache",
            "--no-watchman",
            "--ci",
            "--colors",
            "--config",
            "$(location //ts:jest.config.js)",
        ],
        data = bundled_srcs + [
            "//ts:jest.config.js",
        ],
        target_compatible_with = select({
            "@platforms//os:osx": [],
            "@platforms//os:linux": [],
            "//conditions:default": ["@platforms//os:linux"],
        }),
    )
