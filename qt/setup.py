import setuptools, sys, os

with open("../meta/version") as fh:
    version = fh.read().strip()

def package_files(directory):
    entries = []
    for (path, directories, filenames) in os.walk(directory):
        entries.append((path, [os.path.join(path, f) for f in filenames]))
    return entries

extra_files = package_files('aqt_data')

setuptools.setup(
    name="aqt",
    version=version,
    author="Ankitects Pty Ltd",
    description="Anki's Qt GUI code",
    long_description="Anki's QT GUI code",
    long_description_content_type="text/markdown",
    url="https://apps.ankiweb.net",
    packages=setuptools.find_packages(".", exclude=["tests"]),
    data_files=extra_files,
    classifiers=[
    ],
    python_requires='>=3.6',
    install_requires=[
        'beautifulsoup4',
        'requests',
        'send2trash',
        'pyaudio',
        'markdown',
        'jsonschema',
        'psutil; sys.platform == "win32"',
    ]
)
