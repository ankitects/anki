Anki
====

Prerequisites
-------------

To install the prerequisites on Ubuntu/Debian, please use the following
command:

```bash
$ sudo apt-get install python-qt4 mplayer lame libportaudio2 python-sqlalchemy
```

If you're on another distribution the packages may be named differently, so
please consult your package manager.

Your Python version will need to be 2.6 or 2.7 (not 3+), and both Qt and PyQt
need to be 4.7 or later.

Installation & Running
----------------------

Anki does not need installing, and can be run from the directory it is
extracted to. If you extracted it to `~/anki-2.0` for example, you can run Anki
by simply typing `~/anki-2.0/runanki` in a terminal.

If you'd like to install it system wide, change to the folder you extracted it
to, and run 'sudo make install'. If you need to uninstall Anki in the future,
you can do so by typing 'sudo make uninstall'.

More information
----------------

For more information and the latest version, please see the website at 
[ankisrs.net](http://ankisrs.net/)
