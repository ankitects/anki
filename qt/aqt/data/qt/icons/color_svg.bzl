def color_svg(name, extra_colors = [], visibility = ["//qt:__submodules__"]):
    native.genrule(
        name = name,
        srcs = ["mdi-themed"],
        outs = [
                name + "-light.svg",
            ] + [
                # additional light colors
                "{}{}{}".format(
                    name,
                    "-{}".format(color),
                    "-light.svg"
                ) for color in extra_colors
            ] + [
                name + "-dark.svg",
            ] + [
                # additional dark colors
                "{}{}{}".format(
                    name,
                    "-{}".format(color),
                    "-dark.svg"
                ) for color in extra_colors
            ],
        cmd = "$(location color_svg) {}.svg {} $(OUTS) $(SRCS)".format(
                name, ":".join(["FG"] + extra_colors)
            ),
        tools = [
            "color_svg",
        ],
    )
