# this is a hack to invoke prettier directly from Bazel
node=$(pwd)/$1
node_dir=$(dirname $node)
export PATH="$node_dir:$PATH"
(cd "${BUILD_WORKSPACE_DIRECTORY}" && node_modules/.bin/prettier \
    --config .prettierrc --write \
    $BUILD_WORKING_DIRECTORY )
