#!/bin/sh
#
# Shows the completion rate of the translation for each language.
#
wget -O tmp-translations-page.html https://crowdin.net/project/ankidroid --no-check-certificate
cat tmp-translations-page.html |
egrep 'project-language-name|[approved|translated]: \d+%'|
sed -e "s/<strong.*unselectable\">//g"|
sed -e "s/<\/strong>//g" |
sed -e "s/\w*<\/div>//g" |
sed -e "s/[[:space:]]*//g"|
tr "\n" " " |
tr '%' '\n' |
sed -e "s/^ //g" |
sed -e "s/\:/\: /g" |
grep -v "^\s+$" |
sed -e "s/$/%/g" |
grep -v " 0" > tmp-list.txt

echo "By country:"
cat tmp-list.txt |  sort

echo -e "\nBy rate approved (implies 100% translated):"
cat tmp-list.txt | grep approved | sed -e "s/\(.*\) \([0-9]*\)%/\2% \1/g" | sort -nr

echo -e "\nBy rate translated:"
cat tmp-list.txt | grep translated | sed -e "s/\(.*\) \([0-9]*\)%/\2% \1/g" | sort -nr

rm -f tmp-translations-page.html tmp-list.txt
