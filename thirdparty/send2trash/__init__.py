# Copyright 2010 Hardcoded Software (http://www.hardcoded.net)

# This software is licensed under the "BSD" License as described in the "LICENSE" file, 
# which should be included with this package. The terms are also available at 
# http://www.hardcoded.net/licenses/bsd_license

import sys

if sys.platform == u'darwin':
    from .plat_osx import send2trash
elif sys.platform == u'win32':
    from .plat_win import send2trash
else:
    from .plat_other import send2trash
