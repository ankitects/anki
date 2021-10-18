mypy 0.9 assumes stub files will be installed in site-packages, but they are not
in Bazel's case, so we need to hack around the issue.
