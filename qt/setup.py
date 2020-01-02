import setuptools, sys, os

with open("README.md", "r") as fh:
    long_description = fh.read()

platform_reqs = []
if sys.platform == "win32":
    platform_reqs.append("psutil")

def package_files(directory):
    entries = []
    for (path, directories, filenames) in os.walk(directory):
        entries.append((path, [os.path.join(path, f) for f in filenames]))
    return entries

extra_files = package_files('aqt_data')

setuptools.setup(
    name="aqt",
    version="0.1.0",
    author="Ankitects Pty Ltd",
    description="Anki's Qt GUI code",
    long_description=long_description,
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
        'jsonschema'
    ] + platform_reqs
)
