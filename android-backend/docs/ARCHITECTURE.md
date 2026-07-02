# Architecture

Anki-Android-Backend uses Rust, Java and a little Python. Since setting up a Rust environment is somewhat complex, having a separate library encourages drive-by contributions to the main app by keeping a low barrier to entry for Anki-Android.

This repo is comprised of two main components:

- `rslib-bridge` is a small Rust project that gets compiled into a shared library, which Java code can import. Its API consists
  of only three different functions: `openBackend()`, `closeBackend()`, and `runMethodRaw()`. When one of these functions is called by
  Java code, `rslib-bridge` takes care of converting the Java objects to a native Rust representation, and then passes the call
  on to Anki's Rust backend, which gets included in `rslib-bridge` via the `anki[/rslib]` submodule.
- `rsdroid` is a Kotlin library that provides a friendly interface to the backend code. The bulk of its code is automatically
  generated from the service definitions in `anki/proto/anki`. `rsdroid` also provides an adaptor to the Rust
  database functionality, so that the Rust backend can be used in place of the standard Android SQLite library.

Other folders:

`/anki/` - Git submodule containing the Anki Rust Codebase, used both for building into `.so` files, and to obtain the current `.proto` files for use in Java codegen

`/tools/` Scripts to generate the backend interface and translations

`rsdroid-testing` - Builds a testing library targetting desktop machines, for use with Robolectric

`rsdroid-instrumented` - Android Instrumented Test

This is defined as an application to allow instrumented tests to be run against a library - there may be a better method

## Protocol Buffers

The Rust backend uses Protocol Buffers to define available methods,
and the structure of data each method takes and receives. The files can
be found in `anki/proto/anki`.

For example:

```proto
service StatsService {
  rpc GetGraphPreferences(generic.Empty) returns (GraphPreferences);
}
...
message GraphPreferences {
  enum Weekday {
    SUNDAY = 0;
    MONDAY = 1;
    FRIDAY = 5;
    SATURDAY = 6;
  }
  Weekday calendar_first_day_of_week = 1;
  bool card_counts_separate_inactive = 2;
  bool browser_links_supported = 3;
  bool future_due_show_backlog = 4;
}
```

Every method takes a _request_ structure, and returns a _response_ structure.
In the case of GetGraphPreferences, which doesn't need any input arguments,
we use the _Empty_ structure which is a message that contains no fields.

When Java code wants to invoke a method on the backend, it first creates the
input request, and then encodes it into a ByteArray. It passes the method
id and the input bytes into the backend's `runMethodRaw()`, and the backend
returns another ByteArray which can be decoded into the output message.

Doing this manually for each method would be a pain, so we use code generation
instead. `rslib-bridge/proto.rs` reads the Protobuf files (actually, a binary
representation of them exported by the backend), and
automatically generates methods for us into a GeneratedBackend.kt file. Eg:

```kotlin
    @Throws(BackendException::class)
    fun getGraphPreferencesRaw(input: ByteArray): ByteArray {
        return runMethodRaw(service=10, method=2, input);
    }

    @Throws(BackendException::class)
    open fun getGraphPreferences(): anki.stats.GraphPreferences {
        val builder = anki.generic.Empty.newBuilder();
        val input = builder.build();
        return anki.stats.GraphPreferences.parseFrom(
                getGraphPreferencesRaw(input.toByteArray()));
    }
```

## Backend

The main class that AnkiDroid uses is called `Backend`. It includes:

- all the generated method definitions from `GeneratedBackend.kt`
- a `tr` property that exposes all the backend translations
- helper methods for DB access

When `Backend` is instantiated, it uses `NativeMethods.kt:openBackend()` to
create a handle to a backend instance. `Backend.close()` takes care of
also closing the backend instance, closing the collection if open, and
freeing up memory.

Each backend instance supports a single open collection, so multiple
backend instances need to be created if you need to have multiple collections
open in parallel. Switching between collections does not require multiple
instances - you can close one collection and then open another with the
same backend instance.

`Backend` also wraps the majority of method calls in a mutex, which prevents
any backend call from being made while a transaction is active on another
thread.

## BackendFactory

`BackendFactory.getBackend()` is used by AnkiDroid to get a `Backend` instance
with translations set to the language currently configured by the user.

## Error handling

The API `rslib-bridge` exposes is defined in `rsdroid`'s NativeMethods.kt:

```kotlin
    external fun runMethodRaw(backendPointer: Long, service: Int, method: Int, args: ByteArray): Array<ByteArray?>?
    external fun openBackend(data: ByteArray): Array<ByteArray?>?
    external fun closeBackend(backendPointer: Long)
```

When the backend returns data, it needs to be able to return either the data,
or an error message. This is done with nested arrays: `Array<ByteArray?>?` is
`[valid_data_or_null, error_data_or_null]`. The `Backend` class takes care of
this for us, extracting the error message, decoding it into a protobuf message and
then a native BackendException, and throwing it. The outer array is declared
as nullable to account for rare cases where an array can't be allocated.

## Usage in AnkiDroid

When a collection is opened with `Storage.collection()`, a `Backend` instance
is created (or reused if provided), and stored in `Collection.backend`. As
`Collection` is initialized, it calls `Storage.openDB(path, backend)` which
creates a `DB` instance that delegates database calls to the provided backend.

It contains changes to work with the new backend methods,
such as requesting a list of decks from the backend instead of directly trying
to query them via SQL, eg:

```kotlin
    override fun all_names_and_ids(skip_empty_default: Boolean, include_filtered: Boolean): List<DeckNameId> {
        return backend.getDeckNames(skip_empty_default, include_filtered).map {
                entry ->
            DeckNameId(entry.name, entry.id)
        }
    }
```

## Usage in unit tests

AnkiDroid's unit tests run with Robolectric, and to use the backend inside Robolectric, a separate build of rslib-bridge is required. This is handled
by `rsdroid-testing`, which takes care of compiling `rslib-bridge` correctly,
and provides a `RustBackendLoader.kt` file which AnkiDroid's unit tests call
into.

## Database Access

We need to use the Rust for database access as:

- We need an open collection to perform most commands in rslib
- An open collection obtains a lock on the database - access can only be made through the Rust.

So, we implement `SupportSQLiteOpenHelper.Factory` and related classes.

### Memory Pressure

Anki's rust code does not stream database results, all results are currently obtained without streaming and are temporarily stored in memory in the Rust.

This is not a significant problem, as:

- Rust is not confined by the Java heap limit
- Most results are small
- In time, we will move most data processing to the Rust, removing the need to deserialize data
- Java has been converted to use protobuf serialization (vs Anki Desktop using JSON), this significantly reduces memory usage.

#### LimitOffsetSQLiteCursor

If the above is not sufficient, work could be performed to make `LimitOffsetSQLiteCursor` work.

#### Streaming over JNI

Over the JNI boundary, streaming takes place via `StreamingProtobufSQLiteCursor`

**Caveat**: Nested streamed queries cannot currently take place using this method due to the Rust implementation via a HashMap (ensuring that memory will not be leaked by non-disposed result sets). An exception will occur if nested queries are detected, and this constraint will be revisited.

## Panics

Rust panics (excluding OutOfMemory) are serialized to an `BackendError` proto, and sent to the Java.

`BackendException.fromError` will convert this to a `BackendFatalError`. ACRA cannot pick up on a native crash, by converting it into an Error, we can get a stack trace.

## Publishing

Artifacts are published to Sonatype OSS via CI. This is a Maven repository. `VERSION_NAME` inside `gradle.properties` defines the version number
