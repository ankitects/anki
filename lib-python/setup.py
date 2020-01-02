import setuptools, sys

with open("README.md", "r") as fh:
    long_description = fh.read()

platform_reqs = []
if sys.platform == "win32":
    platform_reqs.append("psutil")
if sys.platform != "win32" and sys.platform != "darwin":
    platform_reqs.append("distro")

setuptools.setup(
    name="anki",
    version="0.1.0",
    author="Ankitects Pty Ltd",
    description="Anki's library code",
    long_description=long_description,
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
    ] + platform_reqs
)
