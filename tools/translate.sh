#!/bin/bash
#
# build translations
#

if [ ! -d "aqt" ]
then
    echo "Please run this from the anki project directory"
    exit
fi

oldpwd=$(pwd)
cd aqt
version=$(grep -i appVersion= __init__.py)
version2=$(expr substr $version 13 $((${#version}-13)))

allPyFiles=aqt.files
echo "Generating translations for version $version2"
for i in *.py forms/*.py
do
    echo $i >> $allPyFiles
done

xgettext -s --no-wrap --package-name="aqt" --package-version=$version2 --files-from=$allPyFiles --output=locale/messages.pot
for file in locale/*.po
do
    echo -n $file
    msgmerge -s --no-wrap $file locale/messages.pot > $file.new && mv $file.new $file
    outdir=$(echo $file | \
        perl -pe 's%locale/aqt_(.*)\.po%locale/\1/LC_MESSAGES%')
    outfile="$outdir/aqt.mo"
    mkdir -p $outdir
    msgfmt $file --output-file=$outfile
done
rm $allPyFiles
cd $oldpwd
