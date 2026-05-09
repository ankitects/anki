#!/bin/bash
# - sync ftl
# - ./copy-core-string.sh scheduling-review browsing-sidebar-card-state-review
# - confirm changes in core-repo/ correct
# - commit and push changes
# - ensure string in template isn't in the 'no need to translate' section
# - update submodule in main repo
./ftl string copy ftl/core-repo/core ftl/core-repo/core $1 $2
