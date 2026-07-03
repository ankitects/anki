# Anki

[![Build Status](https://github.com/ankitects/anki/actions/workflows/ci.yml/badge.svg)](https://github.com/ankitects/anki/actions/workflows/ci.yml)

Core Python library for [Anki](https://apps.ankiweb.net), the spaced repetition flashcard program.

## About

[Anki](https://apps.ankiweb.net) is a spaced repetition program that helps you remember things efficiently. This package contains the Python layer of Anki's core: it wraps the Rust backend (`rslib`) and exposes the primary API used by the desktop app and add-ons alike.

It provides access to:

- **Collection** — open, read, and write an Anki `.anki2` database
- **Notes & Cards** — create, update, and query notes and cards
- **Decks & Models** — manage deck configurations and note types
- **Scheduler** — the FSRS/SM-2 scheduling algorithms
- **Media** — media file management and sync
- **Import / Export** — support for `.apkg`, `.colpkg`, and other formats
- **Sync** — synchronisation with AnkiWeb
- **Hooks** — event system for extending behaviour

## Installation

```bash
pip install anki
```

> **Note:** `anki` is the headless library. If you want the full desktop application, install [`aqt`](https://pypi.org/project/aqt/) instead, which depends on this package.

## Add-on development

If you are building an Anki add-on, this is the package that gives you access to the collection and scheduling internals. See the [Add-on Guide](https://addon-docs.ankiweb.net/) for full documentation.

## Contributing

Want to contribute? Check out the [Contribution Guidelines](https://github.com/ankitects/anki/blob/main/docs/contributing.md) and the [Development Guide](https://github.com/ankitects/anki/blob/main/docs/development.md).

## License

[AGPL-3.0-or-later](https://github.com/ankitects/anki/blob/main/LICENSE)
