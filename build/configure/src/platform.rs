// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use ninja_gen::archives::Platform;

/// Please see [`overriden_python_target_platform()`] for details.
pub fn overriden_rust_target_triple() -> Option<&'static str> {
    overriden_python_wheel_platform().map(|p| p.as_rust_triple())
}

/// Usually None to use the host architecture, except on Windows which
/// always uses x86_64, since WebEngine is unavailable for ARM64.
pub fn overriden_python_venv_platform() -> Option<Platform> {
    if cfg!(target_os = "windows") {
        Some(Platform::WindowsX64)
    } else {
        None
    }
}

/// Like [`overriden_python_venv_platform`], but:
/// If MAC_X86 is set, an X86 wheel will be built on macOS ARM.
/// If LIN_ARM64 is set, an ARM64 wheel will be built on Linux AMD64.
pub fn overriden_python_wheel_platform() -> Option<Platform> {
    if env::var("MAC_X86").is_ok() {
        Some(Platform::MacX64)
    } else if env::var("LIN_ARM64").is_ok() {
        Some(Platform::LinuxArm)
    } else {
        overriden_python_venv_platform()
    }
}
