all:
	@echo "You can run Anki with ./anki"
	@echo "If you wish to install it system wide, type 'sudo make install'"
	@echo "Uninstall with 'sudo make uninstall'"

install:
	@test ! -d .git || (echo "Install from git is not supported. Please use a tarball."; false)
	rm -rf /usr/share/anki
	mkdir -p /usr/share/anki
	cp -av * /usr/share/anki/
	cd /usr/share/anki && (\
	mv anki /usr/local/bin/;\
	mv anki.xpm anki.png /usr/share/pixmaps/;\
	mv anki.desktop /usr/share/applications;\
	mv anki.1 /usr/share/man/man1/)
	@echo
	@echo "Install complete."

uninstall:
	rm -rf /usr/share/anki
	rm -rf /usr/local/bin/anki
	rm -rf /usr/share/pixmaps/anki.{xpm,png}
	rm -rf /usr/share/applications/anki.desktop
	rm -rf /usr/share/man/man1/anki.1
	@echo
	@echo "Uninstall complete."
