# aqt — Anki Qt GUI

`aqt` is the Qt-based desktop interface for [Anki](https://apps.ankiweb.net), the spaced repetition flashcard program.

It provides all the visual components of the Anki desktop app: the deck browser, card editor, reviewer, browser, add-on manager, and more. Under the hood it uses PyQt6 and communicates with Anki's core logic via the [`anki`](https://pypi.org/project/anki/) package.

## Running

Once installed, Anki can be launched from the command line:

```bash
anki
```

## Add-on development

If you are building an Anki add-on, this is the package that exposes the GUI hooks and Qt widgets you need. See the [Add-on Guide](https://addon-docs.ankiweb.net/) for full documentation.

## Source code

<https://github.com/ankitects/anki>
