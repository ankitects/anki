load("@py_deps//:requirements.bzl", "requirement")

def orjson_if_available():
    "Include orjson if it's listed in requirements.txt."
    target = requirement("orjson")
    if "not_found" in target:
        return []
    else:
        return [target]
