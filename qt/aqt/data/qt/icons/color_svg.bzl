def color_svg(name, color = "FG", visibility = ["//qt:__submodules__"]):
    native.genrule(
        name = name,
        srcs = ["mdi-icons"],
        outs = [
            name + "-dark.svg",
            name + "-light.svg",
        ],
        cmd = "$(location color_svg) {} {}.svg $(OUTS) $(SRCS)".format(color, name),
        tools = [
            "color_svg",
        ],
    )
