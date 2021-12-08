#!/bin/bash
#
# This script copies a system-installed PyQt. Written for Debian Bullseye;
# will need adjusting for other platforms.

set -e

sudo apt install -y python3-pyqt5.{qtwebengine,qtmultimedia} patchelf

pyqtBase=/usr/lib/python3/dist-packages/PyQt5
qtData=/usr/share/qt5
qtLibBase=/usr/lib/aarch64-linux-gnu
qtLibExtra=$qtLibBase/qt5

outDir=~/PyQtPrepared/PyQt5/
qtOutputBase=$outDir/Qt5
rm -rf $outDir
mkdir -p $qtOutputBase

# pyqt
rsync -ai --exclude-from=qt.exclude --exclude Qt5 \
    $pyqtBase/ $outDir/
patchelf --set-rpath '$ORIGIN/Qt5/lib' $outDir/*.so

# qt libs
rsync -ai $qtLibBase/libQt5* $qtOutputBase/lib/
patchelf --set-rpath '$ORIGIN' $qtOutputBase/lib/*.so.*

# qt libexec/plugins
rsync -ai --exclude=qml $qtLibExtra/ $qtOutputBase/
patchelf --set-rpath '$ORIGIN/../../lib' $qtOutputBase/plugins/*/*.so
patchelf --set-rpath '$ORIGIN/../lib' $qtOutputBase/libexec/QtWebEngineProcess
cat > $qtOutputBase/libexec/qt.conf <<EOF
[Paths]
Prefix = ..
EOF

# qt translations/resources
rsync -ai $qtData/ $qtOutputBase/
