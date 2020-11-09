
from setuptools import setup
APP = ['qt/runanki']
DATA_FILES = [('images/pig.icns')]
OPTIONS = {
    'argv_emulation': True,
    'site_packages': True,
    'packages': [],
    'iconfile': 'images/pig.icns',
    'plist': {
        'CFBundleName': 'Bobs',
        # 'PyRuntimeLocations': [
        #     '@executable_path/ /opt/miniconda3/envs/anki_37/lib/libpython3.7.dylib',
        #     '/opt/miniconda3/lib/libpython3.7m.dylib'
        #    ]
    }
}
setup(
    app=APP,
    data_files=DATA_FILES,
    options={'py2app': OPTIONS},
    setup_requires=['py2app'],
)