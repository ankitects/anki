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

# homebrew gettext is not on path by default
_pathfix = """export PATH="$$PATH":/usr/local/opt/gettext/bin\n"""

def update_po(name, po_file_in, po_file_out, pot_file, visibility):
    "Merge old .po and latest strings from .pot into new .po"
    native.genrule(
        name = name,
        srcs = [po_file_in, pot_file],
        outs = [po_file_out],
        cmd = _pathfix + """\
msgmerge -q --no-wrap $(location {po_file_in}) $(location {pot_file}) > $(location {po_file_out})
""".format(
            po_file_in = po_file_in,
            po_file_out = po_file_out,
            pot_file = pot_file,
        ),
        message = "Updating translation",
        visibility = visibility,
    )

def compile_po(name, po_file, mo_file):
    "Build .mo file from an updated .po file."
    native.genrule(
        name = name,
        srcs = [po_file],
        outs = [mo_file],
        # homebrew gettext is not on path by default
        cmd = _pathfix + """\
cat $(location {po_file}) | msgfmt - --output-file=$(location {mo_file})
""".format(
            po_file = po_file,
            mo_file = mo_file,
        ),
        message = "Compiling translation",
    )

def build_template(name, pot_file, srcs):
    "Build .pot file from Python files."
    native.genrule(
        name = name,
        srcs = srcs,
        outs = [pot_file],
        cmd = _pathfix + """\
all=all.files
for i in $(SRCS); do
    echo $$i >> $$all
done
xgettext -cT: -s --no-wrap --files-from=$$all --output=$(OUTS)
rm $$all
""",
        message = "Building .pot template",
    )

def update_all_po_files(name, pot_file, visibility):
    # merge external .po files with updated .pot
    po_files = []
    for lang in _langs:
        po_file_in = "@aqt_po//:desktop/{}/anki.po".format(lang)
        po_file_out = "{}/anki.po".format(lang)
        update_po(
            name = lang + "_po",
            po_file_in = po_file_in,
            po_file_out = po_file_out,
            pot_file = pot_file,
            visibility = visibility,
        )
        po_files.append(po_file_out)

    native.filegroup(
        name = name,
        srcs = po_files,
        visibility = visibility,
    )

def compile_all_po_files(name, visibility):
    "Build all .mo files from .po files."
    mo_files = []
    for lang in _langs:
        po_file = "//qt/po:{}/anki.po".format(lang)
        mo_file = "{}/LC_MESSAGES/anki.mo".format(lang)
        compile_po(
            name = lang + "_mo",
            po_file = po_file,
            mo_file = mo_file,
        )
        mo_files.append(mo_file)

    native.filegroup(
        name = name,
        srcs = mo_files,
        visibility = visibility,
    )
