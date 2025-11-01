// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::ffi::CStr;
use std::ffi::CString;
use std::os::unix::prelude::OsStrExt;

use anki_io::ToUtf8Path;
use anyhow::anyhow;
use anyhow::Result;

use crate::get_libpython_path;
use crate::platform::PyFfi;
use crate::State;

impl Drop for PyFfi {
    fn drop(&mut self) {
        unsafe {
            (self.Py_FinalizeEx)();
            libc::dlclose(self.lib)
        };
    }
}

macro_rules! load_sym {
    ($lib:expr, $name:literal) => {{
        libc::dlerror();
        let sym = libc::dlsym($lib, $name.as_ptr());
        if sym.is_null() {
            let dlerror_str = CStr::from_ptr(libc::dlerror()).to_str()?;
            anyhow::bail!("failed to load {}: {dlerror_str}", $name.to_string_lossy());
        }
        std::mem::transmute(sym)
    }};
}

impl PyFfi {
    #[allow(non_snake_case)]
    pub fn load(path: impl AsRef<std::path::Path>) -> Result<Self> {
        unsafe {
            libc::dlerror();
            let lib = libc::dlopen(
                CString::new(path.as_ref().as_os_str().as_bytes())?.as_ptr(),
                libc::RTLD_LAZY | libc::RTLD_GLOBAL,
            );
            if lib.is_null() {
                let dlerror_str = CStr::from_ptr(libc::dlerror()).to_str()?;
                anyhow::bail!("failed to load library: {dlerror_str}");
            }

            #[allow(clippy::missing_transmute_annotations)] // they're not missing
            Ok(PyFfi {
                Py_InitializeEx: load_sym!(lib, c"Py_InitializeEx"),
                Py_IsInitialized: load_sym!(lib, c"Py_IsInitialized"),
                PyRun_SimpleString: load_sym!(lib, c"PyRun_SimpleString"),
                Py_FinalizeEx: load_sym!(lib, c"Py_FinalizeEx"),
                lib,
            })
        }
    }
}

pub fn run(state: &State) -> Result<()> {
    let lib_path = get_libpython_path(state)?;

    // NOTE: activate venv before loading lib
    let path = std::env::var("PATH")?;
    let paths = std::env::split_paths(&path);
    let path = std::env::join_paths(std::iter::once(state.venv_folder.join("bin")).chain(paths))?;
    std::env::set_var("PATH", path);
    std::env::set_var("VIRTUAL_ENV", &state.venv_folder);
    std::env::set_var("PYTHONHOME", "");

    std::env::set_var("ANKI_LAUNCHER", std::env::current_exe()?.utf8()?.as_str());
    std::env::set_var("ANKI_LAUNCHER_UV", state.uv_path.utf8()?.as_str());
    std::env::set_var("UV_PROJECT", state.uv_install_root.utf8()?.as_str());
    std::env::remove_var("SSLKEYLOGFILE");

    let ffi = PyFfi::load(lib_path)?;

    // NOTE: sys.argv would normally be set via PyConfig, but we don't have it here
    let args: String = std::env::args()
        .skip(1)
        .map(|s| format!(r#","{s}""#))
        .collect();

    // NOTE:
    // the venv activation script doesn't seem to be
    // necessary for linux, only PATH and VIRTUAL_ENV
    // but just call it anyway to have a standard setup
    let venv_activate_path = state.venv_folder.join("bin/activate_this.py");
    let venv_activate_path = venv_activate_path
        .as_os_str()
        .to_str()
        .ok_or_else(|| anyhow!("failed to get venv activation script path"))?;

    let preamble = std::ffi::CString::new(format!(
        r#"import sys, runpy; sys.argv = ['Anki'{args}]; runpy.run_path("{venv_activate_path}")"#,
    ))?;

    ffi.run(preamble)?;

    Ok(())
}
