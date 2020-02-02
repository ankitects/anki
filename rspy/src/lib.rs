use anki::backend::{init_backend, Backend as RustBackend};
use pyo3::prelude::*;
use pyo3::types::PyBytes;
use pyo3::{exceptions, wrap_pyfunction};

#[pyclass]
struct Backend {
    backend: RustBackend,
}

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
    fn command(&mut self, py: Python, input: &PyBytes) -> PyObject {
        let out_bytes = self.backend.run_command_bytes(input.as_bytes());
        let out_obj = PyBytes::new(py, &out_bytes);
        out_obj.into()
    }
}

#[pymodule]
fn ankirspy(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Backend>()?;
    m.add_wrapped(wrap_pyfunction!(buildhash)).unwrap();
    m.add_wrapped(wrap_pyfunction!(open_backend)).unwrap();

    Ok(())
}
