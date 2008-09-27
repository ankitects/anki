#!/bin/bash
#
# update translation files
#

if [ ! -d "locale" ]
then
    echo "Please run this from the anki module directory"
    exit
fi

allPyFiles=libanki.files
echo "Generating translations.."
for i in *.py features/*.py
do
    echo $i >> $allPyFiles
done

xgettext -s --no-wrap --files-from=$allPyFiles --output=locale/messages.pot
for file in locale/*.po
do
    echo -n $file
    msgmerge -s --no-wrap $file locale/messages.pot > $file.new && mv $file.new $file
    outdir=$(echo $file | \
        perl -pe 's%locale/libanki_(.*)\.po%locale/\1/LC_MESSAGES%')
    outfile="$outdir/libanki.mo"
    mkdir -p $outdir
    msgfmt $file --output-file=$outfile
done
rm $allPyFiles
