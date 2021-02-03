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

cli()
