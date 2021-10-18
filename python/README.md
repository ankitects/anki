To achieve reproducible builds we use pip-tools to lock packages to a particular version.
Sadly this is complicated by the fact that Python can only tell us which transitive dependencies
are required by actually installing packages, and if you run pip-tools on a Mac or Linux machine,
it will miss packages that are required on Windows and vice versa.

So we're stuck manually merging dependencies for now. To update deps:

- run 'bazel run update' to update requirements.txt for the current
  platform
- consult the git diff, and manually merge the changes, undoing the removal
  of items pinned on other platforms
- repeat the process on the other platform
- run the tests to ensure nothing has broken on either platform
- commit the changes to requirements.txt

At the time of writing, Macs and Linux machines have identical output - it is only
Windows that differs. But we should not assume that will always be the case.
