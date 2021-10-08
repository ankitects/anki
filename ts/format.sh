# this is a hack to invoke prettier directly from Bazel
(cd "${BUILD_WORKSPACE_DIRECTORY}" && node_modules/.bin/prettier --config .prettierrc --write ts qt/aqt/data/web/js)
