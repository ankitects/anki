# this is a hack to invoke prettier directly from Bazel
ts=${BUILD_WORKSPACE_DIRECTORY}/ts
(cd $ts && ./node_modules/.bin/prettier --config .prettierrc --write . $ts/../qt/aqt/data/web/js)
