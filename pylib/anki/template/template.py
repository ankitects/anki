import re
from typing import Any, Callable, Dict, Pattern

from anki.hooks import runFilter
from anki.utils import stripHTML, stripHTMLMedia

# Matches a {{c123::clozed-out text::hint}} Cloze deletion, case-insensitively.
# The regex should be interpolated with a regex number and creates the following
# named groups:
#   - tag: The lowercase or uppercase 'c' letter opening the Cloze.
#   - content: Clozed-out content.
#   - hint: Cloze hint, if provided.
clozeReg = r"(?si)\{\{(?P<tag>c)%s::(?P<content>.*?)(::(?P<hint>.*?))?\}\}"

# Constants referring to group names within clozeReg.
CLOZE_REGEX_MATCH_GROUP_TAG = "tag"
CLOZE_REGEX_MATCH_GROUP_CONTENT = "content"
CLOZE_REGEX_MATCH_GROUP_HINT = "hint"

modifiers: Dict[str, Callable] = {}


def modifier(symbol) -> Callable[[Any], Any]:
    """Decorator for associating a function with a Mustache tag modifier.

    @modifier('P')
    def render_tongue(self, tag_name=None, context=None):
        return ":P %s" % tag_name

    {{P yo }} => :P yo
    """

    def set_modifier(func):
        modifiers[symbol] = func
        return func

    return set_modifier


def get_or_attr(obj, name, default=None) -> Any:
    try:
        return obj[name]
    except KeyError:
        return default
    except:
        try:
            return getattr(obj, name)
        except AttributeError:
            return default


class Template:
    # The regular expression used to find a #section
    section_re: Pattern = None

    # The regular expression used to find a tag.
    tag_re: Pattern = None

    # Opening tag delimiter
    otag = "{{"

    # Closing tag delimiter
    ctag = "}}"

    def __init__(self, template, context=None) -> None:
        self.template = template
        self.context = context or {}
        self.compile_regexps()

    def render(self, template=None, context=None, encoding=None) -> str:
        """Turns a Mustache template into something wonderful."""
        template = template or self.template
        context = context or self.context

        template = self.render_sections(template, context)
        result = self.render_tags(template, context)
        # if encoding is not None:
        #     result = result.encode(encoding)
        return result

    def compile_regexps(self) -> None:
        """Compiles our section and tag regular expressions."""
        tags = {"otag": re.escape(self.otag), "ctag": re.escape(self.ctag)}

        section = r"%(otag)s[\#|^]([^\}]*)%(ctag)s(.+?)%(otag)s/\1%(ctag)s"
        self.section_re = re.compile(section % tags, re.M | re.S)

        tag = r"%(otag)s(#|=|&|!|>|\{)?(.+?)\1?%(ctag)s+"
        self.tag_re = re.compile(tag % tags)

    def render_sections(self, template, context) -> str:
        """Expands sections."""
        while 1:
            match = self.section_re.search(template)
            if match is None:
                break

            section, section_name, inner = match.group(0, 1, 2)
            section_name = section_name.strip()

            # check for cloze
            val = None
            m = re.match(r"c[qa]:(\d+):(.+)", section_name)
            if m:
                # get full field text
                txt = get_or_attr(context, m.group(2), None)
                m = re.search(clozeReg % m.group(1), txt)
                if m:
                    val = m.group(CLOZE_REGEX_MATCH_GROUP_TAG)
            else:
                val = get_or_attr(context, section_name, None)

            replacer = ""
            inverted = section[2] == "^"
            if val:
                val = stripHTMLMedia(val).strip()
            if (val and not inverted) or (not val and inverted):
                replacer = inner

            template = template.replace(section, replacer)

        return template

    def render_tags(self, template, context) -> str:
        """Renders all the tags in a template for a context."""
        repCount = 0
        while 1:
            if repCount > 100:
                print("too many replacements")
                break
            repCount += 1

            match = self.tag_re.search(template)
            if match is None:
                break

            tag, tag_type, tag_name = match.group(0, 1, 2)
            tag_name = tag_name.strip()
            try:
                func = modifiers[tag_type]
                replacement = func(self, tag_name, context)
                template = template.replace(tag, replacement)
            except (SyntaxError, KeyError):
                return "{{invalid template}}"

        return template

    # {{{ functions just like {{ in anki
    @modifier("{")
    def render_tag(self, tag_name, context) -> Any:
        return self.render_unescaped(tag_name, context)

    @modifier("!")
    def render_comment(self, tag_name=None, context=None) -> str:
        """Rendering a comment always returns nothing."""
        return ""

    @modifier(None)
    def render_unescaped(self, tag_name=None, context=None) -> Any:
        """Render a tag without escaping it."""
        txt = get_or_attr(context, tag_name)
        if txt is not None:
            # some field names could have colons in them
            # avoid interpreting these as field modifiers
            # better would probably be to put some restrictions on field names
            return txt

        # field modifiers
        parts = tag_name.split(":")
        extra = None
        if len(parts) == 1 or parts[0] == "":
            return "{unknown field %s}" % tag_name
        else:
            mods, tag = parts[:-1], parts[-1]  # py3k has *mods, tag = parts

        txt = get_or_attr(context, tag)

        # Since 'text:' and other mods can affect html on which Anki relies to
        # process clozes, we need to make sure clozes are always
        # treated after all the other mods, regardless of how they're specified
        # in the template, so that {{cloze:text: == {{text:cloze:
        # For type:, we return directly since no other mod than cloze (or other
        # pre-defined mods) can be present and those are treated separately
        mods.reverse()
        mods.sort(key=lambda s: not s == "type")

        for mod in mods:
            # built-in modifiers
            if mod == "text":
                # strip html
                txt = stripHTML(txt) if txt else ""
            elif mod == "type":
                # type answer field; convert it to [[type:...]] for the gui code
                # to process
                return "[[%s]]" % tag_name
            elif mod.startswith("cq-") or mod.startswith("ca-"):
                # cloze deletion
                mod, extra = mod.split("-")
                txt = self.clozeText(txt, extra, mod[1]) if txt and extra else ""
            else:
                # hook-based field modifier
                m = re.search(r"^(.*?)(?:\((.*)\))?$", mod)
                if not m:
                    return "invalid field modifier " + mod
                mod, extra = m.groups()
                txt = runFilter(
                    "fmod_" + mod, txt or "", extra or "", context, tag, tag_name
                )
                if txt is None:
                    return "{unknown field %s}" % tag_name
        return txt

    @classmethod
    def clozeText(cls, txt: str, ord: str, type: str) -> str:
        """Processe the given Cloze deletion within the given template."""
        reg = clozeReg
        currentRegex = clozeReg % ord
        if not re.search(currentRegex, txt):
            # No Cloze deletion was found in txt.
            return ""
        txt = cls._removeFormattingFromMathjax(txt, ord)

        def repl(m):
            # replace chosen cloze with type
            if type == "q":
                if m.group(CLOZE_REGEX_MATCH_GROUP_HINT):
                    buf = "[%s]" % m.group(CLOZE_REGEX_MATCH_GROUP_HINT)
                else:
                    buf = "[...]"
            else:
                buf = m.group(CLOZE_REGEX_MATCH_GROUP_CONTENT)
            # uppercase = no formatting
            if m.group(CLOZE_REGEX_MATCH_GROUP_TAG) == "c":
                buf = "<span class=cloze>%s</span>" % buf
            return buf

        txt = re.sub(currentRegex, repl, txt)
        # and display other clozes normally
        return re.sub(reg % r"\d+", "\\2", txt)

    @classmethod
    def _removeFormattingFromMathjax(cls, txt, ord) -> str:
        """Marks all clozes within MathJax to prevent formatting them.

        Active Cloze deletions within MathJax should not be wrapped inside
        a Cloze <span>, as that would interfere with MathJax.

        This method finds all Cloze deletions number `ord` in `txt` which are
        inside MathJax inline or display formulas, and replaces their opening
        '{{c123' with a '{{C123'. The clozeText method interprets the upper-case
        C as "don't wrap this Cloze in a <span>".
        """
        creg = clozeReg.replace("(?si)", "")

        # Scan the string left to right.
        # After a MathJax opening - \( or \[ - flip in_mathjax to True.
        # After a MathJax closing - \) or \] - flip in_mathjax to False.
        # When a Cloze pattern number `ord` is found and we are in MathJax,
        # replace its '{{c' with '{{C'.
        #
        # TODO: Report mismatching opens/closes - e.g. '\(\]'
        # TODO: Report errors in this method better than printing to stdout.
        # flags in middle of expression deprecated
        in_mathjax = False

        def replace(match):
            nonlocal in_mathjax
            if match.group("mathjax_open"):
                if in_mathjax:
                    print("MathJax opening found while already in MathJax")
                in_mathjax = True
            elif match.group("mathjax_close"):
                if not in_mathjax:
                    print("MathJax close found while not in MathJax")
                in_mathjax = False
            elif match.group("cloze"):
                if in_mathjax:
                    return match.group(0).replace(
                        "{{c{}::".format(ord), "{{C{}::".format(ord)
                    )
            else:
                print("Unexpected: no expected capture group is present")
            return match.group(0)

        # The following regex matches one of:
        #  -  MathJax opening
        #  -  MathJax close
        #  -  Cloze deletion number `ord`
        return re.sub(
            r"(?si)"
            r"(?P<mathjax_open>\\[([])|"
            r"(?P<mathjax_close>\\[\])])|"
            r"(?P<cloze>" + (creg % ord) + ")",
            replace,
            txt,
        )

    @modifier("=")
    def render_delimiter(self, tag_name=None, context=None) -> str:
        """Changes the Mustache delimiter."""
        try:
            self.otag, self.ctag = tag_name.split(" ")
        except ValueError:
            # invalid
            return ""
        self.compile_regexps()
        return ""
