def _generate_html_page_impl(ctx):
    ctx.actions.expand_template(
        template = ctx.file.template,
        output = ctx.outputs.output,
        substitutions = {
            "{PAGE}": ctx.attr.page,
        },
    )

generate_html_page = rule(
    implementation = _generate_html_page_impl,
    attrs = {
        "template": attr.label(
            default = Label("//ts:page.html"),
            allow_single_file = [".html"],
        ),
        "page": attr.string(mandatory = True),
        "output": attr.output(mandatory = True),
    },
)

def generate_page(page, name = "page"):
    output = page + ".html"

    generate_html_page(
        name = name,
        page = page,
        output = output,
        visibility = ["//visibility:public"],
    )
