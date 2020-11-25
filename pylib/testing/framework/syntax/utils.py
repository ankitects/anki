import re


def trim_indent(s: str):
    s = re.sub(r'^\n+', '', s)
    s = re.sub(r'\n+$', '', s)
    spaces = re.findall(r'^ +', s, flags=re.MULTILINE)
    if len(spaces) > 0 and len(re.findall(r'^[^\s]', s, flags=re.MULTILINE)) == 0:
        s = re.sub(r'^%s' % (min(spaces)), '', s, flags=re.MULTILINE)
    return s


def strip_html_tags(html):
    text = re.sub(r'<br>', '\n', html)
    text = text.replace('\\n', '\n')
    text = re.sub(r'<.*?>', '', text)
    text = text.replace('&lt;', '<').replace('&gt;', '>')
    return text.strip()


def to_snake_case(name):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def to_camel_case(name):
    result = ''.join([x[0].upper() + x[1:] for x in name.split('_')])
    return result[0].lower() + result[1:]
