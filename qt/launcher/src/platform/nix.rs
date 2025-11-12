// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::ffi::CStr;
use std::ffi::CString;
use std::os::unix::prelude::OsStrExt;

use anki_io::ToUtf8Path;
use anyhow::Result;

use crate::get_python_env_info;
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

macro_rules! ffi {
    ($lib:expr, $exec:expr, $($field:ident),* $(,)?) => {
        #[allow(clippy::missing_transmute_annotations)]
        $crate::platform::PyFfi {
            exec: $exec,
            $($field: {
                ::libc::dlerror();
                let name = ::std::ffi::CString::new(stringify!($field)).map_err(|_| ::anyhow::anyhow!("failed to construct sym"))?;
                let sym = ::libc::dlsym($lib, name.as_ptr());
                if sym.is_null() {
                    let dlerror_ptr = ::libc::dlerror();
                    let dlerror_str = if !dlerror_ptr.is_null() {
                        ::std::ffi::CStr::from_ptr(dlerror_ptr)
                            .to_str()
                            .unwrap_or_default()
                    } else {
                        ""
                    };
                    ::anyhow::bail!("failed to load {}: {dlerror_str}", stringify!($field));
                }
                ::std::mem::transmute(sym)
            },)*
            lib: $lib,
        }
    };
}

impl PyFfi {
    #[allow(non_snake_case)]
    pub fn load(path: impl AsRef<std::path::Path>, exec: CString) -> Result<Self> {
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

            Ok(ffi!(
                lib,
                exec,
                Py_IsInitialized,
                PyRun_SimpleString,
                Py_FinalizeEx,
                PyConfig_InitPythonConfig,
                PyConfig_SetBytesString,
                Py_InitializeFromConfig,
                PyConfig_SetBytesArgv,
                PyStatus_Exception
            ))
        }
    }
}

pub fn run(state: &State) -> Result<()> {
    let (version, lib_path, exec) = get_python_env_info(state)?;

    std::env::set_var("ANKI_LAUNCHER", std::env::current_exe()?.utf8()?.as_str());
    std::env::set_var("ANKI_LAUNCHER_UV", state.uv_path.utf8()?.as_str());
    std::env::set_var("UV_PROJECT", state.uv_install_root.utf8()?.as_str());
    std::env::remove_var("SSLKEYLOGFILE");

    PyFfi::load(lib_path, exec)?.run(&version, None)
}
