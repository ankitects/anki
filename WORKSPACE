workspace(
    name = "anki",
    managed_directories = {"@npm": [
        "ts/node_modules",
    ]},
)

load(":packages.bzl", "register_deps")

register_deps()

load(":defs.bzl", "setup_deps")

setup_deps()

load(":late_deps.bzl", "setup_late_deps")

setup_late_deps()
