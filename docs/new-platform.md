# Building on a new platform

## Upstream prerequisites

- A recent Qt, PyQt5 and PyQtWebEngine must work on your platform. Chances are wheels
  for your platform are not available on PyPI, so you will need to have a working
  version installed already, either from your Linux distro's archives, or by building
  from source.
- Rust and Node must support your platform.

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

- Anki uses the Python 'orjson' module. If it's not available on your system,
  you will need to [patch it out](https://github.com/ankitects/anki/pull/752#issuecomment-748861582), and remove references to it in the build scripts.

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

If you node don't provide a binary for your platform and you have a local copy
installed, you may be able to modify node_repositories() in /defs.bzl to point
to your [local npm installation](https://bazelbuild.github.io/rules_nodejs/install.html).

## Submitting changes

If the changes to support your platform do not require platform-specific hacks,
a PR that adds them is welcome - please see [Contributing](./contributing.md) for more.
