# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import subprocess
from collections.abc import Collection
from typing import cast

from briefcase.commands import (
    BuildCommand,
    CreateCommand,
    PackageCommand,
    UpdateCommand,
)
from briefcase.config import AppConfig
from briefcase.exceptions import BriefcaseCommandError
from briefcase.integrations.subprocess import NativeAppContext
from briefcase.platforms.linux import (
    LinuxMixin,
    LocalRequirementsMixin,
)


class LinuxZipAppConfig(AppConfig):
    """An AppConfig with Linux zip packaging attributes.

    Set during ``finalize_app_config()`` on ``LinuxZipMixin``.
    """

    python_version_tag: str


class LinuxZipMixin(LinuxMixin):
    output_format = "zip"
    supported_host_os: Collection[str] = {"Linux"}
    supports_external_packaging = True

    def project_path(self, app):
        return self.bundle_path(app) / f"{app.app_name}-{app.version}"

    def binary_path(self, app):
        return self.project_path(app) / app.app_name

    def distribution_filename(self, app: AppConfig) -> str:
        app = cast(LinuxZipAppConfig, app)
        return f"{app.bundle_name}_{app.version}-{getattr(app, 'revision', 1)}.tar.zst"

    def distribution_path(self, app: AppConfig):
        return self.dist_path / self.distribution_filename(app)

    def app_python_version_tag(self, app: AppConfig):
        # Use the version of Python that was used to run Briefcase.
        return self.python_version_tag

    def finalize_app_config(
        self,
        app: AppConfig,
        **kwargs,
    ):
        """Finalize app configuration.

        :param app: The app configuration to finalize.
        """
        self.console.verbose(
            "Finalizing application configuration...", prefix=app.app_name
        )
        app = cast(LinuxZipAppConfig, app)
        app.python_version_tag = self.app_python_version_tag(app)

        self.console.verbose(f"Targeting Python{app.python_version_tag}")


class LinuxZipMixin(LinuxZipMixin):
    supported_host_os: Collection[str] = {"Linux"}
    supported_host_os_reason = "Linux zip projects can only be built on Linux,"

    def verify_app_tools(self, app: AppConfig):
        """Verify App environment is prepared and available.

        :param app: The application being built
        """
        app = cast(LinuxZipAppConfig, app)
        NativeAppContext.verify(tools=self.tools, app=app)

        super().verify_app_tools(app)


class LinuxZipCreateCommand(LinuxZipMixin, LocalRequirementsMixin, CreateCommand):
    description = "Create and populate a Linux zip project."

    def output_format_template_context(self, app: AppConfig):
        app = cast(LinuxZipAppConfig, app)
        context = super().output_format_template_context(app)
        context["python_version"] = app.python_version_tag

        return context


class LinuxZipUpdateCommand(LinuxZipCreateCommand, UpdateCommand):
    description = "Update an existing Linux zip project."


class LinuxZipBuildCommand(LinuxZipMixin, BuildCommand):
    description = "Build a Linux zip project."

    def build_app(self, app: AppConfig, **kwargs):
        """Build an application.

        :param app: The application to build
        """
        self.console.info("Building application...", prefix=app.app_name)

        self.console.info("Build bootstrap binary...")
        with self.console.wait_bar("Building bootstrap binary..."):
            try:
                # Build the bootstrap binary.
                self.tools[app].app_context.run(
                    [
                        "make",
                        "-C",
                        "bootstrap",
                        "install",
                    ],
                    check=True,
                    cwd=self.bundle_path(app),
                )
            except subprocess.CalledProcessError as e:
                raise BriefcaseCommandError(
                    f"Error building bootstrap binary for {app.app_name}."
                ) from e

        with self.console.wait_bar("Installing license..."):
            if license_file := app.license.get("file"):
                license_file = self.base_path / license_file
                if license_file.is_file():
                    self.tools.shutil.copy(
                        license_file, self.project_path(app) / "LICENSE"
                    )
                else:
                    _relative_license_path = license_file.relative_to(self.base_path)
                    raise BriefcaseCommandError(f"""\
Your `pyproject.toml` specifies a license file of {str(_relative_license_path)!r}.
However, this file does not exist.

Ensure you have correctly spelled the filename in your `license.file` setting.

""")
            elif license_text := app.license.get("text"):
                (self.project_path(app) / "LICENSE").write_text(
                    license_text, encoding="utf-8"
                )
                if len(license_text.splitlines()) <= 1:
                    self.console.warning("""
Your app specifies a license using `license.text`, but the value doesn't appear to be a
full license. Briefcase will generate a `copyright` file for your project; you should
ensure that the contents of this file is adequate.
""")
            else:
                raise BriefcaseCommandError("""\
Your project does not contain a LICENSE definition.

Create a file named `LICENSE` in the same directory as your `pyproject.toml`
with your app's licensing terms, and set `license.file = 'LICENSE'` in your
app's configuration.
""")

        with self.console.wait_bar("Stripping binary..."):
            self.tools.subprocess.check_output(["strip", self.binary_path(app)])


class LinuxZipPackageCommand(LinuxZipMixin, PackageCommand):
    description = "Package a Linux zip project."

    def package_app(self, app: AppConfig, **kwargs):
        app = cast(LinuxZipAppConfig, app)
        self.console.info("Build zip package...")
        with self.console.wait_bar("Building zip package..."):
            try:
                self.tools[app].app_context.run(
                    [
                        "tar",
                        "-I",
                        "zstd -c --long -T0 -18",
                        "--transform",
                        f"s%^.%{self.project_path(app).name}-linux%S",
                        "-cf",
                        self.distribution_path(app),
                        "-C",
                        self.project_path(app),
                        ".",
                    ],
                    check=True,
                )
            except subprocess.CalledProcessError as e:
                raise BriefcaseCommandError(
                    f"Error building zip for {app.app_name}."
                ) from e


# Declare the briefcase command bindings
create = LinuxZipCreateCommand
update = LinuxZipUpdateCommand
build = LinuxZipBuildCommand
package = LinuxZipPackageCommand
