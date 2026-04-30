# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from briefcase.platforms.linux.system import (
    LinuxSystemBuildCommand,
    LinuxSystemCreateCommand,
    LinuxSystemDevCommand,
    LinuxSystemOpenCommand,
    LinuxSystemPackageCommand,
    LinuxSystemPublishCommand,
    LinuxSystemRunCommand,
    LinuxSystemUpdateCommand,
)


class LinuxZipCreateCommand(LinuxSystemCreateCommand):
    output_format = "zip"


create = LinuxZipCreateCommand
update = LinuxSystemUpdateCommand
open = LinuxSystemOpenCommand
build = LinuxSystemBuildCommand
run = LinuxSystemRunCommand
package = LinuxSystemPackageCommand
publish = LinuxSystemPublishCommand
dev = LinuxSystemDevCommand
