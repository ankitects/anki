all:
	@echo "You can run Anki with ./anki"
	@echo "If you wish to install it system wide, type 'sudo make install'"
	@echo "Uninstall with 'sudo make uninstall'"

install:
	@test ! -d .git || (echo "Install from git is not supported. Please use a tarball."; false)
	rm -rf ${DESTDIR}/usr/share/anki
	mkdir -p ${DESTDIR}/usr/share/anki
	cp -av * ${DESTDIR}/usr/share/anki/
	cd ${DESTDIR}/usr/share/anki && (\
	mv anki ${DESTDIR}/usr/local/bin/;\
	mv anki.xpm anki.png ${DESTDIR}/usr/share/pixmaps/;\
	mv anki.desktop ${DESTDIR}/usr/share/applications;\
	mv anki.1 ${DESTDIR}/usr/share/man/man1/)
	xdg-mime install anki.xml
	xdg-mime default anki.desktop application/x-anki
	xdg-mime default anki.desktop application/x-apkg
	@echo
	@echo "Install complete."

uninstall:
	rm -rf ${DESTDIR}/usr/share/anki
	rm -rf ${DESTDIR}/usr/local/bin/anki
	rm -rf ${DESTDIR}/usr/share/pixmaps/anki.{xpm,png}
	rm -rf ${DESTDIR}/usr/share/applications/anki.desktop
	rm -rf ${DESTDIR}/usr/share/man/man1/anki.1
	xdg-mime uninstall ${DESTDIR}/usr/share/mime/packages/anki.xml
	@echo
	@echo "Uninstall complete."
