We handle PyQt specially for a few reasons:

-   It ships with a few issues in its .pyi files that we want to correct.
-   Bazel's Python tools install each package in a separate folder, but the
    various PyQt packages expect to be installed in the same location, and
    will give runtime linking issues when they are split up.

To update the version lock file, change to the folder with requirements.in,
modify the file, and then run "bazel run //python:update". PyQt does not
depend on platform-specific packages, so the update can be run on any platform.
