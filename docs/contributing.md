# Contributing Code

For info on contributing things other than code, such as translations, decks
and add-ons, please see https://docs.ankiweb.net/#/contrib

With most users now on 2.1, it's time to start paying down some of the
technical debt that Anki's codebase has built up over the years. This is
not an easy task - the code is tightly coupled together, not fully covered
by unit tests, and mostly dynamically typed, meaning even small changes
carry the risk of regressions.

At the moment, the focus is on changes that will make future maintenance and
refactoring easier - migrating parts of the codebase to Rust, improving tooling
and linting, type hints in the Python code, and more unit tests.

New features are not currently the top priority, unless they are easy wins as
part of the refactoring process.

If you are planning to contribute any non-trivial changes, please reach out
on the support site before you begin work. Some areas (primarily pylib/) are
likely to change/conflict with other work, and larger changes will likely need
to wait until the refactoring process nears completion.

## Help wanted

If you'd like to contribute but don't know what to work on, please take a look
at the issues on the following repo. It's quite bare at the moment, but will
hopefully grow with time.

https://github.com/ankitects/help-wanted

## Type hints

Type hints have recently been added to parts of the Python codebase, mainly
using automated tools. At the moment, large parts of the codebase are still
missing type hints, and some of the hints that do exist are incorrect or too
general.

When running 'bazel test', Anki uses mypy to typecheck the code. Mypy is able to
infer some types by itself, but adding more type signatures to the code
increases the amount of code that mypy is able to analyze.

Patches that improve the type hints would be appreciated. And if you're
adding new functionality, please use type hints in the new code you write
where practical.

Parts of Anki's codebase use ad-hoc data structures like nested dictionaries
and lists, and they can be difficult to fully type. Don't worry too much about
getting the types perfect - even a partial type like Dict[str, Any] or
List[Tuple] is an improvement over no types at all.

Qt's stubs are not perfect, so you'll find when doing things like connecting
signals, you may have to add the following to the end of a line to silence the
spurious errors.

```
 # type: ignore
```

In cases where you have two modules that reference each other, you can fix the
import cycle by using fully qualified names in the types, and enabling
annotations. For example, instead of

```
from aqt.browser import Browser

def myfunc(b: Browser) -> None:
  pass
```

use the following instead:

```
from __future__ import annotations

import aqt

def myfunc(b: aqt.browser.Browser) -> None:
  pass
```

## Hooks

If you're writing an add-on and would like to extend a function that doesn't
currently have a hook, a pull request that adds the required hooks would be
welcome. If you could mention your use case in the pull request, that would be
appreciated.

The hooks try to follow one of two formats:

[subject] [verb] - eg, note_type_added, card_will_render

[module] [verb] [subject] - eg, browser_did_change_row, editor_did_update_tags

The qt code tends to use the second form as the hooks tend to focus on
particular screens. The pylib code tends to use the first form, as the focus
is usually subjects like cards, notes, etc.

Using "did change" instead of the past test "changed" can seem awkward, but
makes it consistent with "will", and is similar to the naming style used in
iOS's libraries.

In most cases, hooks are better added in the GUI code than in pylib.

The hook code is automatically generated using the definitions in
pylib/tools/genhooks.py and qt/tools/genhooks_gui.py. Adding a new definition
in one of those files will update the generated files.

## Translations

For information on adding new translatable strings to Anki, please see
https://translating.ankiweb.net/#/anki/developers

## Tests Must Pass

Please make sure 'bazel test //...' completes successfully before submitting code.
You can do this automatically by adding the following into
.git/hooks/pre-commit or .git/hooks/pre-push and making it executable.

```sh
#!/bin/bash
bazel test //...
```

If your change is non-trivial and not covered by the existing unit tests, please
consider adding a unit test at the same time.

## Code Style

You are welcome to use snake_case variable names and functions in newly
introduced code, but please avoid renaming existing functions and global
variables that use camelCaps. Variables local to a function are safer to
rename, but please do so only when a function needs to be changed for other
reasons as well.

## Do One Thing

A patch or pull request should be the minimum necessary to address one issue.
Please don't make a pull request for a bunch of unrelated changes, as they are
difficult to review and will be rejected - split them up into separate
requests instead.

## License

Please add yourself to the CONTRIBUTORS file in your first pull request.
