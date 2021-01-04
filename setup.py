from setuptools import setup
APP = ['qt/runanki']
DATA_FILES = [('images/pig.icns')]
RESOURCES = ['qt/aqt_data', 'testing/libs']
OPTIONS = {
    'argv_emulation': False,
    'site_packages': True,
    'resources': RESOURCES,
    'iconfile': 'images/pig.icns',
    'plist': {
        'CFBundleName': 'Bobs',
    }
}
setup(
    app=APP,
    data_files=DATA_FILES,
    options={'py2app': OPTIONS},
    setup_requires=['py2app'],
)
