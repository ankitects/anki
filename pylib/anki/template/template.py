import re
from typing import Any, Callable, Dict, Pattern

from anki.template2 import apply_field_filters, field_is_not_empty

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

            val = get_or_attr(context, section_name, None)

            replacer = ""
            inverted = section[2] == "^"
            nonempty = field_is_not_empty(val or "")
            if (nonempty and not inverted) or (not nonempty and inverted):
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
        # split out field modifiers
        *mods, tag = tag_name.split(":")

        # return an error if field doesn't exist
        txt = get_or_attr(context, tag)
        if txt is None:
            return "{unknown field %s}" % tag_name

        # the filter closest to the field name is applied first
        mods.reverse()
        return apply_field_filters(tag, txt, context, mods)

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
