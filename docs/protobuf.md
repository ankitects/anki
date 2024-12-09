# Protocol Buffers

ProtoBuf is a format used both to save data in storage and transmit
data between services. You can think of it as similar to JSON with
schemas, given that you can use basic types, list and records. Except
that it's usually represented in an efficient byteform and not in a
human readable way.

In this document we'll first explain how to encode and decode protobuf
messages in the three languages used in anki. Typescript, Python and
Rust. Please note that AnkiDroid and AnkiMobile are out of scope for
this document. In the second part, we'll explain how the code in those
three languages can communicate, usually using protobuf rpc.

# Reading and creating protobuf messages.

Anki uses [different implementations of Protocol Buffers](./architecture.md#protobuf)
and each has its own peculiarities. This document highlights some aspects relevant
to Anki and hopefully helps to avoid some common pitfalls.

For information about Protobuf's types and syntax, please see the official [language guide](https://developers.google.com/protocol-buffers/docs/proto3).

## General Notes

### Names

Generated code follows the naming conventions of the targeted language. So to access
the message field `foo_bar` you need to use `fooBar` in Typescript and the
namespace created by the message `FooBar` is called `foo_bar` in Rust.

### Optional Values

In Python and Typescript, unset optional values will contain the type's default
value rather than `None`, `null` or `undefined`. Here's an example:

```protobuf
message Foo {
  optional string name = 1;
  optional int32 number = 2;
}
```

```python
message = Foo()
assert message.number == 0
assert message name == ""
```

In Python, we can use the message's `HasField()` method to check whether a field is
actually set:

```python
message = Foo(name="")
assert message.HasField("name")
assert not message.HasField("number")
```

In Typescript, this is even less ergonomic and it can be easier to avoid using
the default values in active fields. E.g. the `CsvMetadata` message uses 1-based
indices instead of optional 0-based ones to avoid ambiguity when an index is `0`.

### Oneofs

All fields in a oneof are implicitly optional, so the caveats [above](#optional-values)
apply just as much to a message like this:

```protobuf
message Foo {
    oneof bar {
      string name = 1;
      int32 number = 2;
    }
}
```

In addition to `HasField()`, `WhichOneof()` can be used to get the name of the set
field:

```python
message = Foo(name="")
assert message.WhichOneof("bar") == "name"
```

### Backwards Compatibility

The official [language guide](https://developers.google.com/protocol-buffers/docs/proto3)
makes a lot of notes about backwards compatibility, but as Anki usually doesn't
use Protobuf to communicate between different clients, things like shuffling around
field numbers are usually not a concern.

However, there are some messages, like `Deck`, which get stored in the database.
If these are modified in an incompatible way, this can lead to serious issues if
clients with a different protocol try to read them. Such modifications are only
safe to make as part of a schema upgrade, because schema 11 (the targeted schema
when choosing _Downgrade_), does not make use of Protobuf messages.

### Field Numbers

Field numbers larger than 15 need an additional byte to encode, so `repeated` fields
should preferably be assigned a number between 1 and 15. If a message contains
`reserved` fields, this is usually to accommodate potential future `repeated` fields.

## Implementation-Specific Notes

### Python

Protobuf has an official Python implementation with an extensive [reference](https://developers.google.com/protocol-buffers/docs/reference/python-generated).

- Every message used in aqt or pylib must be added to the respective `.pylintrc`
  to avoid failing type checks. The unqualified protobuf message's name must be
  used, not an alias from `collection.py` for example. This should be taken into
  account when choosing a message name in order to prevent skipping typechecking
  a Python class of the same name.

### Typescript

Anki uses [protobuf-es](https://github.com/bufbuild/protobuf-es), which offers
some documentation.

### Rust

Anki uses the [prost crate](https://docs.rs/prost/latest/prost/).
Its documentation has some useful hints, but for working with the generated code,
there is a better option: From within `anki/rslib` run `cargo doc --open --document-private-items`.
Inside the `pb` module you will find all generated Rust types and their implementations.

- Given an enum field `Foo foo = 1;`, `message.foo` is an `i32`. Use the accessor
  `message.foo()` instead to avoid having to manually convert to a `Foo`.
- Protobuf does not guarantee any oneof field to be set or an enum field to contain
  a valid variant, so the Rust code needs to deal with a lot of `Option`s. As we
  don't expect other parts of Anki to send invalid messages, using an `InvalidInput`
  error or `unwrap_or_default()` is usually fine.

# Communication between languages

Currently, Rust does not send message to Python or Ts. That is, the
back-end do not send message to the front-end.  Instead, the front end
can request data, that Rust returns as part of a RPC initiated by the
front end. Those RPC are discussed below.

## Sending a message to typescript.

This is the only case that don't use protobuf. Any Python code, having
access to a webview `wv` can send an arbitrary
`wv.web.eval(js_command)` or
`wv.web.evalWithCallback(js _command, cb)` to execute an arbitrary javascript command.

If your command need to be executed inside a svelte script, you can add it to `+page.svelte`, with

```js
globalThis.anki || = {};
globalThis.anki.methodName = function() {// or async
  body
}
```

You then simply have to execute `anki.methodName()`.

Note that if the function is asynchronous, you can't directly send the
result to a callback. Instead your function will have to call a post
method that will be sent to Python or Rust. Which leads us to the next
section.

## From typescript to Python

In this section, I assume you want to send a rpc to Python only, and not Rust.

In this case:

- Add your rpc declaration `rpc NameOfMyRpc(InputType) returns (OutputType)` to [frontend.proto](/proto/anki/frontend.proto)'s FrontendService,
- In typescript, add `import {NameOfMyRpc} from "@generated/backend"` to use this RPC, you can then use `NameOfMyRpc(args)`,
- In [mediasrv.py](/qt/aqt/mediasrv.py) you create a method `def name_of_my_rpc(args) -> bytes`, and add `name_of_my_rpc` to `post_handler_list`.

As you don't want to block the thread, unless the result can be really
quickly computed, you'll want to return `b""`, and use
`aqt.mw.taskman.run_on_main(function_doing_actual_work)`.

## From Typescript or Python to Rust

For the sake of the example, I'll consider that you want to add a RPC in the DecksService. Any service, expect `FrontendService`, works similarly.

- In [decks.proto](/proto/anki/decks.proto)'s DecksService, add your new rpc `rpc NameOfMyRpc(InputType) returns (OutputType)`.
- In [services.rs](/rslib/src/decks/service.rs)'s `impl crate::services::DecksService for Collection` add the implementation `fn name_of_my_rpc(&mut self, args) -> Error::Result<OutputType>`
- In typescript add `import {NameOfMyRpc} from "@generated/backend"` to use this RPC, you can then use `NameOfMyRpc(args)`,
- In Python add can call `col._backend.name_of_my_rpc(args)`, with `col` being your collection object.
