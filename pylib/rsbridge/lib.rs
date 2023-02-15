// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki::backend::init_backend;
use anki::backend::Backend as RustBackend;
use anki::log::set_global_logger;
use anki::sync::http_server::SimpleServer;
use pyo3::create_exception;
use pyo3::exceptions::PyException;
use pyo3::prelude::*;
use pyo3::types::PyBytes;
use pyo3::wrap_pyfunction;

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
fn initialize_logging(path: Option<&str>) -> PyResult<()> {
    set_global_logger(path).map_err(|e| PyException::new_err(e.to_string()))
}

#[pyfunction]
fn syncserver() -> PyResult<()> {
    set_global_logger(None).unwrap();
    SimpleServer::run().map_err(|e| PyException::new_err(format!("{e:?}")))
}

#[pyfunction]
fn open_backend(init_msg: &PyBytes) -> PyResult<Backend> {
    match init_backend(init_msg.as_bytes()) {
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
    m.add_wrapped(wrap_pyfunction!(initialize_logging)).unwrap();
    m.add_wrapped(wrap_pyfunction!(syncserver)).unwrap();

    Ok(())
}
