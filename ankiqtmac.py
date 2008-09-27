#!/usr/bin/env python
#
# hack because py2app barfs on a try block
#

import pkg_resources

import os, sys

modDir=os.path.dirname(os.path.abspath(__file__))
runningDir=os.path.split(modDir)[0]
# set up paths for local development
sys.path.insert(0, os.path.join(runningDir, "libanki"))
sys.path.insert(0, os.path.join(os.path.join(runningDir, ".."), "libanki"))

import ankiqt
ankiqt.run()
