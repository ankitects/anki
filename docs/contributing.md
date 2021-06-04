# Contributing Code

For info on contributing things other than code, such as translations, decks
and add-ons, please see https://docs.ankiweb.net/contrib

With most users now on 2.1, the past year has been focused on paying down some
of the technical debt that Anki's codebase has built up over the years, and making
changes that will make future maintenance and refactoring easier. A lot of Anki's
"business logic" has been migrated to Rust, which AnkiMobile and AnkiDroid
can also take advantage of - previously a lot of effort was wasted writing the same
code for each platform, and then debugging differences in the implementations.
Considerable effort has also been put into improving the Python side of things,
with type hints added to the majority of the codebase, automatic linting/formatting,
and refactoring of parts of the code.

The scheduling code and import/export code remains to be done, and this will likely
take a number of months to work through. Until that is complete, new features
will not be the top priority, unless they are easy wins as part of the refactoring
process.

If you are planning to contribute any non-trivial changes, please reach out
on the support site before you begin work. Some areas (primarily pylib/) are
likely to change/conflict with other work, and larger changes will likely need
to wait until the refactoring process is done.

## Help wanted

If you'd like to contribute but don't know what to work on, please take a look
at the [issues tab](https://github.com/ankitects/anki/issues) of the Anki repo
on GitHub.

## Type hints

Most of Anki's Python code now has type hints, which improve code completion,
and make it easier to discover errors during development. When adding new
code, please make sure you add type hints as well, or the tests will fail.

Qt's stubs are not perfect, so you may sometimes need to use cast(), or silence
a type error. When connecting signals, there's a qconnect() helper in aqt.utils
that can be used to work around the type warnings without obscuring other errors
such as a mistyped variable.

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

The qt code tends to use the second form, as the hooks tend to focus on
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
https://translating.ankiweb.net/anki/developers

## Tests Must Pass

Please make sure 'bazel test //...' completes successfully before submitting code.
You can do this automatically by adding the following into
.git/hooks/pre-commit or .git/hooks/pre-push and making it executable.

```sh
#!/bin/bash
bazel test //...
```

You may want to explicitly set PATH to your normal shell PATH in that script,
as pre-commit does not use a login shell, and if your path differs Bazel will
end up recompiling things unnecessarily.

If your change is non-trivial and not covered by the existing unit tests, please
consider adding a unit test at the same time.

## Code Style

Please use standard Python snake_case variable names and functions in newly
introduced code. Because add-ons often rely on existing function names, if
renaming an existing function, please add a legacy alias to the old function.

## Do One Thing

A patch or pull request should be the minimum necessary to address one issue.
Please don't make a pull request for a bunch of unrelated changes, as they are
difficult to review and will be rejected - split them up into separate
requests instead.

## License

Please add yourself to the CONTRIBUTORS file in your first pull request.
