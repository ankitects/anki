// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki::backend::{init_backend, Backend as RustBackend};
use pyo3::exceptions::Exception;
use pyo3::prelude::*;
use pyo3::types::PyBytes;
use pyo3::{create_exception, exceptions, wrap_pyfunction};

// Regular backend
//////////////////////////////////

#[pyclass]
struct Backend {
    backend: RustBackend,
}

create_exception!(ankirspy, DBError, Exception);

#[pyfunction]
fn buildhash() -> &'static str {
    include_str!("../../meta/buildhash").trim()
}

#[pyfunction]
fn open_backend(init_msg: &PyBytes) -> PyResult<Backend> {
    match init_backend(init_msg.as_bytes()) {
        Ok(backend) => Ok(Backend { backend }),
        Err(e) => Err(exceptions::Exception::py_err(e)),
    }
}

#[pymethods]
impl Backend {
    fn command(&mut self, py: Python, input: &PyBytes, release_gil: bool) -> PyObject {
        let in_bytes = input.as_bytes();
        let out_bytes = if release_gil {
            py.allow_threads(move || self.backend.run_command_bytes(in_bytes))
        } else {
            self.backend.run_command_bytes(in_bytes)
        };
        let out_obj = PyBytes::new(py, &out_bytes);
        out_obj.into()
    }

    fn set_progress_callback(&mut self, callback: PyObject) {
        if callback.is_none() {
            self.backend.set_progress_callback(None);
        } else {
            let func = move |bytes: Vec<u8>| {
                let gil = Python::acquire_gil();
                let py = gil.python();
                let out_bytes = PyBytes::new(py, &bytes);
                let out_obj: PyObject = out_bytes.into();
                let res: PyObject = match callback.call1(py, (out_obj,)) {
                    Ok(res) => res,
                    Err(e) => {
                        println!("error calling callback:");
                        e.print(py);
                        return false;
                    }
                };
                match res.extract(py) {
                    Ok(cont) => cont,
                    Err(e) => {
                        println!("callback did not return bool: {:?}", e);
                        false
                    }
                }
            };
            self.backend.set_progress_callback(Some(Box::new(func)));
        }
    }

    fn db_command(&mut self, py: Python, input: &PyBytes) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        let out_res = py.allow_threads(move || {
            self.backend
                .db_command(in_bytes)
                .map_err(|e| DBError::py_err(e.localized_description(&self.backend.i18n())))
        });
        let out_string = out_res?;
        let out_obj = PyBytes::new(py, out_string.as_bytes());
        Ok(out_obj.into())
    }
}

// Module definition
//////////////////////////////////

#[pymodule]
fn ankirspy(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Backend>()?;
    m.add_wrapped(wrap_pyfunction!(buildhash)).unwrap();
    m.add_wrapped(wrap_pyfunction!(open_backend)).unwrap();

    Ok(())
}
