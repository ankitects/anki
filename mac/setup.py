"""
Script for building Anki.app (Mac build).
Requires py2app.

Usage:
    python setup.py py2app
    python setup.py bdist_dmg
"""

from setuptools import setup, Command
import os, sys
from ankiqt import appVersion
scriptDir=os.path.split(os.path.dirname(os.path.abspath(sys.argv[0])))[0]
sys.path.append(os.path.join(scriptDir, "../libanki"))

class bdist_dmg(Command):

	description = "create a Mac disk image (.dmg) binary distribution"

	user_options = []

	def initialize_options(self):
		pass

	def finalize_options(self):
		pass

	def run(self):
                self.run_command('py2app')
                if 'debug' in os.environ:
                        return
                # zlib
                result = os.spawnvp(os.P_WAIT, 'hdiutil', (
                        'hdiutil create -ov -format UDZO ' +
                        '-volname Anki -srcfolder dist ' +
                        '-o Anki.dmg -imagekey zlib-level=9').split())
                if result is not 0:
                        raise Exception('dmg creation failed %d' % result)

APP = ['ankiqt/ankiqtmac.py']
VERSION = appVersion
DATA_FILES = [
#    'ankiqt',
    'libanki/anki/locale',
    'ankiqt/ankiqt/locale',
    'kakasi',
    #'audio',
    'ankiqt/imageformats',
    'libanki/anki/features/chinese/unihan.db',
    ]
PLIST = dict(
	CFBundleIdentifier = 'net.ichi2.anki',
	CFBundleName = 'Anki',
        CFBundleDocumentTypes=[dict(CFBundleTypeExtensions=["anki"],
                                    CFBundleTypeName="Anki Deck",
                                    CFBundleTypeRole="Editor")],
	CFBundleLocalizations = ['en'],
        )
OPTIONS = {
	'argv_emulation': True,
    'optimize': 0,
        'alias': 'debug' in os.environ,
	'plist': PLIST,
	'iconfile': 'ankiqt/mac/anki.icns',
    "includes": ["sip", "cgi", "encodings", "encodings.utf_8",
                 "encodings.shift_jis", "_multibytecodec",
                 "PyQt4.QtNetwork"],
    'packages': ["sqlalchemy", "pysqlite2", "simplejson"],
    'excludes': ['_gtkagg', '_tkagg', "_wxagg",
                 "wx", "_wx",
                 "Tkconstants", "Tkinter", "tcl", "pygame"],
     #'frameworks': ['libmp3lame.dylib'],
    'dylib_excludes': ['libncurses.5.dylib',
                       '_wxagg.so',
                       '_tkagg.so',
                       '_gtkagg.so',
                       'wx.so'],
}

setup(
    app = APP,
    version = VERSION,
    data_files = DATA_FILES,
    options = {'py2app': OPTIONS},
    setup_requires = ['py2app'],
    cmdclass = {'bdist_dmg': bdist_dmg},
)
