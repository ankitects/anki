# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

set -e

# base folder with Anki.app in it
output="$1"
dist="$1/tmp"
dmg_path="$output/Anki.dmg"

if [ -d "/Volumes/Anki" ]
then
    echo "You already have one Anki mounted, unmount it first!"
    exit 1
fi

rm -rf $dist $dmg_path
mkdir -p $dist
rsync -av $output/Anki.app $dist/
script_folder=$(dirname $0)

echo "bundling..."
ln -s /Applications $dist/Applications
mkdir -p $dist/.background
cp ${script_folder}/anki-logo-bg.png $dist/.background
cp ${script_folder}/dmg_ds_store $dist/.DS_Store

# create a writable dmg first, and modify its layout with AppleScript
hdiutil create -attach -ov -format UDRW -fs HFS+ -volname Anki -srcfolder $dist -o /tmp/Anki-rw.dmg
# announce before making the window appear 
say "applescript"
open /tmp/Anki-rw.dmg
sleep 2
open ${script_folder}/set-dmg-settings.app
sleep 2
hdiutil detach "/Volumes/Anki" || (sleep 3; hdiutil detach /Volumes/Anki)
sleep 1
if [ -d "/Volumes/Anki" ]
then
    echo "drive did not detach"
    exit 1
fi

# convert it to a read-only image
rm -rf $dmg_path
hdiutil convert /tmp/Anki-rw.dmg -ov -format ULFO -o $dmg_path
rm -rf /tmp/Anki-rw.dmg
