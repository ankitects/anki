from typing import Any, Dict, Union

# Model attributes are stored in a dict keyed by strings. This type alias
# provides more descriptive function signatures than just 'Dict[str, Any]'
# for methods that operate on models.
# TODO: Use https://www.python.org/dev/peps/pep-0589/ when available in
# supported Python versions.

NoteType = Dict[str, Any]

Field = Dict[str, Any]

Template = Dict[str, Union[str, int, None]]
