# How to make `mypy` happy without having to build the entire project

You can use the type checker `mypy` to check if the Python code contains type errors:

```shell
mypy pylib/anki qt/aqt
```

That command will, however, produce lots of errors if you execute it without first having compiled the [`/proto/anki/*.proto` files](/proto/anki/) into Python code. To make `mypy` happy, you need to do an initial compilation of all `*.proto` files into `*_pb2.py` files that `mypy` can then use for type checking. You will also have to re-compile these `*.proto` files every single time someone (not necessarily you) changes one of these `*.proto` files if you don't want `mypy` to suddenly produce errors that weren't there the last time, even though you didn't make any changes.

The easiest (albeit overkill) way to compile these `*.proto` files would be to simply build the entire project. That would include compiling the `*.proto` files. That way, you could make use of the existing scripts and build system that handle all the necessary steps for you, but you'd also have to install the entire Rust toolchain. That would be complete overkill if you don't even know Rust, work only with Python, and have no plans of learning Rust. Perhaps you're also short on disk space because you have a Mac with a small SSD.

If you only want `mypy` to work without having to build the entire project or installing Rust, here's how to do it.

### Install Protobuf

First, make sure you have Protobuf installed.

If you use macOS, you can install it with Homebrew:

```shell
brew install protobuf
```

If you use Ubuntu, you can install it with APT:

```shell
sudo apt install -y protobuf-compiler
```

Now check if Protobuf is installed:

```shell
protoc --version
```

Protobuf alone isn't enough. To grant Protobuf the ability to compile `*.proto` files into Python code, you also need to have the Python package called `protoc-gen-mypy` installed. But first, make sure that you're in a virtual Python environment.

### Activate your virtual environment

Make sure that you're in a virtual Python environment:

```shell
python3 -m venv .venv     # create a virtual environment
source .venv/bin/activate # activate that virtual environment
```

Make sure you have all Python requirements installed in your virtual environment:

```shell
python3 -m pip install -r python/requirements.dev.txt
```

Pick the correct command for your operating system (macOS, Windows, Linux):

```shell
python3 -m pip install -r python/requirements.qt6_mac.txt
python3 -m pip install -r python/requirements.qt6_win.txt
python3 -m pip install -r python/requirements.qt6_lin.txt
```

In particular, make sure you have the previously mentioned Python package called `protoc-gen-mypy` installed:

```shell
python3 -m pip install --upgrade mypy-protobuf
```

### Preparing the compilation

Using some shell variables will make the following `protoc` command easier to read:

```shell
IMPORT_DIR=proto
OUTPUT_DIR=out/pylib
PROTOC_GEN_MYPY=${VIRTUAL_ENV}/bin/protoc-gen-mypy
```

Remember that you'll have to set these variables anew every time you close your terminal window.

### Compile the `*.proto` files into `*_pb2.py` files

The `*_pb2.py` files are what `mypy` requires in order to check types without producing errors. These `*_pb2.py` files are generated from the `*.proto` files by the Protobuf compiler `protoc`.

The following command will make `mypy` work without errors. Repeat it every time any `*.proto` file was changed (even if it was someone else who changed it).

```shell
protoc --plugin=protoc-gen-mypy=$PROTOC_GEN_MYPY \
	--proto_path=$IMPORT_DIR \
	--python_out=$OUTPUT_DIR \
	--mypy_out=$OUTPUT_DIR \
	$IMPORT_DIR/**/*.proto
```

If this command didn't work, you might have to set the shell variables from the previous step another time.

### Check if `mypy` runs error-free

Now you should be able to run this `mypy` command without any errors:

```shell
mypy pylib/anki qt/aqt
```

Happy type checking!
