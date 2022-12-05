// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki::{
    backend::{init_backend, Backend as RustBackend},
    log::default_logger,
};
use pyo3::{
    create_exception, exceptions::PyException, prelude::*, types::PyBytes, wrap_pyfunction,
};

#[pyclass(module = "_rsbridge")]
struct Backend {
    backend: RustBackend,
}

create_exception!(_rsbridge, BackendError, PyException);

#[pyfunction]
fn buildhash() -> &'static str {
    anki::version::buildhash()
}

#[pyfunction]
fn open_backend(init_msg: &PyBytes, log_file: Option<String>) -> PyResult<Backend> {
    let log = match default_logger(log_file.as_deref()) {
        Ok(log) => Some(log),
        Err(e) => return Err(PyException::new_err(e)),
    };
    match init_backend(init_msg.as_bytes(), log) {
        Ok(backend) => Ok(Backend { backend }),
        Err(e) => Err(PyException::new_err(e)),
    }
}

#[pymethods]
impl Backend {
    fn command(
        &self,
        py: Python,
        service: u32,
        method: u32,
        input: &PyBytes,
    ) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        py.allow_threads(|| self.backend.run_method(service, method, in_bytes))
            .map(|out_bytes| {
                let out_obj = PyBytes::new(py, &out_bytes);
                out_obj.into()
            })
            .map_err(BackendError::new_err)
    }

    /// This takes and returns JSON, due to Python's slow protobuf
    /// encoding/decoding.
    fn db_command(&self, py: Python, input: &PyBytes) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        let out_res = py.allow_threads(|| {
            self.backend
                .run_db_command_bytes(in_bytes)
                .map_err(BackendError::new_err)
        });
        let out_bytes = out_res?;
        let out_obj = PyBytes::new(py, &out_bytes);
        Ok(out_obj.into())
    }
}

// Module definition
//////////////////////////////////

#[pymodule]
fn _rsbridge(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Backend>()?;
    m.add_wrapped(wrap_pyfunction!(buildhash)).unwrap();
    m.add_wrapped(wrap_pyfunction!(open_backend)).unwrap();

    Ok(())
}
