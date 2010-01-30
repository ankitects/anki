all: manual.html

manual.html: manual.txt Makefile
	a2x -v -f xhtml -d book manual.txt
	sed -i -e 's/margin: 1em 5% 1em 5%;/width: 65em; margin: 0 auto 0;/' docbook-xsl.css
