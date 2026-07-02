#!/bin/bash
# Shows human-readable (not XML) changelog for English and for languages where it is not identical to English.

echo "English:"
grep "<item>" AnkiDroid/src/main/res/values/13-newfeatures.xml | sed -e "s/.*<item>/• /" -e "s/<.*//" -e "s/\\\\//"

LANGS=`ls AnkiDroid/src/main/res | grep "values-" | sed -e "s/values-//" | grep -v "v11"`

for LANG in $LANGS
do
	DIFFERENT=`diff -b AnkiDroid/src/main/res/values-$LANG/13-newfeatures.xml AnkiDroid/src/main/res/values/13-newfeatures.xml | grep "<item>" | wc -l`
	if [ $DIFFERENT -ne "0" ]
	then
		echo "$LANG:"
		grep "<item>" AnkiDroid/src/main/res/values-$LANG/13-newfeatures.xml | sed -e "s/.*<item>/• /" -e "s/<.*//" -e "s/\\\\//"
	else
		echo "($LANG identical to English)"
	fi
done




