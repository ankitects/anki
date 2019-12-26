# Fuzz tests

Run fuzz tests using:

```bash
tools/fuzz.sh
```

The fuzz tests will run in parallel.

Individual fuzz tests live in `fuzz/*/fuzz.py`. If a fuzz test finds an issue,
it will write a `crash` file to its directory and abort fuzzing.

TODO(agentydragon): Currently if a fuzzer crashes or you press Ctrl+C,
the remaining fuzzers will keep runnning in the background, despite our attempts
to kill them.
