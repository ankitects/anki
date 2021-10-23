To achieve reproducible builds we use pip-tools to lock packages to a particular version.
Sadly this is complicated by the fact that Python can only tell us which transitive dependencies
are required by actually installing packages, and if you run pip-tools on a Mac or Linux machine,
it will miss packages that are required on Windows and vice versa.

Currently the Windows dependencies are a strict superset, so the package locks need to be generated
on a Windows machine. To do so, run "bazel run update" from this folder.

pyqt is handled separately - see pyqt/
