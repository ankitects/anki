// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::launcher::uninstall_response::ActionNeeded;
use anyhow::Result;

pub fn finalize_uninstall() -> Result<Option<ActionNeeded>> {
    let uninstall_script = std::path::Path::new("/usr/local/share/anki/uninstall.sh");

    Ok(uninstall_script
        .exists()
        .then_some(ActionNeeded::UnixScript(
            uninstall_script.display().to_string(),
        )))
}

pub fn ensure_glibc_supported() -> Result<()> {
    use std::ffi::CStr;
    let get_glibc_version = || -> Option<(u32, u32)> {
        let version_ptr = unsafe { libc::gnu_get_libc_version() };
        if version_ptr.is_null() {
            return None;
        }

        let version_cstr = unsafe { CStr::from_ptr(version_ptr) };
        let version_str = version_cstr.to_str().ok()?;

        // Parse version string (format: "2.36" or "2.36.1")
        let version_parts: Vec<&str> = version_str.split('.').collect();
        if version_parts.len() < 2 {
            return None;
        }

        let major: u32 = version_parts[0].parse().ok()?;
        let minor: u32 = version_parts[1].parse().ok()?;

        Some((major, minor))
    };

    let (major, minor) = get_glibc_version().unwrap_or_default();
    if major < 2 || (major == 2 && minor < 36) {
        anyhow::bail!("Anki requires a modern Linux distro with glibc 2.36 or later.");
    }

    Ok(())
}
