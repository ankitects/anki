# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
String Utils
"""

import re
from jinja2.nativetypes import NativeEnvironment

env = NativeEnvironment()
IDENT_TAB_SIZE = 4


def render_template(template: str = '', retab: bool = False, **kwargs) -> str:
    """
    Renders string based on template and model parameters
    Beautifies result string, by removing extra spaces and new-lines

    :param template: source template
    :param retab: expand tabs with spaces
    :param kwargs: model
    :return: rendered template string
    """
    global env
    if template == '':
        return template
    t = env.from_string(template)
    s = t.render(kwargs)
    s = re.sub(r'^\n+', '', s)
    s = re.sub(r'\n+$', '', s)
    s = '\n'.join([re.sub(r'^ +', '', line) for line in s.split('\n')])
    s = re.sub(r' ,', ',', s)
    s = re.sub(r' +\)', ')', s)
    s = re.sub(r'\( +', '(', s)
    s = re.sub(r'\n{3,}', '\n\n', s)
    if retab:
        s = s.replace('\t', ' ' * IDENT_TAB_SIZE)
    return s


def strip_html_tags(html: str) -> str:
    """
    Remove html tags from html, replace <br> with \n, replce &lt; and &gt; by < and >
    :param html: target html
    :return: stripped html
    """
    text = re.sub(r'<br>', '\n', html)
    text = text.replace('\\n', '\n')
    text = re.sub(r'<.*?>', '', text)
    text = text.replace('&lt;', '<').replace('&gt;', '>')
    return text.strip()


def to_snake_case(name: str) -> str:
    """
    Converts camel-cased string to snake-cased, for example helloWorld -> hello_world
    :param name: target name to convert
    :return: snake-cased string
    """
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def to_camel_case(name: str) -> str:
    """
    Converts snake-cased string to camel-cased, for example hello_world -> helloWorld
    :param name: target name to convert
    :return: camel-cased string
    """
    result = ''.join([x[0].upper() + x[1:] for x in name.split('_')])
    return result[0].lower() + result[1:]


def get_line_number_prefix(line_number: str, line_number_offset: int):
    """
    Parses line_number to int, subtracts offset from it
    :param line_number: target line number
    :param line_number_offset: code offset
    :return: line number prefix or empty string if line number is outside of user scope or empty
    """
    try:
        n = int(line_number)
    except ValueError:
        return ''

    n -= line_number_offset
    if n <= 0:
        return ''

    return f'{n}:'
