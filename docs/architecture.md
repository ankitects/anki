# Anki Architecture

Very brief notes for now.

## Backend/GUI

At the highest level, Anki is logically separated into two parts.

A neat visualization of the file layout is available here:
<https://mango-dune-07a8b7110.1.azurestaticapps.net/?repo=ankitects%2Fanki>
(or go to <https://githubnext.com/projects/repo-visualization#explore-for-yourself> and enter `ankitects/anki`).

### Library (`rslib/` and `pylib/`)

The code responsible for the actual logic (opening collections, fetching and answering cards, et cetera) — the "backend" methods — is written parts in Python, parts in Rust.
Most of the logic previously implemented in Python was rewritten in Rust so that the code can be shared between the different Anki apps.

- The parts of the library code written in Python are located in `pylib/` (more specifically, in `pylib/anki/`).
- The parts of the library code written in Rust are located in `rslib/`.

The Python code serves as proxy for the Rust library and, when called, forwards the requests to the code in `rslib/` which does the actual computations and then returns the results to the code in `pylib/anki/`.
The way that is implemented is by way of a Python binding named `rsbridge` within `pylib/` (that is, `pylib/rsbridge/`) that wraps around the Rust code and makes the logic implemented in Rust accessible with Python code.

The Python library is made available as a Python package named `anki` and is accessible through `import anki` in Python code.

Anki’s GUI relies on this Python library (located in `pylib/anki/`) and you can use it too (by installing it with `pip install anki`) if you want to develop command line programs to programmatically access your Anki decks without the need of running Anki’s GUI.

### GUI (`qt/` and `ts/`)

Anki’s GUI is written parts in Python using the toolkit Qt (via Qt's Python bindings known as PyQt), and parts in a mix of TypeScript, HTML, and CSS.

The Python code that implements Anki’s GUI (using PyQt) is located in `qt/aqt/`. It’s made available as a Python package named `aqt` (installable with `pip install 'aqt[qt6]'`) and is accessible through `import aqt` in Python code.

The web code is split between `ts/` and `qt/aqt/data/web/`.
The majority of new code now goes into `ts/` rather than `qt/aqt/data/web/` and, during the build process, gets copied into `qt/aqt/data/web/` instead.

## Protobuf

Anki uses Protocol Buffers to define backend methods, and the storage format of
some items in a collection file. The definitions live in `proto/anki/`.

The Python/Rust bridge uses them to pass data back and forth, and some of the
TypeScript code also makes use of them, allowing data to be communicated in a
type-safe manner between the different languages.

At the moment, the protobuf is not considered public API. Some pylib methods
expose a protobuf object directly to callers, but when they do so, they use a
type alias, so callers outside pylib should never need to import a generated
\_pb2.py file.
