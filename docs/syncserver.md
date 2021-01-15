# Local sync server

A local sync server is bundled with Anki. If you cannot or do not wish to
use AnkiWeb, you can run the server on a machine on your local network.

Things to be aware of:

- Media syncing is not currently supported. You will either need to disable
  syncing of sounds and images in the preferences screen, sync your media via
  AnkiWeb, or use some other solution.
- AnkiMobile does not yet provide an option for using a local sync server,
  so for now this will only be usable with the computer version of Anki, and
  AnkiDroid.
- This code is partly new, and while it has had some testing, it's possible
  something has been missed. Please make backups, and report any bugs you run
  into.
- The server runs over an unencrypted HTTP connection and does not require
  authentication, so it is only suitable for use on a private network.
- This is an advanced feature, targeted at users who are comfortable with
  networking and the command line. If you use this, the expectation is you
  can resolve any setup/network/firewall issues you run into yourself, and
  use of this is entirely at your own risk.

## From source

If you run Anki from git, you can run a sync server with:

```
./scripts/runopt --syncserver
```

## From a packaged build

From 2.1.39beta1+, the sync server is included in the packaged binaries.

On Windows in a cmd.exe session:

```
"\program files\anki\anki-console.exe" --syncserver
```

Or MacOS, in Terminal.app:

```
/Applications/Anki.app/Contents/MacOS/AnkiMac --syncserver
```

Or Linux:

```
anki --syncserver
```

## Without Qt dependencies

You can run the server without installing the GUI portion of Anki. Once Anki
2.1.39 is released, the following will work:

```
pip install anki[syncserver]
python -m anki.syncserver
```

## Server setup

The server needs to store a copy of your collection in a folder.
By default it is ~/.syncserver; you can change this by defining
a `FOLDER` environmental variable. This should not be the same location
as your normal Anki data folder.

You can also define `HOST` and `PORT`.

## Client setup

When the server starts, it will print the address it is listening on.
You need to set an environmental variable before starting your Anki
clients to tell them where to connect to. Eg:

```
set SYNC_ENDPOINT="http://10.0.0.5:8080/sync/"
anki
```

Currently any username and password will be accepted. If you wish to
keep using AnkiWeb for media, sync once with AnkiWeb first, then switch
to your local endpoint - collection syncs will be local, and media syncs
will continue to go to AnkiWeb.

## Contributing

Authentication shouldn't be too hard to add - login() and request() in
http_client.rs can be used as a reference. A PR that accepts a password in an
env var, and generates a stable hkey based on it would be welcome.

Once that is done, basic multi-profile support could be implemented by moving
the col object into an array or dict, and fetching the relevant collection based
on the user's authentication.

Because this server is bundled with Anki, simplicity is a design goal - it is
targeted at individual/family use, only makes use of Python libraries the GUI is
already using, and does not require a configuration file. PRs that deviate from
this are less likely to be merged, so please consider reaching out first if you
are thinking of starting work on a larger change.
