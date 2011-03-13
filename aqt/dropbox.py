#!/usr/bin/env python
# stripped down version of http://dl.dropbox.com/u/552/pyDropboxPath/1.0.1/index.html

import imp, os, sys, sqlite3
from base64 import b64encode, b64decode
from pickle import dumps, loads

def GetDbFolder():
	if sys.platform == 'win32':
		assert os.environ.has_key('APPDATA'), Exception('APPDATA env variable not found')
		dbpath = os.path.join(os.environ['APPDATA'],'Dropbox')
	elif sys.platform in ('linux2','darwin'):
		assert os.environ.has_key('HOME'), Exception('HOME env variable not found')
		dbpath = os.path.join(os.environ['HOME'],'.dropbox')
	else: # FIXME other archs?
		raise Exception('platform %s not known, please report' % sys.platform)
	if os.path.isfile(os.path.join(dbpath,'config.db')):
		dbfn, dbfnver = os.path.join(dbpath,'config.db'), 1
	elif os.path.isfile(os.path.join(dbpath, 'dropbox.db')):
		dbfn, dbfnver = os.path.join(dbpath,'dropbox.db'), 0
	else:
		raise Exception('Dropbox database not found, is dropbox installed?')
	return (dbfn, dbfnver)


def GetConn(dbfile):
	lastdir = os.getcwd()
	os.chdir(os.path.dirname(dbfile))
	connection = sqlite3.connect(os.path.basename(dbfile), isolation_level=None)
	os.chdir(lastdir)
	return connection


def ReadDbLocation(dbfile, dbfnver):
	connection = GetConn(dbfile)
	cursor = connection.cursor()
	if dbfnver == 0: # dropbox.db, old-style
		dbver = 0
	elif dbfnver == 1: # config.db, can be upgraded, lets check schema
		cursor.execute('SELECT value FROM config WHERE key="config_schema_version"')
		row = cursor.fetchone()
		dbver = row[0]
	# dup code now, but maybe someday it will be confusing
	if dbver == 0:
		cursor.execute('SELECT value FROM config WHERE key="dropbox_path"')
	elif dbver == 1:
		cursor.execute('SELECT value FROM config WHERE key="dropbox_path"')
	else:
		raise Exception('Unhandled DB schema version %d' % dbver)

	row = cursor.fetchone()
	cursor.close()
	connection.close()
	if row is None:
		if sys.platform == 'win32':
			import ctypes
			dll = ctypes.windll.shell32
			buf = ctypes.create_string_buffer(300)
			dll.SHGetSpecialFolderPathA(None, buf, 0x0005, False)
			dbfolder = os.path.join(buf.value,'My Dropbox')
		elif sys.platform in ('linux2','darwin'):
			dbfolder = os.path.join(os.environ['HOME'],'Dropbox')
		else:
			raise Exception('platform %s not known, please report' % sys.platform)
		#print 'No dropbox path defined in config, using default location %s' % dbfolder
	else:
		if dbver == 0: # always b64encoded
			dbfolder = loads(b64decode(row[0]))
		elif dbver == 1: # normal
			dbfolder = row[0]
		else:
			raise Exception('Unhandled DB schema version %d' % dbver)
	return (dbfolder, dbver)

def getPath():
        return ReadDbLocation(*GetDbFolder())[0]
