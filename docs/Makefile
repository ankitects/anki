all: manual.html

manual.html: manual.txt Makefile
	a2x -v -f xhtml -d book --stylesheet manual.css manual.txt
