// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(super) fn init() {
    #[cfg(target_os = "windows")]
    attach_console();

    println!("Anki starting...");
}

/// If parent process has a console (eg cmd.exe), redirect our output there.
#[cfg(target_os = "windows")]
fn attach_console() {
    use std::ffi::CString;

    use libc_stdhandle::*;
    use winapi::um::wincon;

    let console_attached = unsafe { wincon::AttachConsole(wincon::ATTACH_PARENT_PROCESS) };
    if console_attached == 0 {
        return;
    }

    let conin = CString::new("CONIN$").unwrap();
    let conout = CString::new("CONOUT$").unwrap();
    let r = CString::new("r").unwrap();
    let w = CString::new("w").unwrap();

    // Python uses the CRT for I/O, and it requires the descriptors are reopened.
    unsafe {
        libc::freopen(conin.as_ptr(), r.as_ptr(), stdin());
        libc::freopen(conout.as_ptr(), w.as_ptr(), stdout());
        libc::freopen(conout.as_ptr(), w.as_ptr(), stderr());
    }
}
