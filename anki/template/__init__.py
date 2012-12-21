from anki.template.template import Template
from anki.template.view import View

def render(template, context=None, **kwargs):
    context = context and context.copy() or {}
    context.update(kwargs)
    return Template(template, context).render()
