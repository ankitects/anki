from setuptools import setup, find_packages
from glob import glob
import sys, os

setup(
    name='anki-woodrow',
    version='2.1.14+master',
    description='Memory and Learning system',
    license='GPL v3',
    author='Ankitects Pty Ltd and contributors',
    packages=find_packages(),
    install_requires=[
        'future',
        'beautifulsoup4',
        'send2trash',
        'requests',
        'pyaudio',
        'decorator',
        'markdown',
        'jsonschema',
        ],
    entry_points={
        'console_scripts': ['runanki = aqt.__init__:run']
    },
    classifiers=[
        'Operating System :: OS Independent',
        'Programming Language :: Python :: 3.6',
        'Operating System :: MacOS :: MacOS X',
        'Operating System :: Microsoft :: Windows',
        'Operating System :: POSIX',
        'License :: OSI Approved :: GNU General Public License v3 (GPLv3)'],
    data_files=[
        ('.', glob('anki.*')),
        ('designer', glob('designer/icons.qrc')),
        ('designer', glob('designer/*.ui')),
        ('designer', glob('designer/icons/*.png')),
        ('designer', glob('designer/icons/*.svg')),
        ('web', glob('web/*.js')),
        ('web', glob('web/*.css')),
        ('web/imgs', glob('web/imgs/*')),
        ('web/mathjax', glob('web/mathjax/*.js')),
        ('web/mathjax/extensions', glob('web/mathjax/extensions/*.js')),
        ('web/mathjax/extensions/HTML-CSS', glob('web/mathjax/extensions/HTML-CSS/*.js')),
        ('web/mathjax/extensions/TeX', glob('web/mathjax/extensions/TeX/*.js')),
        ('web/mathjax/extensions/TeX/mhchem3', glob('web/mathjax/extensions/TeX/mhchem3/*.js')),
        ('web/mathjax/fonts/HTML-CSS/TeX/woff', glob('web/mathjax/fonts/HTML-CSS/TeX/woff/*.js')),
        ('web/mathjax/jax/element/mml', glob('web/mathjax/jax/element/mml/*.js')),
        ('web/mathjax/jax/element/mml/optable', glob('web/mathjax/jax/element/mml/optable/*.js')),
        ('web/mathjax/jax/input/AsciiMath', glob('web/mathjax/jax/input/AsciiMath/*.js')),
        ('web/mathjax/jax/input/MathML', glob('web/mathjax/jax/input/MathML/*.js')),
        ('web/mathjax/jax/input/MathML/entities', glob('web/mathjax/jax/input/MathML/entities/*.js')),
        ('web/mathjax/jax/input/TeX', glob('web/mathjax/jax/input/TeX/*.js')),
        ('web/mathjax/jax/output/CommonHTML', glob('web/mathjax/jax/output/CommonHTML/*.js')),
        ('web/mathjax/jax/output/CommonHTML/autoload', glob('web/mathjax/jax/output/CommonHTML/autoload/*.js')),
        ('web/mathjax/jax/output/CommonHTML/fonts/TeX', glob('web/mathjax/jax/output/CommonHTML/fonts/TeX/*.js')),
        ('web/mathjax/jax/output/HTML-CSS', glob('web/mathjax/jax/output/HTML-CSS/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/autoload', glob('web/mathjax/jax/output/HTML-CSS/autoload/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/AMS/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/AMS/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Caligraphic/Bold', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Caligraphic/Bold/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Caligraphic/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Caligraphic/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Fraktur/Bold', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Fraktur/Bold/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Fraktur/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Fraktur/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/Bold', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/Bold/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/BoldItalic', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/BoldItalic/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/Italic', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/Italict/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Greek/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Main/Bold', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Main/Bold/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Main/Italic', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Main/Italic/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Main/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Main/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Math/BoldItalic', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Math/BoldItalic/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Math/Italic', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Math/Italic/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Bold', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Bold/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Italic', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Italic/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/SansSerif/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Script/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Script/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size1/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size1/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size2/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size2/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size3/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size3/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size4/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Size4/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Typewriter/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/Typewriter/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/WinChrome/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/WinChrome/Regular/*.js')),
        ('web/mathjax/jax/output/HTML-CSS/fonts/TeX/WinIE6/Regular', glob('web/mathjax/jax/output/HTML-CSS/fonts/TeX/WinIE6/Regular/*.js')),
    ],    
)

