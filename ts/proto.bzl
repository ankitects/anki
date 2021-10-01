load("@npm//protobufjs-cli-taylorm:index.bzl", "pbjs", "pbts")

def protobuf_ts(name, src):
    pbjs(
        name = "%s_js" % name,
        outs = ["%s.mjs" % name],
        args = [
            "-t",
            "json-module",
            "-w",
            "es6",
            "$(execpath %s)" % src,
            "-o",
            "$(execpath %s.mjs)" % name,
        ],
        data = [
            src,
            "@npm//protobufjs-taylorm",
        ],
    )

    pbjs(
        name = "%s_js_static" % name,
        outs = ["%s.static.js" % name],
        args = [
            "-t",
            "static-module",
            "$(execpath %s)" % src,
            "-o",
            "$(execpath %s.static.js)" % name,
        ],
        data = [
            src,
            "@npm//protobufjs-taylorm",
        ],
    )

    pbts(
        name = "%s_ts" % name,
        outs = ["%s.d.ts" % name],
        args = [
            "-w",
            "es6",
            "$(execpath :%s.static.js)" % name,
            "-o",
            "$(execpath %s.d.ts)" % name,
        ],
        data = [
            ":%s.static.js" % name,
        ],
    )

    # native.filegroup(
    #     name = name,
    #     srcs = [
    #         "%s.js" % name,
    #         "%s.d.ts" % name,
    #     ],
    # )
