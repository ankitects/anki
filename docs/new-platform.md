# Building on a new platform

## Upstream prerequisites

- A recent Qt, PyQt5 and PyQtWebEngine must work on your platform. Chances are wheels
  for your platform are not available on PyPI, so you will need to have a working
  version installed already, either from your Linux distro's archives, or by building
  from source.
- Rust and Node must support your platform.
- If the Bazel Rust and Node rules do not support your platform, extra work may be required.

## 32 bit builds

Bazel does not support running on a 32-bit host. It may be theoretically
possible to cross-compile from a 64-bit host, but this is not something we've
tried, and it will likely not work out of the box.

## Protobuf & Python

- Edit /platforms/BUILD.bzl and add the new platform. Available platforms
  and CPUs can be found here: https://github.com/bazelbuild/platforms
- Edit /protobuf.bzl and add the new platform and a link to a protoc binary.
- Edit /pylib/anki/BUILD.bazel and add the new platform and the relevant
  Python wheel name for your platform.

Example of the above changes:
https://github.com/ankitects/anki/commit/db3308e788f20b188e84add40d6a1dce5bf726a0

- We need to tell the build process to use your local PyQt5 install, instead of
  fetching it from PyPI. Locate your Python site-packages folder where the PyQt5
  folder is, and add the following to user.bazelrc in the project folder root:

```
build --action_env=PYTHON_SITE_PACKAGES=/path/to/site-packages
```

- Anki uses the Python 'orjson' module to speed up DB access. If you're on a
  platform that can not build orjson, you can remove it from
  pip/requirements.txt to skip it during running/building, but DB operations
  will be slower.

  The py_wheel() rule in pylib/anki/BUILD.bazel adds an orjson requirement to
  the generated Anki wheel on x86_64. If you have removed orjson, you'll want to
  remove that line. If you have successfully built orjson for another platform,
  you'll want to adjust that line to include your platform.

## Rust

- Ensure you have Rust installed (eg via rustup)
- Edit /Cargo.toml in this project, and add your platform
  to the targets. Check that the platform [is supported](https://github.com/bazelbuild/rules_rust/blob/master/rust/platform/platform.bzl) by rules_rust; if it is not, you'll
  need to send a PR to them adding it. You can test your change locally by modifying
  /repos.bzl in this project to point to a local rules_rust checkout.
- Edit /cargo/BUILD.request.bazel and add the new platform.
- Run update.py in the /cargo folder.

Examples of the required changes:

- https://github.com/ankitects/anki/commit/eca27b371000e77b68cb4c790b44848507ca3883
- https://github.com/ankitects/anki/commit/3f3f4b5c3640a7d1f4eec02f326fda93214ec34b

## NodeJS

If node doesn't provide a binary for your platform and you have a local copy
installed, you can create a local_node folder in the project root, symlink in
your local installation, and modify defs.bzl.

```patch
diff --git a/defs.bzl b/defs.bzl
index eff3d9df2..fb2e9f7fe 100644
--- a/defs.bzl
+++ b/defs.bzl
@@ -41,7 +41,15 @@ def setup_deps():
         python_runtime = "@python//:python",
     )

-    node_repositories(package_json = ["@ankidesktop//ts:package.json"])
+    native.local_repository(
+        name = "local_node",
+        path = "local_node",
+    )
+
+    node_repositories(
+        package_json = ["@ankidesktop//ts:package.json"],
+        vendored_node = "@local_node//:node",
+    )

     yarn_install(
         name = "npm",
diff --git a/local_node/BUILD.bazel b/local_node/BUILD.bazel
new file mode 100644
index 000000000..aa0c473ae
--- /dev/null
+++ b/local_node/BUILD.bazel
@@ -0,0 +1 @@
+exports_files(["node/bin/node"] + glob(["node/lib/node_modules/**"]))
diff --git a/local_node/WORKSPACE b/local_node/WORKSPACE
new file mode 100644
index 000000000..e69de29bb
diff --git a/local_node/node/bin/node b/local_node/node/bin/node
new file mode 120000
index 000000000..d7b371472
--- /dev/null
+++ b/local_node/node/bin/node
@@ -0,0 +1 @@
+/usr/local/bin/node
\ No newline at end of file
diff --git a/local_node/node/lib/node_modules b/local_node/node/lib/node_modules
new file mode 120000
index 000000000..23dd0736e
--- /dev/null
+++ b/local_node/node/lib/node_modules
@@ -0,0 +1 @@
+/usr/local/lib/node_modules
\ No newline at end of file
```

## Submitting changes

If the changes to support your platform do not require platform-specific hacks,
a PR that adds them is welcome - please see [Contributing](./contributing.md) for more.
