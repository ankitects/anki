"""
System File Locations
Retrieves common system path names on Windows XP/Vista
Depends only on ctypes, and retrieves path locations in Unicode
"""

import ctypes
from ctypes import windll, wintypes  # type: ignore

__license__ = "MIT"
__version__ = "0.2"
__author__ = "Ryan Ginstrom"
__description__ = "Retrieves common Windows system paths as Unicode strings"


class PathConstants:
    """
    Define constants here to avoid dependency on shellcon.
    Put it in a class to avoid polluting namespace
    """

    CSIDL_DESKTOP = 0
    CSIDL_PROGRAMS = 2
    CSIDL_PERSONAL = 5
    CSIDL_FAVORITES = 6
    CSIDL_STARTUP = 7
    CSIDL_RECENT = 8
    CSIDL_SENDTO = 9
    CSIDL_BITBUCKET = 10
    CSIDL_STARTMENU = 11
    CSIDL_MYDOCUMENTS = 12
    CSIDL_MYMUSIC = 13
    CSIDL_MYVIDEO = 14
    CSIDL_DESKTOPDIRECTORY = 16
    CSIDL_DRIVES = 17
    CSIDL_NETWORK = 18
    CSIDL_NETHOOD = 19
    CSIDL_FONTS = 20
    CSIDL_TEMPLATES = 21
    CSIDL_COMMON_STARTMENU = 22
    CSIDL_COMMON_PROGRAMS = 23
    CSIDL_COMMON_STARTUP = 24
    CSIDL_COMMON_DESKTOPDIRECTORY = 25
    CSIDL_APPDATA = 26
    CSIDL_PRINTHOOD = 27
    CSIDL_LOCAL_APPDATA = 28
    CSIDL_ALTSTARTUP = 29
    CSIDL_COMMON_ALTSTARTUP = 30
    CSIDL_COMMON_FAVORITES = 31
    CSIDL_INTERNET_CACHE = 32
    CSIDL_COOKIES = 33
    CSIDL_HISTORY = 34
    CSIDL_COMMON_APPDATA = 35
    CSIDL_WINDOWS = 36
    CSIDL_SYSTEM = 37
    CSIDL_PROGRAM_FILES = 38
    CSIDL_MYPICTURES = 39
    CSIDL_PROFILE = 40
    CSIDL_SYSTEMX86 = 41
    CSIDL_PROGRAM_FILESX86 = 42
    CSIDL_PROGRAM_FILES_COMMON = 43
    CSIDL_PROGRAM_FILES_COMMONX86 = 44
    CSIDL_COMMON_TEMPLATES = 45
    CSIDL_COMMON_DOCUMENTS = 46
    CSIDL_COMMON_ADMINTOOLS = 47
    CSIDL_ADMINTOOLS = 48
    CSIDL_CONNECTIONS = 49
    CSIDL_COMMON_MUSIC = 53
    CSIDL_COMMON_PICTURES = 54
    CSIDL_COMMON_VIDEO = 55
    CSIDL_RESOURCES = 56
    CSIDL_RESOURCES_LOCALIZED = 57
    CSIDL_COMMON_OEM_LINKS = 58
    CSIDL_CDBURN_AREA = 59
    # 60 unused
    CSIDL_COMPUTERSNEARME = 61


class WinPathsException(Exception):
    pass


def _err_unless_zero(result):
    if result == 0:
        return result
    else:
        raise WinPathsException(f"Failed to retrieve windows path: {result}")


_SHGetFolderPath = windll.shell32.SHGetFolderPathW
_SHGetFolderPath.argtypes = [
    wintypes.HWND,
    ctypes.c_int,
    wintypes.HANDLE,
    wintypes.DWORD,
    wintypes.LPCWSTR,
]
_SHGetFolderPath.restype = _err_unless_zero


def _get_path_buf(csidl):
    path_buf = ctypes.create_unicode_buffer(wintypes.MAX_PATH)
    result = _SHGetFolderPath(0, csidl, 0, 0, path_buf)
    return path_buf.value


def get_local_appdata():
    return _get_path_buf(PathConstants.CSIDL_LOCAL_APPDATA)


def get_appdata():
    return _get_path_buf(PathConstants.CSIDL_APPDATA)


def get_desktop():
    return _get_path_buf(PathConstants.CSIDL_DESKTOP)


def get_programs():
    """current user -> Start menu -> Programs"""
    return _get_path_buf(PathConstants.CSIDL_PROGRAMS)


def get_admin_tools():
    """current user -> Start menu -> Programs -> Admin tools"""
    return _get_path_buf(PathConstants.CSIDL_ADMINTOOLS)


def get_common_admin_tools():
    """all users -> Start menu -> Programs -> Admin tools"""
    return _get_path_buf(PathConstants.CSIDL_COMMON_ADMINTOOLS)


def get_common_appdata():
    return _get_path_buf(PathConstants.CSIDL_COMMON_APPDATA)


def get_common_documents():
    return _get_path_buf(PathConstants.CSIDL_COMMON_DOCUMENTS)


def get_cookies():
    return _get_path_buf(PathConstants.CSIDL_COOKIES)


def get_history():
    return _get_path_buf(PathConstants.CSIDL_HISTORY)


def get_internet_cache():
    return _get_path_buf(PathConstants.CSIDL_INTERNET_CACHE)


def get_my_pictures():
    """Get the user's My Pictures folder"""
    return _get_path_buf(PathConstants.CSIDL_MYPICTURES)


def get_personal():
    """AKA 'My Documents'"""
    return _get_path_buf(PathConstants.CSIDL_PERSONAL)


get_my_documents = get_personal


def get_program_files():
    return _get_path_buf(PathConstants.CSIDL_PROGRAM_FILES)


def get_program_files_common():
    return _get_path_buf(PathConstants.CSIDL_PROGRAM_FILES_COMMON)


def get_system():
    """Use with care and discretion"""
    return _get_path_buf(PathConstants.CSIDL_SYSTEM)


def get_windows():
    """Use with care and discretion"""
    return _get_path_buf(PathConstants.CSIDL_WINDOWS)


def get_favorites():
    return _get_path_buf(PathConstants.CSIDL_FAVORITES)


def get_startup():
    """current user -> start menu -> programs -> startup"""
    return _get_path_buf(PathConstants.CSIDL_STARTUP)


def get_recent():
    return _get_path_buf(PathConstants.CSIDL_RECENT)
