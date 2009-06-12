#!/bin/bash
#
# build translations
#

if [ ! -d "ankiqt" ]
then
    echo "Please run this from the anki project directory"
    exit
fi

oldpwd=$(pwd)
cd ankiqt
version=$(grep -i appVersion= __init__.py)
version2=$(expr substr $version 13 $((${#version}-13)))

allPyFiles=ankiqt.files
echo "Generating translations for version $version2"
for i in *.py ui/*.py forms/*.py
do
    echo $i >> $allPyFiles
done

xgettext -s --no-wrap --package-name="ankiqt" --package-version=$version2 --files-from=$allPyFiles --output=locale/messages.pot
for file in locale/*.po
do
    echo -n $file
    msgmerge -s --no-wrap $file locale/messages.pot > $file.new && mv $file.new $file
    outdir=$(echo $file | \
        perl -pe 's%locale/ankiqt_(.*)\.po%locale/\1/LC_MESSAGES%')
    outfile="$outdir/ankiqt.mo"
    mkdir -p $outdir
    msgfmt $file --output-file=$outfile
done
rm $allPyFiles
cd $oldpwd
