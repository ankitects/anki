To upgrade deps:

bazel run @nodejs//:yarn upgrade

To add a new dev dependency:

bazel run @nodejs//:yarn add @rollup/plugin-alias -- -D
