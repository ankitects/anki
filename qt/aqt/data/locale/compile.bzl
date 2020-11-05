def compile(name, po_file, pot_file, mo_file):
    native.genrule(
        name = name,
        srcs = [po_file, pot_file],
        outs = [mo_file],
        # homebrew gettext is not on path by default
        cmd = """\
export PATH="$$PATH":/usr/local/opt/gettext/bin
msgmerge -q $(location {po_file}) $(location {pot_file}) | msgfmt - --output-file=$(location {mo_file})
""".format(
            po_file = po_file,
            pot_file = pot_file,
            mo_file = mo_file,
        ),
        message = "Building translation",
    )

_langs = [
    "af",
    "ar",
    "bg",
    "ca",
    "cs",
    "da",
    "de",
    "el",
    "en-GB",
    "eo",
    "es",
    "et",
    "eu",
    "fa",
    "fi",
    "fr",
    "ga-IE",
    "gl",
    "he",
    "hi-IN",
    "hr",
    "hu",
    "hy-AM",
    "it",
    "ja",
    "jbo",
    "kab",
    #    "km",
    "ko",
    "la",
    "mn",
    "mr",
    "ms",
    "nb-NO",
    "nl",
    "nn-NO",
    "oc",
    "or",
    "pl",
    "pt-BR",
    "pt-PT",
    "ro",
    "ru",
    "sk",
    "sl",
    "sr",
    "sv-SE",
    "th",
    "tr",
    "uk",
    #    "ur",
    "vi",
    "zh-CN",
    "zh-TW",
]

def compile_all(name, visibility):
    pot_file = "@aqt_po//:desktop/anki.pot"
    mo_files = []
    for lang in _langs:
        po_file = "@aqt_po//:desktop/{}/anki.po".format(lang)
        mo_file = "{}/LC_MESSAGES/anki.mo".format(lang)
        mo_files.append(mo_file)
        compile(lang, po_file, pot_file, mo_file)

    native.filegroup(
        name = name,
        srcs = mo_files,
        visibility = visibility,
    )
