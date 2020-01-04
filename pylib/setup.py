import setuptools, sys

with open("../meta/version") as fh:
    version = fh.read().strip()

setuptools.setup(
    name="anki",
    version=version,
    author="Ankitects Pty Ltd",
    description="Anki's library code",
    long_description="Anki's library code",
    long_description_content_type="text/markdown",
    url="https://apps.ankiweb.net",
    packages=setuptools.find_packages(".", exclude=["tests"]),
    classifiers=[
    ],
    python_requires='>=3.6',
    install_requires=[
        'beautifulsoup4',
        'requests',
        'decorator',
        'protobuf',
        'psutil; sys_platform == "win32"',
        'distro; sys_platform != "darwin" and sys_platform != "win32"'
    ]
)
