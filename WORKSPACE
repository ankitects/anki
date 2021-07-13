workspace(
    name = "ankidesktop",
    managed_directories = {"@npm": [
        "ts/node_modules",
    ]},
)

load(":repos.bzl", "register_repos")

register_repos()

load(":defs.bzl", "setup_deps")

setup_deps()

load(":late_deps.bzl", "setup_late_deps")

setup_late_deps()
