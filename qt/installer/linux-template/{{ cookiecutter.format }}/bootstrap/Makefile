PBS_PREFIX=../{{ cookiecutter.app_name }}-{{ cookiecutter.version }}/python
CFLAGS=$(shell $(PBS_PREFIX)/bin/python{{ cookiecutter.python_version|py_tag }}-config --cflags --embed) -DAPP_NAME=\"{{ cookiecutter.app_name }}\"
LDFLAGS=$(shell $(PBS_PREFIX)/bin/python{{ cookiecutter.python_version|py_tag }}-config --ldflags --embed) -Wl,-rpath,\$$ORIGIN/python/lib

all: {{ cookiecutter.app_name }}

pyversion.h:
	$(PBS_PREFIX)/bin/python{{ cookiecutter.python_version|py_tag }} -c "import sys; print(f'#define PY_TAG \"{sys.version_info.major}.{sys.version_info.minor}\"')" > pyversion.h

{{ cookiecutter.app_name }}: main.c pyversion.h
	gcc -o {{ cookiecutter.app_name }} $^ $(CFLAGS) $(LDFLAGS) -fPIE

clean:
	rm -f {{ cookiecutter.app_name }} pyversion.h

install: all
	cp {{ cookiecutter.app_name }} ../{{ cookiecutter.app_name }}-{{ cookiecutter.version }}/

.PHONY: all clean install
