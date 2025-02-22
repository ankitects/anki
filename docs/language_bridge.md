Anki's codebase uses three layers.

1. The web frontend, created in Svelte and typescript,
2. The Python layer and
3. The core Rust layer.

Each layer can can makes RPC (Remote Procedure Call) to the layers below it. While it should be avoided, Python can also invoke Typescript functions. The Rust layers never make calls to the other layers. Note that it can make RPC to AnkiWeb and other servers, which is out of scope of this document.

In this document we'll provide examples of bridge between languages, explaining:

- where the RPC is declared,
- where it is called (with the appropriate imports) and
- where it is implemented.

Imitating those examples should allow you to make call and create new RPCs.

## Declaring RPCs

Let's consider the method `NewDeck` of `DecksServices`. It's declared in [decks.proto](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/proto/anki/decks.proto#L14) as `rpc NewDeck(generic.Empty) returns (Deck);`. This means this methods takes no argument (technically, an argument containing no information), and returns a [`Deck`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/proto/anki/decks.proto#L54).

Read [protobuf](./protobuf.md) to learn more about how those input and output types are defined.

If the RPC implementation is in Python, it should be declared in [frontend.proto](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/proto/anki/frontend.proto#L24C3-L24C66)'s `FrontendService`. Otherwise, it can be declared in any other service. Obviously, the service should be consistent with what the method do.

## Making RPC

In this section we'll consider how to make RPC from languages used in Anki. Languages used for AnkiDroid and AnkiMobile are out of scope of this document.

### Making a RPC from Python

Python can invoke the `NewDeck` method with [`col._backend.new_deck()`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/pylib/anki/decks.py#L168). This python method takes no argument and returns a `Deck` value.

However, most of Python code should not call this method directly. Instead it should call [`col.decks.new_deck()`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/pylib/anki/decks.py#L166). Generally speaking, all back-ends functions called from Python should be called through an helper method defined in pylib/anki/. The _backend part is an implementation details that most callers should ignore. This is especially important because add-ons should expect a relatively stable API independently on the implementation details of the RPC.

### Invoking method from TypeScript

Let's consider the method [`rpc GetCsvMetadata(CsvMetadataRequest) returns (CsvMetadata);`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/proto/anki/import_export.proto#L20) from `ImportExportService`..

It's used in the TypeScript class [`ImportCsvState`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/ts/routes/import-csv/lib.ts#L102), as an asynchronous function. It's argument is a single javascript object, whose keys are as in [`CsvMetadataRequest`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/proto/anki/import_export.proto#L138) and it returns a `CsvMetadata`.

The method was imported with `import { getCsvMetadata } from "@generated/backend";` and the types were imported with `import type { CsvMetadata } from "@generated/anki/import_export_pb";`. Note that it was not necessary to import the input type given that it's simply an untyped javascript object.

## Implementation

Let's now look at implementations of those RPCs.

### Implementation in Rust

The method NewDeck is implemented in Rust's [DecksService](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/rslib/src/decks/service.rs#L21) as `fn new_deck(&mut self) -> error::Result<anki_proto::decks::Deck>`. It should be noted that the method name was changed from Pascal case to snake case, and the rps's argument of type `generic.Empty` is ignored.

### Implementation in Python

Let's consider the implementation of the method [DeckOptionsRequireClose](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/mediasrv.py#L578). It's defined as `def deck_options_require_close() -> bytes:`. In this case, there should be a returned value. However, it'll be ignored, so returning `b""` is perfectly fine.

It must be noted that:

- the RPC is not received on main. In order to do any work with window, we should call `aqt.mw.taskman.run_on_main`.
- at the time the RPC is received, we have no guarantee that the main view's active window is a DeckOptionsDialog. So this should be checked before doing anything else. If it's not the case, the call should be ignored.
- While MediaServer handles all request from typescript, it's not the appropriate place for most implementation. So it's expected that, once it has determined that the active view has the expected type, this active view actually handle the request.

## Invoking a TypeScript method from Python

This case should be avoided if possible, as we generally should avoid
calls to the upper layer. Contrary to the previous cases, we don't use
protobuf.

### Calling a TS function.

Let's take as Example [`export function getTypedAnswer(): string | null`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/ts/reviewer/index.ts#L35). It's an exported function, and its return type can be encoded in JSON.

It's called in the Reviewer class through [`self.web.evalWithCallback("getTypedAnswer();", self._onTypedAnswer)`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/reviewer.py#L785). The result is then sent to [`_onTypedAnswer`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/reviewer.py#L787).

If no return value is needed, `web.eval` would have been sufficient.

### Calling a Svelte method

Let's now consider the case where the method we want to call is implemented in a Svelte library. Let's take as example [`deckOptionsPendingChanges`](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/ts/routes/deck-options/%5BdeckId%5D/%2Bpage.svelte#L17). We define it with:

```js
globalThis.anki || = {};
globalThis.anki.methodName = async (): Promise<void>=>{body}
```

Note that if the function is asynchronous, you can't directly send the
result to a callback. Instead your function will have to call a post
method that will be sent to Python or Rust. Which leads us to the next
section.

This method is called in [deckoptions.py](https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/deckoptions.py#L68) with `self.web.eval("anki.deckOptionsPendingChanges();"`.
