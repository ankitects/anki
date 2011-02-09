from setuptools import setup, find_packages
import sys, os

import anki

setup(name='anki',
      version=anki.version,
      description='An intelligent spaced-repetition memory training library',
      long_description="",
      # Get strings from http://www.python.org/pypi?%3Aaction=list_classifiers
      classifiers=[
    'Intended Audience :: Developers',
    'Intended Audience :: Education',
    'License :: OSI Approved :: GNU General Public License (GPL)',
    'Operating System :: OS Independent',
    'Programming Language :: Python',
    'Topic :: Education',
    'Topic :: Software Development :: Libraries :: Python Modules',
        ],
      keywords='',
      author='Damien Elmes',
      author_email='anki@ichi2.net',
      url='http://ichi2.net/anki/index.html',
      license='GPLv3',
      packages=["anki", "anki.importing", "anki.template"],
      package_data={'anki': ['locale/*/*/*'],},
      include_package_data=True,
      zip_safe=False,
      install_requires=[
        ],
      )
