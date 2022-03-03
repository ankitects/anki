set_build_path(VARS.get("build"))

excluded_source_prefixes = [
    "ctypes.test",
    "distutils.tests",
    "idlelib",
    "lib2to3.tests",
    "test",
    "tkinter",
    "win32comext",
    "win32com",
    "win32",
    "pythonwin",
]

excluded_resource_suffixes = [
    ".pyi",
    ".pyc",
    "py.typed",
]

included_resource_packages = [
    "anki",
    "aqt",
    "lib2to3",
    "certifi",
    "jsonschema",
]

def handle_resource(policy, resource):
    if type(resource) == "PythonModuleSource":
        resource.add_include = True
        for prefix in excluded_source_prefixes:
            if resource.name.startswith(prefix):
                resource.add_include = False

        # if resource.add_include:
        #     print("src", resource.name, resource.add_include)

    elif type(resource) == "PythonExtensionModule":
        resource.add_include = True
        if resource.name.startswith("win32"):
            resource.add_include = False

        #print("ext", resource.name, resource.add_include)

    elif type(resource) == "PythonPackageResource":
        for prefix in included_resource_packages:
            if resource.package.startswith(prefix):
                resource.add_include = True
        for suffix in excluded_resource_suffixes:
            if resource.name.endswith(suffix):
                resource.add_include = False

        # aqt web resources can be stored in binary
        if resource.package == "aqt":
            if not resource.name.startswith("data/web"):
                resource.add_location = "filesystem-relative:lib"

        # if resource.add_include:
        #     print("rsrc", resource.package, resource.name, resource.add_include)

    elif type(resource) == "PythonPackageDistributionResource":
        #print("dist", resource.package, resource.name, resource.add_include)
        pass

        # elif type(resource) == "File":
        #     print(resource.path)

    elif type(resource) == "File":
        if (
            resource.path.startswith("win32") or
            resource.path.startswith("pythonwin") or
            resource.path.startswith("pywin32")
        ):
            exclude = (
                "tests" in resource.path or
                "benchmark" in resource.path or
                "__pycache__" in resource.path
            )
            if not exclude:
                resource.add_include = True
                resource.add_location = "filesystem-relative:lib"

        if ".dist-info" in resource.path:
            resource.add_include = False

    else:
        print("unexpected type", type(resource))

def make_exe():
    if BUILD_TARGET_TRIPLE == "aarch64-unknown-linux-gnu":
        dist = PythonDistribution(
            url = "https://github.com/ankitects/python-build-standalone/releases/download/anki-2021-10-15/cpython-3.9.7-aarch64-unknown-linux-gnu-pgo-20211013T1538.tar.zst",
            sha256 = "d25de2da9dc1b988350570c0ade9a79587cafeaad9ba1965fcec12c4ecaa9d98",
        )

    elif BUILD_TARGET_TRIPLE == "x86_64-unknown-linux-gnu":
        dist = PythonDistribution(
            url = "https://github.com/ankitects/python-build-standalone/releases/download/anki-2022-02-18/cpython-3.9.10-x86_64-unknown-linux-gnu-pgo-20220218T1329.tar.zst",
            sha256 = "83167967d09ada10554c0a1ed8228bcfac1117669880d2979578017565537400",
        )

    else:
        dist = default_python_distribution()

    policy = dist.make_python_packaging_policy()

    policy.file_scanner_classify_files = True
    policy.include_classified_resources = False

    policy.allow_files = True
    policy.file_scanner_emit_files = True
    policy.include_file_resources = False

    policy.include_distribution_sources = False
    policy.include_distribution_resources = False
    policy.include_non_distribution_sources = False
    policy.include_test = False

    policy.resources_location = "in-memory"
    policy.resources_location_fallback = "filesystem-relative:lib"

    policy.register_resource_callback(handle_resource)

    policy.bytecode_optimize_level_zero = False
    policy.bytecode_optimize_level_two = True

    python_config = dist.make_python_interpreter_config()

    # detected libs do not need this, but we add extra afterwards
    python_config.module_search_paths = ["$ORIGIN/lib"]
    python_config.optimization_level = 2
    if BUILD_TARGET_TRIPLE == "x86_64-apple-darwin":
        # jemalloc currently fails to build when run under Rosetta
        python_config.allocator_backend = "default"

    python_config.run_command = "import aqt; aqt.run()"

    exe = dist.to_python_executable(
        name = "anki",
        packaging_policy = policy,
        config = python_config,
    )

    exe.windows_runtime_dlls_mode = "always"

    # set in main.rs
    exe.windows_subsystem = "console"

    venv_path = "venv"

    resources = exe.read_virtualenv(VARS.get("venv"))
    exe.add_python_resources(resources)

    return exe

def make_embedded_resources(exe):
    return exe.to_embedded_resources()

def make_install(exe):
    files = FileManifest()
    files.add_python_resource(".", exe)
    return files

register_target("exe", make_exe)
register_target("resources", make_embedded_resources, depends = ["exe"], default_build_script = True)
register_target("install", make_install, depends = ["exe"], default = True)
resolve_targets()
