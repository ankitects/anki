# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Example use:
    p = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            )

    pipe_non_blocking_set(p.stdout.fileno())

    try:
        data = os.read(p.stdout.fileno(), 1)
    except PortableBlockingIOError as ex:
        if not pipe_non_blocking_is_error_blocking(ex):
            raise ex
"""

__all__ = (
    "pipe_non_blocking_set",
    "pipe_non_blocking_is_error_blocking",
    "PortableBlockingIOError")

import os
import sys

if sys.platform.startswith("win32"):
    def pipe_non_blocking_set(fd):
        """
        Set pipe in non blocking mode
        :param fd: file descriptor
        """
        # Constant could define globally but avoid polluting the name-space
        # thanks to: https://stackoverflow.com/questions/34504970
        import msvcrt

        from ctypes import windll, byref, wintypes, WinError, POINTER
        from ctypes.wintypes import HANDLE, DWORD, BOOL

        LPDWORD = POINTER(DWORD)

        PIPE_NOWAIT = wintypes.DWORD(0x00000001)

        def pipe_no_wait(pipefd):
            """
            Creates non-blocking pipe
            :param pipefd: pipe descriptor
            :return: True - operation was successful, False - failed
            """
            SetNamedPipeHandleState = windll.kernel32.SetNamedPipeHandleState
            SetNamedPipeHandleState.argtypes = [HANDLE, LPDWORD, LPDWORD, LPDWORD]
            SetNamedPipeHandleState.restype = BOOL

            h = msvcrt.get_osfhandle(pipefd)

            res = windll.kernel32.SetNamedPipeHandleState(h, byref(PIPE_NOWAIT), None, None)
            if res == 0:
                print(WinError())
                return False
            return True

        return pipe_no_wait(fd)


    def pipe_non_blocking_is_error_blocking(ex):
        """
        Checks if exception to be ignored
        :param ex: target exception
        :return: True can be ignored, False it will be re-thrown
        """
        if not isinstance(ex, PortableBlockingIOError):
            return False
        from ctypes import GetLastError
        ERROR_NO_DATA = 232

        return GetLastError() == ERROR_NO_DATA


    PortableBlockingIOError = OSError
else:
    def pipe_non_blocking_set(fd):
        """
        Set pipe in non blocking mode
        :param fd: file descriptor
        """
        import fcntl
        fl = fcntl.fcntl(fd, fcntl.F_GETFL)
        fcntl.fcntl(fd, fcntl.F_SETFL, fl | os.O_NONBLOCK)
        return True


    def pipe_non_blocking_is_error_blocking(ex):
        """
        Checks if exception to be ignored
        :param ex: target exception
        :return: True can be ignored, False it will be re-thrown
        """
        if not isinstance(ex, PortableBlockingIOError):
            return False
        return True


    PortableBlockingIOError = BlockingIOError


def non_blocking_readlines(f, chunk=1024):
    """
    Thanks to https://stackoverflow.com/questions/375427/a-non-blocking-read-on-a-subprocess-pipe-in-python
    Iterate over lines, yielding b'' when nothings left
    or when new data is not yet available.

    stdout_iter = iter(non_blocking_readlines(process.stdout))

    line = next(stdout_iter)  # will be a line or b''.
    :param chunk: amount of bytes to read
    :param f target pipe
    """
    import os

    fd = f.fileno()
    pipe_non_blocking_set(fd)

    blocks = []

    while True:
        try:
            data = os.read(fd, chunk)
            if not data:
                # case were reading finishes with no trailing newline
                yield b''.join(blocks)
                blocks.clear()
        except PortableBlockingIOError as ex:
            if not pipe_non_blocking_is_error_blocking(ex):
                raise ex

            yield b''
            continue

        while True:
            n = data.find(b'\n')
            if n == -1:
                break

            yield b''.join(blocks) + data[:n + 1]
            data = data[n + 1:]
            blocks.clear()
        blocks.append(data)
