import sys
import os
import click
from piptools.scripts import compile


@click.group()
def cli():
    pass


cli.add_command(compile.cli, "compile")

print("Updating deps...")
os.chdir("pip")
sys.argv[1:] = ["compile", "--allow-unsafe", "--upgrade", "--no-header"]


def after_run():
    with open("requirements.txt", "a+") as file:
        file.write(
            """
# manually added for now; ensure it and the earlier winrt are not removed on update
winrt==1.0.20330.1; sys.platform == "win32" and platform_release == "10" and python_version >= "3.9"
"""
        )


import atexit

atexit.register(after_run)

cli()
