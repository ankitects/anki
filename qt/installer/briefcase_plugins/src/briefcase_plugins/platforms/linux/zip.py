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
from briefcase.config import DraftAppConfig, FinalizedAppConfig
from briefcase.exceptions import BriefcaseCommandError
from briefcase.integrations.subprocess import NativeAppContext
from briefcase.platforms.linux import (
    LinuxMixin,
    LocalRequirementsMixin,
)


class LinuxZipAppConfig(FinalizedAppConfig):
    """A FinalizedAppConfig with Linux zip packaging attributes.

    Set during ``finalize_app_config()`` on ``LinuxZipMixin``.
    """

    python_version_tag: str


class LinuxZipMixin(LinuxMixin):
    output_format = "zip"
    supported_host_os: Collection[str] = {"Linux"}
    supported_host_os_reason = "Linux zip projects can only be built on Linux,"
    supports_external_packaging = True

    def project_path(self, app):
        return self.bundle_path(app) / f"{app.app_name}-{app.version}"

    def binary_path(self, app):
        return self.project_path(app) / app.app_name

    def distribution_filename(self, app: FinalizedAppConfig) -> str:
        app = cast(LinuxZipAppConfig, app)
        return f"{app.bundle_name}_{app.version}-{getattr(app, 'revision', 1)}.tar.zst"

    def distribution_path(self, app: FinalizedAppConfig):
        return self.dist_path / self.distribution_filename(app)

    def app_python_version_tag(self, app: FinalizedAppConfig):
        # Use the version of Python that was used to run Briefcase.
        return self.python_version_tag

    def finalize_app_config(
        self,
        app: DraftAppConfig,
        **kwargs,
    ) -> LinuxZipAppConfig:
        """Finalize app configuration.

        :param app: The app configuration to finalize.
        """
        self.console.verbose(
            "Finalizing application configuration...", prefix=app.app_name
        )
        app = cast(LinuxZipAppConfig, app)
        app.python_version_tag = self.app_python_version_tag(app)

        self.console.verbose(f"Targeting Python{app.python_version_tag}")

        return LinuxZipAppConfig(super().finalize_app_config(app, **kwargs))

    def verify_app_tools(self, app: FinalizedAppConfig):
        """Verify App environment is prepared and available.

        :param app: The application being built
        """
        app = cast(LinuxZipAppConfig, app)
        NativeAppContext.verify(tools=self.tools, app=app)

        super().verify_app_tools(app)


class LinuxZipCreateCommand(LinuxZipMixin, LocalRequirementsMixin, CreateCommand):
    description = "Create and populate a Linux zip project."

    def output_format_template_context(self, app: FinalizedAppConfig):
        app = cast(LinuxZipAppConfig, app)
        context = super().output_format_template_context(app)
        context["python_version"] = app.python_version_tag

        return context


class LinuxZipUpdateCommand(LinuxZipCreateCommand, UpdateCommand):
    description = "Update an existing Linux zip project."


class LinuxZipBuildCommand(LinuxZipMixin, BuildCommand):
    description = "Build a Linux zip project."

    def build_app(self, app: FinalizedAppConfig, **kwargs):
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

        if app.license_files:
            with self.console.wait_bar("Installing license..."):
                separator = "-" * 75
                parts = []
                for license_path_str in app.license_files:
                    parts.append(
                        (self.base_path / license_path_str).read_text(encoding="utf-8")
                    )
                (self.project_path(app) / "LICENSE").write_text(
                    f"\n{separator}\n".join(parts), encoding="utf-8"
                )
        else:
            raise BriefcaseCommandError("""\
Your project does not include any license files.

Ensure your `pyproject.toml` is in PEP 639 format and specifies at least
one file in the `license-files` setting.
 """)
        with self.console.wait_bar("Stripping binary..."):
            self.tools.subprocess.check_output(["strip", self.binary_path(app)])


class LinuxZipPackageCommand(LinuxZipMixin, PackageCommand):
    description = "Package a Linux zip project."

    def package_app(self, app: FinalizedAppConfig, **kwargs):
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
