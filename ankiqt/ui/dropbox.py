#!/usr/bin/env python
# coding: utf-8
# vim:foldmethod=indent:ai:ts=4:sw=4
version = (1,0,1)
helpGeneral = '''pyDropboxPath version %d.%d build %d

This script changes the name/location of your Dropbox folder in the database
that the program uses.
You are supposed to do the filesystem changes on your own - Explorer,
Finder, Gnome Nautilus, you have the power.

Supported/tested databases/schemas:
Database v0 "dropbox.db" schema v0:
 - Stable 0.7.x and below
Database v1 "config.db"  schema v1:
 - Forum builds 0.8.x with versions up to 0.8.114
 - Release candidates 1.0.x up to 1.0.10 (stable release)
 - May work with newer versions. Ask in the forum thread!

Of course, NO WARRANTIES, you CAN LOSE files, yadda yadda... Backup first.
As for the instructions... continue reading:

General instructions:
1-BACKUP YOUR DROPBOX
2-Close the dropbox client
  GUI: Right click it, select exit
  Shell: Use the 'dropbox stop' command or the likes
3-Use your OS tools to move/rename the Dropbox folder
''' % version

helpGui = helpGeneral + '''
Instructions (GUI):
--- Check for errors on the status bar after any of these steps ---
4-Check if the autodetected locations of Dropbox database
  AND Dropbox folder are right!
5-Put your new location of the Dropbox folder in the 'New Location' field
6-Press the 'Save location' button
7-Close this script and start dropbox (the program).
'''

helpShell = helpGeneral + '''
Instructions (Shell):
4-The syntax of the script is simple:
  pyDropboxPath.py --newlocation /some/place/DropboxNewLocation
5-Check for the messages on execution.
  If any errors should appear, move the Dropbox folder to old location
  before starting it again.
'''

helpWx = '''
You do not have wxPython installed. To install:
In Ubuntu:
	Search package manager and install,
	or use 'sudo apt-get install python-wxgtk2.8' if you feel advanced.
In Windows and OSX:
	Go to the wxpython website:
		http://www.wxpython.org/download.php#binaries
	And select the binary package relative to your arch and python version.
	(Please select one of the unicode versions)

Alternatively, you can use the command line (no bells or whistles):
pyDropboxPath.py --newlocation /some/place/DropboxNewLocation
'''

'''
CHANGELOG
2009-05-25.0.6
	Added darwin; should require someone to test
2009-05-29.0.7
	Using pickles as Arash mentioned
	Changed binascii to base64 module
	Using wx
	Minor misc changes
2009-06-01.0.8
	Working around a possible bug in sqlite3 by opening db after chdir()
2009-06-02.0.8.1
	The workaround was not complete, still opening sqlite with full path
2009-06-02.0.8.2
	Brown paper bag on me :(
	Three times is the charm for doing a simple bug fix.
2010-06-29.0.8.3
	Supporting 0.8 database version at least... copying from pyDropConflicts
2010-12-17.1.0.1
	If dropbox can release 1.0, why can't I? :)
	Supporting headless servers with command line parameter
'''

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


def WriteDbLocation(dbfile, dbver, newloc):
	if dbver == 0:
		newpath = b64encode(dumps(newloc))
	elif dbver == 1:
		newpath = os.path.abspath(newloc)
	else:
		raise Exception('Unhandled DB schema version %d' % dbver)
	connection = GetConn(dbfile)
	cursor = connection.cursor()
	cursor.execute('REPLACE INTO config (key,value) VALUES ("dropbox_path",?)', (newpath,))
	cursor.close()
	connection.close()


def ShellChangeLocation(newloc):
	try:
		dbfile, dbfnver = GetDbFolder()
		dbhost = os.path.join(os.path.dirname(dbfile), 'host.db')
		curdbfolder, dbver = ReadDbLocation(dbfile, dbfnver)
		print '>>> Database location:'
		print dbfile + ' (v%d/%d)' % (dbfnver,dbver)
		print '>>> Current dropbox folder location:'
		print curdbfolder
		if curdbfolder == newloc:
			print '\n=== No changes needed, the new location is already on database'
			return 0
		print '>>> Updating with new location: "%s"' % newloc
		WriteDbLocation(dbfile, dbver, newloc)
		print 'location changed successfully'
		print '>>> Removing host.db...'
		if os.path.isfile(dbhost):
			try:
				os.unlink(self.dbhost)
				print 'host.db removed'
			except Exception, e:
				print '''could not remove host.db (don't worry)'''
		print '\n=== Dropbox database changed!'
	except Exception, e:
		print '''
=== GOT EXCEPTION, exiting.
Do NOT forget to move dropbox folder back if you
want to start it from the old location. Exception caught is:
'''
		raise
	return 0


def main_is_frozen():
	return (
		hasattr(sys, "frozen") # new py2exe
		or
		hasattr(sys, "importers") # old py2exe
		or
		imp.is_frozen("__main__") # tools/freeze
	)


if __name__ == '__main__':
	if (not main_is_frozen()) and (len(sys.argv) > 1):
		# Check parameters, shell mode
		def usage(txt=None):
			print helpShell
			if txt: print '\n',txt,'\n'
			raw_input('Press ENTER to exit...')
			sys.exit(1)
		import getopt
		try:
			opts, args = getopt.gnu_getopt(sys.argv[1:] , 'h', ('help','newlocation='))
		except getopt.GetoptError:
			usage('Invalid parameters: %s' % sys.argv[1:])
		for opt, arg in opts:
			if opt in ('-h','--help'):
				usage()
			elif opt == '--newlocation':
				if not os.path.isdir(arg):
					usage('Invalid new location: %s' % arg)
				else:
					newloc = os.path.abspath(arg)
					sys.exit(ShellChangeLocation(newloc))
		usage() # catchall
	else:
		# Frozen (EXE) or no parameters, try GUI mode
		try:
			import wx
		except ImportError:
			print helpWx
			raw_input('Press ENTER to exit...')
			sys.exit(1)


class MainWindow(wx.Frame):
	DatabaseText = None
	CurrentFolderText = None
	NewFolderText = None
	NewFolderBtn = None
	SaveBtn = None
	dbfile = ''
	dbfnver = None
	dbver = None
	dbhost = ''
	curdbfolder = ''
	newdbfolder = ''
	def __init__(self, parent, title):
		wx.Frame.__init__(self, parent, wx.ID_ANY, title)
		self.CreateStatusBar()
		self.StatusBar.SetStatusText('Waiting.')

		panel = wx.Panel(self, wx.ID_ANY)
		self.panel = panel
		topsizer = wx.BoxSizer(wx.VERTICAL)
		colsizer = wx.GridBagSizer(hgap=5, vgap=5)

		w = wx.StaticText(panel, wx.ID_ANY, 'pyDropboxPath version %d.%d build %d' % version)
		w.SetFont(wx.Font(10, wx.SWISS, wx.NORMAL, wx.BOLD))
		topsizer.Add(w, 0, wx.CENTER|wx.ALL, 5)
		w = wx.StaticText(panel, wx.ID_ANY, 'READ THIS CAREFULLY')
		w.SetForegroundColour(wx.RED)
		topsizer.Add(w, 0, wx.CENTER|wx.ALL, 5)
		w = wx.TextCtrl(panel, wx.ID_ANY, helpGui, size=(500,230), style=wx.TE_MULTILINE|wx.TE_READONLY)
		topsizer.Add(w, 0, wx.CENTER, 5)

		w = wx.StaticText(panel, wx.ID_ANY, 'Database location:')
		colsizer.Add(w, pos=(0,0), border=5)
		w = wx.StaticText(panel, wx.ID_ANY, 'None', style=wx.TE_READONLY)
		self.DatabaseText = w
		colsizer.Add(w, pos=(0,1), border=5)

		w = wx.StaticText(panel, wx.ID_ANY, 'Current location:')
		colsizer.Add(w, pos=(1,0), border=5)
		w = wx.StaticText(panel, wx.ID_ANY, 'None', style=wx.TE_READONLY)
		self.CurrentFolderText = w
		colsizer.Add(w, pos=(1,1), border=5)

		w = wx.StaticText(panel, wx.ID_ANY, 'New folder location:')
		colsizer.Add(w, pos=(2,0), border=5)
		w = wx.StaticText(panel, wx.ID_ANY, 'None', style=wx.TE_READONLY)
		self.NewFolderText = w
		colsizer.Add(w, pos=(2,1), border=5)
		w = wx.Button(panel, wx.ID_ANY, "Browse...")
		w.Disable()
		self.NewFolderBtn = w
		self.Bind(wx.EVT_BUTTON, self.OnBrowseDBFolder, w)
		colsizer.Add(w, pos=(2,2), border=5)

		topsizer.Add(colsizer, 0, wx.CENTER|wx.ALL, 5)
		w = wx.Button(panel, wx.ID_ANY, "Save new dropbox folder location")
		w.Disable()
		self.SaveBtn = w
		self.Bind(wx.EVT_BUTTON, self.OnSave, w)
		topsizer.Add(w, 0, wx.CENTER|wx.ALL, 5)
		self.DatabaseText.SetLabel('None')
		self.ReadDatabase()
		self.SetSize(wx.Size(510, 550))
		#panel.SetAutoLayout(True)
		panel.SetSizer(topsizer)
		#topsizer.Fit(panel)
		#topsizer.SetSizeHints(panel)
		panel.Layout()
		self.SetClientSize(panel.GetBestSize())


	def ReadDatabase(self):
		try:
			self.dbfile, self.dbfnver = GetDbFolder()
			self.dbhost = os.path.join(os.path.dirname(self.dbfile), 'host.db')
			self.curdbfolder, self.dbver = ReadDbLocation(self.dbfile, self.dbfnver)
			self.DatabaseText.SetLabel(self.dbfile + ' (v%d/%d)' % (self.dbfnver,self.dbver))
			self.CurrentFolderText.SetLabel(self.curdbfolder)
			self.StatusBar.SetStatusText('Database read successfully. Select new location.')
			self.NewFolderBtn.Enable()
			self.NewFolderBtn.SetFocus()
			self.NewFolderText.SetLabel('None')
			if not self.IsMaximized():
				self.SetClientSize(self.panel.GetBestSize())
		except Exception, e:
			self.StatusBar.SetStatusText('Got exception: '+e.message)
			self.NewFolderBtn.Disable()
			return


	def OnBrowseDBFolder(self, event):
		dlg = wx.DirDialog(self.panel, message='Select NEW Dropbox Folder location (move first!)',
			style=wx.DD_DIR_MUST_EXIST)
		if dlg.ShowModal() != wx.ID_OK:
			return
		self.newdbfolder = dlg.GetPath()
		dlg.Destroy()
		if os.path.isdir(self.newdbfolder):
			self.NewFolderText.SetLabel(self.newdbfolder)
			self.StatusBar.SetStatusText('New folder selected. Press the save button to change dropbox.')
			self.SaveBtn.Enable()
		else:
			self.SaveBtn.Disable()
		if not self.IsMaximized():
			self.SetClientSize(self.panel.GetBestSize())


	def OnSave(self, event):
		try:
			WriteDbLocation(self.dbfile, self.dbver, self.newdbfolder)
			self.curdbfolder, self.newdbfolder = self.newdbfolder, ''
			self.CurrentFolderText.SetLabel(self.curdbfolder)
			self.NewFolderText.SetLabel('None')
			self.NewFolderBtn.SetFocus()
			self.SaveBtn.Disable()
			msg = 'Location changed successfully!'
			if os.path.isfile(self.dbhost):
				try:
					os.unlink(self.dbhost)
					msg += ' (host.db removed)'
				except Exception, e:
					msg += ' (could not remove host.db)'
				self.dbhost = ''
			self.StatusBar.SetStatusText(msg)
		except Exception, e:
			self.StatusBar.SetStatusText('Got exception: '+e.message)


class MyApp(wx.App):
	def OnInit(self):
		frame = MainWindow(None, 'pyDropboxPath')
		self.SetTopWindow(frame)
		frame.Show(True)
		return True


if __name__ == '__main__':
	app = MyApp(0)
	#app = MyApp(redirect=True)
	app.MainLoop()
