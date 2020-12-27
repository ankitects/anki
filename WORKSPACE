workspace(
    name = "net_ankiweb_anki",
    managed_directories = {"@npm": [
        "ts/node_modules",
    ]},
)

load(":pylib_deps.bzl", "pylib_deps")
pylib_deps()

load(":desktop_extradeps.bzl", desktop_extradeps = "register_repos")
desktop_extradeps()

load(":pylib_defs.bzl", pylib_setup_deps = "setup_deps")
pylib_setup_deps()

load(":desktop_extradefs.bzl", desktop_extradefs = "setup_deps")
desktop_extradefs()


load(":pylib_late_deps.bzl", pylib_setup_late_deps = "setup_late_deps")
pylib_setup_late_deps()

load(":desktop_late_deps.bzl", "setup_late_deps")
setup_late_deps()
