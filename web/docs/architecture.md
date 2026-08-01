# Anki Architecture

Very brief notes for now.

## Backend/GUI

At the highest level, Anki is logically separated into two parts.

A neat visualization of the file layout is available here:
<https://mango-dune-07a8b7110.1.azurestaticapps.net/?repo=ankitects%2Fanki>
(or go to <https://githubnext.com/projects/repo-visualization#explore-for-yourself> and enter `ankitects/anki`).

### Library (rslib & pylib)

The Python library (pylib) exports "backend" methods - opening collections,
fetching and answering cards, and so on. It is used by Anki’s GUI, and can also
be included in command line programs to access Anki decks without the GUI.

The library is accessible in Python with "import anki". Its code lives in
the `pylib/anki/` folder.

These days, the majority of backend logic lives in a Rust library (rslib, located in `rslib/`). Calls to pylib proxy requests to rslib, and return the results.

pylib contains a private Python module called rsbridge (`pylib/rsbridge/`) that wraps the Rust code, making it accessible in Python.

### GUI (aqt & ts)

Anki's _GUI_ is a mix of Qt (via the PyQt Python bindings for Qt), and
TypeScript/HTML/CSS. The Qt code lives in `qt/aqt/`, and is importable in Python
with "import aqt". The web code is split between `qt/aqt/data/web/` and `ts/`,
with the majority of new code being placed in the latter, and copied into the
former at build time.

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
