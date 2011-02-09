from setuptools import setup, find_packages
import sys, os

import ankiqt

setup(name='ankiqt',
      version=ankiqt.appVersion,
      description='An intelligent spaced-repetition memory training program',
      long_description="",
      # Get strings from http://www.python.org/pypi?%3Aaction=list_classifiers
      classifiers=[
    'Intended Audience :: Education',
    'License :: OSI Approved :: GNU General Public License (GPL)',
    'Operating System :: OS Independent',
    'Programming Language :: Python',
    'Topic :: Education',
        ],
      keywords='',
      author='Damien Elmes',
      author_email='anki@ichi2.net',
      url='http://ichi2.net/anki/index.html',
      license='GPLv3',
      packages=find_packages(),
      include_package_data=True,
      install_requires = 'anki >= ' + ankiqt.appVersion,
      zip_safe=False,
      package_data={'ankiqt':
                    ['locale/*/*/*']},
      scripts = ['anki'],
      )
