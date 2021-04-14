# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

import click
from piptools.scripts import compile


@click.group()
def cli():
    pass


cli.add_command(compile.cli, "compile")

print("Updating deps...")
os.chdir("pip")
sys.argv[1:] = ["compile", "--allow-unsafe", "--upgrade", "--no-header"]

cli()
