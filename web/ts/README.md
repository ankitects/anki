Anki's TypeScript and Sass dependencies. Some TS/JS code is also
stored separately in ../qt/aqt/data/web/.

To update all dependencies:

./update.sh

To add a new dev dependency, use something like:

./add.sh -D @rollup/plugin-alias
