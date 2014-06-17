import re
from anki.utils import stripHTML, stripHTMLMedia
from anki.hooks import runFilter
from anki.template import furigana; furigana.install()
from anki.template import hint; hint.install()

clozeReg = r"(?s)\{\{c%s::(.*?)(::(.*?))?\}\}"

modifiers = {}
def modifier(symbol):
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


def get_or_attr(obj, name, default=None):
    try:
        return obj[name]
    except KeyError:
        return default
    except:
        try:
            return getattr(obj, name)
        except AttributeError:
            return default


class Template(object):
    # The regular expression used to find a #section
    section_re = None

    # The regular expression used to find a tag.
    tag_re = None

    # Opening tag delimiter
    otag = '{{'

    # Closing tag delimiter
    ctag = '}}'

    def __init__(self, template, context=None):
        self.template = template
        self.context = context or {}
        self.compile_regexps()

    def render(self, template=None, context=None, encoding=None):
        """Turns a Mustache template into something wonderful."""
        template = template or self.template
        context = context or self.context

        template = self.render_sections(template, context)
        result = self.render_tags(template, context)
        if encoding is not None:
            result = result.encode(encoding)
        return result

    def compile_regexps(self):
        """Compiles our section and tag regular expressions."""
        tags = { 'otag': re.escape(self.otag), 'ctag': re.escape(self.ctag) }

        section = r"%(otag)s[\#|^]([^\}]*)%(ctag)s(.+?)%(otag)s/\1%(ctag)s"
        self.section_re = re.compile(section % tags, re.M|re.S)

        tag = r"%(otag)s(#|=|&|!|>|\{)?(.+?)\1?%(ctag)s+"
        self.tag_re = re.compile(tag % tags)

    def render_sections(self, template, context):
        """Expands sections."""
        while 1:
            match = self.section_re.search(template)
            if match is None:
                break

            section, section_name, inner = match.group(0, 1, 2)
            section_name = section_name.strip()

            # check for cloze
            m = re.match("c[qa]:(\d+):(.+)", section_name)
            if m:
                # get full field text
                txt = get_or_attr(context, m.group(2), None)
                m = re.search(clozeReg%m.group(1), txt)
                if m:
                    it = m.group(1)
                else:
                    it = None
            else:
                it = get_or_attr(context, section_name, None)

            replacer = ''
            # if it and isinstance(it, collections.Callable):
            #     replacer = it(inner)
            if isinstance(it, basestring):
                it = stripHTMLMedia(it).strip()
            if it and not hasattr(it, '__iter__'):
                if section[2] != '^':
                    replacer = inner
            elif it and hasattr(it, 'keys') and hasattr(it, '__getitem__'):
                if section[2] != '^':
                    replacer = self.render(inner, it)
            elif it:
                insides = []
                for item in it:
                    insides.append(self.render(inner, item))
                replacer = ''.join(insides)
            elif not it and section[2] == '^':
                replacer = inner

            template = template.replace(section, replacer)

        return template

    def render_tags(self, template, context):
        """Renders all the tags in a template for a context."""
        while 1:
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
                return u"{{invalid template}}"

        return template

    # {{{ functions just like {{ in anki
    @modifier('{')
    def render_tag(self, tag_name, context):
        return self.render_unescaped(tag_name, context)

    @modifier('!')
    def render_comment(self, tag_name=None, context=None):
        """Rendering a comment always returns nothing."""
        return ''

    @modifier(None)
    def render_unescaped(self, tag_name=None, context=None):
        """Render a tag without escaping it."""
        txt = get_or_attr(context, tag_name)
        if txt is not None:
            # some field names could have colons in them
            # avoid interpreting these as field modifiers
            # better would probably be to put some restrictions on field names
            return txt

        # field modifiers
        parts = tag_name.split(':')
        extra = None
        if len(parts) == 1 or parts[0] == '':
            return '{unknown field %s}' % tag_name
        else:
            mods, tag = parts[:-1], parts[-1] #py3k has *mods, tag = parts

        txt = get_or_attr(context, tag)
        
        #Since 'text:' and other mods can affect html on which Anki relies to
        #process clozes, we need to make sure clozes are always
        #treated after all the other mods, regardless of how they're specified
        #in the template, so that {{cloze:text: == {{text:cloze:
        #For type:, we return directly since no other mod than cloze (or other
        #pre-defined mods) can be present and those are treated separately
        mods.reverse()
        mods.sort(key=lambda s: not s=="type")

        for mod in mods:
            # built-in modifiers
            if mod == 'text':
                # strip html
                txt = stripHTML(txt) if txt else ""
            elif mod == 'type':
                # type answer field; convert it to [[type:...]] for the gui code
                # to process
                return "[[%s]]" % tag_name
            elif mod.startswith('cq-') or mod.startswith('ca-'):
                # cloze deletion
                mod, extra = mod.split("-")
                txt = self.clozeText(txt, extra, mod[1]) if txt and extra else ""
            else:
                # hook-based field modifier
                mod, extra = re.search("^(.*?)(?:\((.*)\))?$", mod).groups()
                txt = runFilter('fmod_' + mod, txt or '', extra or '', context,
                                tag, tag_name);
                if txt is None:
                    return '{unknown field %s}' % tag_name
        return txt

    def clozeText(self, txt, ord, type):
        reg = clozeReg
        if not re.search(reg%ord, txt):
            return ""
        def repl(m):
            # replace chosen cloze with type
            if type == "q":
                if m.group(3):
                    return "<span class=cloze>[%s]</span>" % m.group(3)
                else:
                    return "<span class=cloze>[...]</span>"
            else:
                return "<span class=cloze>%s</span>" % m.group(1)
        txt = re.sub(reg%ord, repl, txt)
        # and display other clozes normally
        return re.sub(reg%"\d+", "\\1", txt)

    @modifier('=')
    def render_delimiter(self, tag_name=None, context=None):
        """Changes the Mustache delimiter."""
        try:
            self.otag, self.ctag = tag_name.split(' ')
        except ValueError:
            # invalid
            return
        self.compile_regexps()
        return ''
