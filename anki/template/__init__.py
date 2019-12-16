from .template import Template
from . import furigana; furigana.install()
from . import hint; hint.install()

def render(template, context=None, **kwargs):
    context = context and context.copy() or {}
    context.update(kwargs)
    return Template(template, context).render()
