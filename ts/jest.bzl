load("@npm//@bazel/typescript:index.bzl", "ts_library")
load("@npm//jest-cli:index.bzl", _jest_test = "jest_test")

def jest_test(deps, data = [], name = "jest"):
    "Build *.test.ts into a library, then test it with Jest."

    # compile sources
    ts_library(
        name = name + "_lib",
        srcs = native.glob(["*.test.ts"]),
        tsconfig = "//ts:tsconfig.json",
        deps = deps + [
            "@npm//@types/jest",
        ],
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
        data = data + [
            name + "_lib",
            "//ts:jest.config.js",
        ],
        target_compatible_with = select({
            "@platforms//os:osx": [],
            "@platforms//os:linux": [],
            "//conditions:default": ["@platforms//os:linux"],
        }),
    )
