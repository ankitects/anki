// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use ninja_gen::archives::Platform;

/// Usually None to use the host architecture; can be overriden by setting
/// MAC_X86 to build for x86_64 on Apple Silicon
pub fn overriden_rust_target_triple() -> Option<&'static str> {
    overriden_python_target_platform().map(|p| p.as_rust_triple())
}

/// Usually None to use the host architecture; can be overriden by setting
/// MAC_X86 to build for x86_64 on Apple Silicon
pub fn overriden_python_target_platform() -> Option<Platform> {
    if env::var("MAC_X86").is_ok() {
        Some(Platform::MacX64)
    } else {
        None
    }
}
