#!/bin/bash
# Replace version tags with commit hashes in github actions workflows
# Example: "uses: actions/checkout@v4"
# -> "uses: actions/checkout@11bd...683 #v4"

shopt -s globstar

for workflow in .github/**/*.yml; do
    grep -E "uses:[[:space:]]+[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*@v[0-9]+" "$workflow" |
    while read -r line; do
        action=$(echo "$line" | sed -E 's/.*uses:[[:space:]]+([^[:space:]#]+)@v[0-9]+.*/\1/')
        repo=$(echo "$action" | cut -d/ -f1,2)
        tag=$(echo "$line" | sed -E 's/.*@(v[0-9]+).*/\1/')

        commit_hash=$(git ls-remote "https://github.com/$repo.git" "refs/tags/$tag" | cut -f1)

        if [ -n "$commit_hash" ]; then
            sed -i.bak -E \
                "s|(uses:[[:space:]]+$action@)$tag|\1$commit_hash #$tag|g" \
                "$workflow"
            rm -f "$workflow.bak"
        fi
    done
done
