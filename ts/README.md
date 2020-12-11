Anki's TypeScript and Sass dependencies. Some TS/JS code is also
stored separately in ../qt/aqt/data/web/.

To add a new dev dependency, use something like:

bazel run @nodejs//:yarn add @rollup/plugin-alias -- -D
