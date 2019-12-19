from .template import Template
from . import furigana; furigana.install()
from . import hint; hint.install()
from typing import Any

def render(template, context=None, **kwargs) -> Any:
    context = context and context.copy() or {}
    context.update(kwargs)
    return Template(template, context).render()
