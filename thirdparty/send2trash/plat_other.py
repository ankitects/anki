# Copyright 2010 Hardcoded Software (http://www.hardcoded.net)

# This software is licensed under the "BSD" License as described in the "LICENSE" file, 
# which should be included with this package. The terms are also available at 
# http://www.hardcoded.net/licenses/bsd_license

# This is a reimplementation of plat_other.py with reference to the
# freedesktop.org trash specification:
#   [1] http://www.freedesktop.org/wiki/Specifications/trash-spec
#   [2] http://www.ramendik.ru/docs/trashspec.html
# See also:
#   [3] http://standards.freedesktop.org/basedir-spec/basedir-spec-latest.html
#
# For external volumes this implementation will raise an exception if it can't
# find or create the user's trash directory.

import sys
import os
import os.path as op
from datetime import datetime
import stat
from urllib import quote
from io import open

FILES_DIR = u'files'
INFO_DIR = u'info'
INFO_SUFFIX = u'.trashinfo'

# Default of ~/.local/share [3]
XDG_DATA_HOME = op.expanduser(os.environ.get(u'XDG_DATA_HOME', u'~/.local/share'))
HOMETRASH = op.join(XDG_DATA_HOME, u'Trash')

uid = os.getuid()
TOPDIR_TRASH = u'.Trash'
TOPDIR_FALLBACK = u'.Trash-' + unicode(uid)

def is_parent(parent, path):
    path = op.realpath(path) # In case it's a symlink
    parent = op.realpath(parent)
    return path.startswith(parent)

def format_date(date):
    return date.strftime(u"%Y-%m-%dT%H:%M:%S")

def info_for(src, topdir):
    # ...it MUST not include a ".."" directory, and for files not "under" that
    # directory, absolute pathnames must be used. [2]
    if topdir is None or not is_parent(topdir, src):
        src = op.abspath(src)
    else:
        src = op.relpath(src, topdir)

    info  = u"[Trash Info]\n"
    info += u"Path=" + quote(src) + u"\n"
    info += u"DeletionDate=" + format_date(datetime.now()) + u"\n"
    return info

def check_create(dir):
    # use 0700 for paths [3]
    if not op.exists(dir):
        os.makedirs(dir, 0700)

def trash_move(src, dst, topdir=None):
    filename = op.basename(src)
    filespath = op.join(dst, FILES_DIR)
    infopath = op.join(dst, INFO_DIR)
    base_name, ext = op.splitext(filename)

    counter = 0
    destname = filename
    while op.exists(op.join(filespath, destname)) or op.exists(op.join(infopath, destname + INFO_SUFFIX)):
        counter += 1
        destname = u'%s %s%s' % (base_name, counter, ext)
    
    check_create(filespath)
    check_create(infopath)
    
    os.rename(src, op.join(filespath, destname))
    f = open(op.join(infopath, destname + INFO_SUFFIX), u'w')
    f.write(info_for(src, topdir))
    f.close()

def find_mount_point(path):
    # Even if something's wrong, "/" is a mount point, so the loop will exit.
    # Use realpath in case it's a symlink
    path = op.realpath(path) # Required to avoid infinite loop
    while not op.ismount(path):
        path = op.split(path)[0]
    return path

def find_ext_volume_global_trash(volume_root):
    # from [2] Trash directories (1) check for a .Trash dir with the right
    # permissions set.
    trash_dir = op.join(volume_root, TOPDIR_TRASH)
    if not op.exists(trash_dir):
        return None
    
    mode = os.lstat(trash_dir).st_mode
    # vol/.Trash must be a directory, cannot be a symlink, and must have the
    # sticky bit set.
    if not op.isdir(trash_dir) or op.islink(trash_dir) or not (mode & stat.S_ISVTX):
        return None

    trash_dir = op.join(trash_dir, unicode(uid))
    try:
        check_create(trash_dir)
    except OSError:
        return None
    return trash_dir

def find_ext_volume_fallback_trash(volume_root):
    # from [2] Trash directories (1) create a .Trash-$uid dir.
    trash_dir = op.join(volume_root, TOPDIR_FALLBACK)
    # Try to make the directory, if we can't the OSError exception will escape
    # be thrown out of send2trash.
    check_create(trash_dir)
    return trash_dir

def find_ext_volume_trash(volume_root):
    trash_dir = find_ext_volume_global_trash(volume_root)
    if trash_dir is None:
        trash_dir = find_ext_volume_fallback_trash(volume_root)
    return trash_dir

# Pull this out so it's easy to stub (to avoid stubbing lstat itself)
def get_dev(path):
    return os.lstat(path).st_dev

def send2trash(path):
    if not isinstance(path, unicode):
        path = unicode(path, sys.getfilesystemencoding())
    if not op.exists(path):
        raise OSError(u"File not found: %s" % path)
    # ...should check whether the user has the necessary permissions to delete
    # it, before starting the trashing operation itself. [2]
    if not os.access(path, os.W_OK):
        raise OSError(u"Permission denied: %s" % path)
    # if the file to be trashed is on the same device as HOMETRASH we
    # want to move it there.
    path_dev = get_dev(path)
    
    # If XDG_DATA_HOME or HOMETRASH do not yet exist we need to stat the
    # home directory, and these paths will be created further on if needed.
    trash_dev = get_dev(op.expanduser(u'~'))

    if path_dev == trash_dev:
        topdir = XDG_DATA_HOME
        dest_trash = HOMETRASH
    else:
        topdir = find_mount_point(path)
        trash_dev = get_dev(topdir)
        if trash_dev != path_dev:
            raise OSError(u"Couldn't find mount point for %s" % path)
        dest_trash = find_ext_volume_trash(topdir)
    trash_move(path, dest_trash, topdir)
