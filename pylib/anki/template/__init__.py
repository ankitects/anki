from typing import Any

from . import furigana, hint
from .template import Template

furigana.install()

hint.install()


def render(template, context=None, **kwargs) -> Any:
    context = context and context.copy() or {}
    context.update(kwargs)
    return Template(template, context).render()
