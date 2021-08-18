# coding: utf-8
# ------------------------------------------------------------------------------
#
# mpv.py - Control mpv from Python using JSON IPC
#
# Copyright (c) 2015 Lars Gustäbel <lars@gustaebel.de>
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
# ------------------------------------------------------------------------------

# pylint: disable=raise-missing-from

import inspect
import json
import os
import select
import socket
import subprocess
import sys
import tempfile
import threading
import time
from distutils.spawn import (  # pylint: disable=import-error,no-name-in-module
    find_executable,
)
from queue import Empty, Full, Queue
from typing import Dict, Optional

from anki.utils import isWin


class MPVError(Exception):
    pass


class MPVProcessError(MPVError):
    pass


class MPVCommunicationError(MPVError):
    pass


class MPVCommandError(MPVError):
    pass


class MPVTimeoutError(MPVError):
    pass


if isWin:
    # pylint: disable=import-error
    import pywintypes
    import win32file  # pytype: disable=import-error
    import win32pipe
    import winerror


class MPVBase:
    """Base class for communication with the mpv media player via unix socket
    based JSON IPC.
    """

    executable = find_executable("mpv")
    popenEnv: Optional[Dict[str, str]] = None

    default_argv = [
        "--idle",
        "--no-terminal",
        "--force-window=no",
        "--ontop",
        "--audio-display=no",
        "--keep-open=no",
        "--autoload-files=no",
        "--gapless-audio=no",
    ]

    def __init__(self, window_id=None, debug=False):
        self.window_id = window_id
        self.debug = debug

        self._prepare_socket()
        self._prepare_process()
        self._start_process()
        self._start_socket()
        self._prepare_thread()
        self._start_thread()

    def __del__(self):
        self._stop_thread()
        self._stop_process()
        self._stop_socket()

    def _thread_id(self):
        return threading.get_ident()

    #
    # Process
    #
    def _prepare_process(self):
        """Prepare the argument list for the mpv process."""
        self.argv = [self.executable]
        self.argv += self.default_argv
        self.argv += [f"--input-ipc-server={self._sock_filename}"]
        if self.window_id is not None:
            self.argv += [f"--wid={str(self.window_id)}"]

    def _start_process(self):
        """Start the mpv process."""
        self._proc = subprocess.Popen(self.argv, env=self.popenEnv)

    def _stop_process(self):
        """Stop the mpv process."""
        if hasattr(self, "_proc"):
            try:
                self._proc.terminate()
                self._proc.wait()
            except ProcessLookupError:
                pass

    #
    # Socket communication
    #
    def _prepare_socket(self):
        """Create a random socket filename which we pass to mpv with the
        --input-unix-socket option.
        """
        if isWin:
            self._sock_filename = "ankimpv"
            return
        fd, self._sock_filename = tempfile.mkstemp(prefix="mpv.")
        os.close(fd)
        os.remove(self._sock_filename)

    def _start_socket(self):
        """Wait for the mpv process to create the unix socket and finish
        startup.
        """
        start = time.time()
        while self.is_running() and time.time() < start + 10:
            time.sleep(0.1)

            if isWin:
                # named pipe
                try:
                    self._sock = win32file.CreateFile(
                        r"\\.\pipe\ankimpv",
                        win32file.GENERIC_READ | win32file.GENERIC_WRITE,
                        0,
                        None,
                        win32file.OPEN_EXISTING,
                        0,
                        None,
                    )
                    win32pipe.SetNamedPipeHandleState(
                        self._sock, 1, None, None  # PIPE_NOWAIT
                    )
                except pywintypes.error as err:
                    if err.args[0] == winerror.ERROR_FILE_NOT_FOUND:
                        pass
                    else:
                        break
                else:
                    break
            else:
                # unix socket
                try:
                    self._sock = socket.socket(socket.AF_UNIX)
                    self._sock.connect(self._sock_filename)
                except (FileNotFoundError, ConnectionRefusedError):
                    self._sock.close()
                    continue
                else:
                    break
        else:
            raise MPVProcessError("unable to start process")

    def _stop_socket(self):
        """Clean up the socket."""
        if hasattr(self, "_sock"):
            self._sock.close()
        if hasattr(self, "_sock_filename"):
            try:
                os.remove(self._sock_filename)
            except OSError:
                pass

    def _prepare_thread(self):
        """Set up the queues for the communication threads."""
        self._request_queue = Queue(1)
        self._response_queues = {}
        self._event_queue = Queue()
        self._stop_event = threading.Event()

    def _start_thread(self):
        """Start up the communication threads."""
        self._thread = threading.Thread(target=self._reader)
        self._thread.daemon = True
        self._thread.start()

    def _stop_thread(self):
        """Stop the communication threads."""
        if hasattr(self, "_stop_event"):
            self._stop_event.set()
        if hasattr(self, "_thread"):
            self._thread.join()

    def _reader(self):
        """Read the incoming json messages from the unix socket that is
        connected to the mpv process. Pass them on to the message handler.
        """
        buf = b""
        while not self._stop_event.is_set():
            if isWin:
                try:
                    (n, b) = win32file.ReadFile(self._sock, 4096)
                    buf += b
                except pywintypes.error as err:
                    if err.args[0] == winerror.ERROR_NO_DATA:
                        time.sleep(0.1)
                        continue
                    elif err.args[0] == winerror.ERROR_BROKEN_PIPE:
                        return
                    else:
                        raise
            else:
                r, w, e = select.select([self._sock], [], [], 1)
                if r:
                    try:
                        b = self._sock.recv(1024)
                        if not b:
                            break
                        buf += b
                    except ConnectionResetError:
                        return

            newline = buf.find(b"\n")
            while newline >= 0:
                data = buf[: newline + 1]
                buf = buf[newline + 1 :]

                if self.debug:
                    sys.stdout.write(f"<<< {data.decode('utf8', 'replace')}")

                message = self._parse_message(data)
                self._handle_message(message)

                newline = buf.find(b"\n")

    #
    # Message handling
    #
    def _compose_message(self, message):
        """Return a json representation from a message dictionary."""
        # XXX may be strict is too strict ;-)
        data = json.dumps(message)
        return data.encode("utf8", "strict") + b"\n"

    def _parse_message(self, data):
        """Return a message dictionary from a json representation."""
        # XXX may be strict is too strict ;-)
        data = data.decode("utf8", "strict")
        return json.loads(data)

    def _handle_message(self, message):
        """Handle different types of incoming messages, i.e. responses to
        commands or asynchronous events.
        """
        if "error" in message:
            # This message is a reply to a request.
            try:
                thread_id = self._request_queue.get(timeout=1)
            except Empty:
                raise MPVCommunicationError("got a response without a pending request")

            self._response_queues[thread_id].put(message)

        elif "event" in message:
            # This message is an asynchronous event.
            self._event_queue.put(message)

        else:
            raise MPVCommunicationError(f"invalid message {message!r}")

    def _send_message(self, message, timeout=None):
        """Send a message/command to the mpv process, message must be a
        dictionary of the form {"command": ["arg1", "arg2", ...]}. Responses
        from the mpv process must be collected using _get_response().
        """
        data = self._compose_message(message)

        if self.debug:
            sys.stdout.write(f">>> {data.decode('utf8', 'replace')}")

        # Request/response cycles are coordinated across different threads, so
        # that they don't get mixed up. This makes it possible to use commands
        # (e.g. fetch properties) from event callbacks that run in a different
        # thread context.
        thread_id = self._thread_id()
        if thread_id not in self._response_queues:
            # Prepare a response queue for the thread to wait on.
            self._response_queues[thread_id] = Queue()

        # Put the id of the current thread on the request queue. This id is
        # later used to associate responses from the mpv process with this
        # request.
        try:
            self._request_queue.put(thread_id, block=True, timeout=timeout)
        except Full:
            raise MPVTimeoutError("unable to put request")

        # Write the message data to the socket.
        if isWin:
            win32file.WriteFile(self._sock, data)
        else:
            while data:
                size = self._sock.send(data)
                if size == 0:
                    raise MPVCommunicationError("broken sender socket")
                data = data[size:]

    def _get_response(self, timeout=None):
        """Collect the response message to a previous request. If there was an
        error a MPVCommandError exception is raised, otherwise the command
        specific data is returned.
        """
        try:
            message = self._response_queues[self._thread_id()].get(
                block=True, timeout=timeout
            )
        except Empty:
            raise MPVTimeoutError("unable to get response")

        if message["error"] != "success":
            raise MPVCommandError(message["error"])
        else:
            return message.get("data")

    def _get_event(self, timeout=None):
        """Collect a single event message that has been received out-of-band
        from the mpv process. If a timeout is specified and there have not
        been any events during that period, None is returned.
        """
        try:
            return self._event_queue.get(block=timeout is not None, timeout=timeout)
        except Empty:
            return None

    def _send_request(self, message, timeout=None, _retry=1):
        """Send a command to the mpv process and collect the result."""
        self.ensure_running()
        try:
            self._send_message(message, timeout)
            return self._get_response(timeout)
        except MPVCommandError as e:
            raise MPVCommandError(f"{message['command']!r}: {e}")
        except Exception as e:
            if _retry:
                print("mpv timed out, restarting")
                self._stop_process()
                return self._send_request(message, timeout, _retry - 1)
            else:
                raise

    def _register_callbacks(self):
        """Will be called after mpv restart to reinitialize callbacks
        defined in MPV subclass
        """

    #
    # Public API
    #
    def is_running(self):
        """Return True if the mpv process is still active."""
        return self._proc.poll() is None

    def ensure_running(self):
        if not self.is_running():
            self._stop_thread()
            self._stop_process()
            self._stop_socket()
            self._prepare_socket()
            self._prepare_process()
            self._start_process()
            self._start_socket()
            self._prepare_thread()
            self._start_thread()
            self._register_callbacks()

    def close(self):
        """Shutdown the mpv process and our communication setup."""
        if self.is_running():
            self._send_request({"command": ["quit"]}, timeout=1)
            self._stop_process()
        self._stop_thread()
        self._stop_socket()
        self._stop_process()


class MPV(MPVBase):
    """Class for communication with the mpv media player via unix socket
    based JSON IPC. It adds a few usable methods and a callback API.

    To automatically register methods as event callbacks, subclass this
    class and define specially named methods as follows:

        def on_file_loaded(self):
            # This is called for every 'file-loaded' event.
            ...

        def on_property_time_pos(self, position):
            # This is called whenever the 'time-pos' property is updated.
            ...

    Please note that callbacks are executed inside a separate thread. The
    MPV class itself is completely thread-safe. Requests from different
    threads to the same MPV instance are synchronized.
    """

    def __init__(self, *args, **kwargs):
        self._callbacks_queue = Queue()
        self._callbacks_initialized = False

        super().__init__(*args, **kwargs)

        self._register_callbacks()

    def _register_callbacks(self):
        self._callbacks = {}
        self._property_serials = {}
        self._new_serial = iter(range(sys.maxsize))

        # Enumerate all methods and auto-register callbacks for
        # events and property-changes.
        for method_name, method in inspect.getmembers(self):
            if not inspect.ismethod(method):
                continue

            # Bypass MPVError: no such event 'init'
            if method_name == "on_init":
                continue

            if method_name.startswith("on_property_"):
                name = method_name[12:]
                name = name.replace("_", "-")
                self.register_property_callback(name, method)

            elif method_name.startswith("on_"):
                name = method_name[3:]
                name = name.replace("_", "-")
                self.register_callback(name, method)

        self._callbacks_initialized = True
        while True:
            try:
                message = self._callbacks_queue.get_nowait()
            except Empty:
                break
            self._handle_event(message)

        # Simulate an init event when the process and all callbacks have been
        # completely set up.
        if hasattr(self, "on_init"):
            # pylint: disable=no-member
            self.on_init()

    #
    # Socket communication
    #
    def _start_thread(self):
        """Start up the communication threads."""
        super()._start_thread()
        if not hasattr(self, "_event_thread"):
            self._event_thread = threading.Thread(target=self._event_reader)
            self._event_thread.daemon = True
            self._event_thread.start()

    #
    # Event/callback API
    #
    def _event_reader(self):
        """Collect incoming event messages and call the event handler."""
        while True:
            message = self._get_event(timeout=1)
            if message is None:
                continue

            self._handle_event(message)

    def _handle_event(self, message):
        """Lookup and call the callbacks for a particular event message."""
        if not self._callbacks_initialized:
            self._callbacks_queue.put(message)
            return

        if message["event"] == "property-change":
            name = f"property-{message['name']}"
        else:
            name = message["event"]

        for callback in self._callbacks.get(name, []):
            if "data" in message:
                callback(message["data"])
            else:
                callback()

    def register_callback(self, name, callback):
        """Register a function `callback` for the event `name`."""
        try:
            self.command("enable_event", name)
        except MPVCommandError:
            raise MPVError(f"no such event {name!r}")

        self._callbacks.setdefault(name, []).append(callback)

    def unregister_callback(self, name, callback):
        """Unregister a previously registered function `callback` for the event
        `name`.
        """
        try:
            callbacks = self._callbacks[name]
        except KeyError:
            raise MPVError(f"no callbacks registered for event {name!r}")

        try:
            callbacks.remove(callback)
        except ValueError:
            raise MPVError(f"callback {callback!r} not registered for event {name!r}")

    def register_property_callback(self, name, callback):
        """Register a function `callback` for the property-change event on
        property `name`.
        """
        # Property changes are normally not sent over the connection unless they
        # are requested using the 'observe_property' command.

        # XXX We manually have to check for the existence of the property name.
        # Apparently observe_property does not check it :-(
        proplist = self.command("get_property", "property-list", timeout=5)
        if name not in proplist:
            raise MPVError(f"no such property {name!r}")

        self._callbacks.setdefault(f"property-{name}", []).append(callback)

        # 'observe_property' expects some kind of id which can be used later
        # for unregistering with 'unobserve_property'.
        serial = next(self._new_serial)
        self.command("observe_property", serial, name)
        self._property_serials[(name, callback)] = serial
        return serial

    def unregister_property_callback(self, name, callback):
        """Unregister a previously registered function `callback` for the
        property-change event on property `name`.
        """
        try:
            callbacks = self._callbacks[f"property-{name}"]
        except KeyError:
            raise MPVError(f"no callbacks registered for property {name!r}")

        try:
            callbacks.remove(callback)
        except ValueError:
            raise MPVError(
                f"callback {callback!r} not registered for property {name!r}"
            )

        serial = self._property_serials.pop((name, callback))
        self.command("unobserve_property", serial)

    #
    # Public API
    #
    def command(self, *args, timeout=1):
        """Execute a single command on the mpv process and return the result."""
        return self._send_request({"command": list(args)}, timeout=timeout)

    def get_property(self, name):
        """Return the value of property `name`."""
        return self.command("get_property", name)

    def set_property(self, name, value):
        """Set the value of property `name`."""
        return self.command("set_property", name, value)


# alias this module for backwards compat
sys.modules["anki.mpv"] = sys.modules["aqt.mpv"]
