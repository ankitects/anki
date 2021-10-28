// Based off PyOxidizer's 'init-rust-project'.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

#![windows_subsystem = "windows"]

mod anki;

use pyembed::{MainPythonInterpreter, OxidizedPythonInterpreterConfig};

#[cfg(feature = "global-allocator-jemalloc")]
#[global_allocator]
static GLOBAL: jemallocator::Jemalloc = jemallocator::Jemalloc;

include!(env!("DEFAULT_PYTHON_CONFIG_RS"));

fn main() {
    anki::init();

    let exit_code = {
        let config: OxidizedPythonInterpreterConfig = default_python_config();
        match MainPythonInterpreter::new(config) {
            Ok(interp) => interp.run(),
            Err(msg) => {
                eprintln!("error instantiating embedded Python interpreter: {}", msg);
                1
            }
        }
    };
    std::process::exit(exit_code);
}
