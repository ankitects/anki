use anki::backend::Backend as RustBackend;
use pyo3::prelude::*;
use pyo3::types::PyBytes;
use pyo3::wrap_pyfunction;

#[pyclass]
struct Backend {
    backend: RustBackend,
}

#[pyfunction]
fn buildhash() -> &'static str {
    include_str!("../../meta/buildhash").trim()
}

#[pymethods]
impl Backend {
    #[new]
    fn init(obj: &PyRawObject, col_path: String, media_folder: String) {
        obj.init({
            Backend {
                backend: RustBackend::new(col_path, media_folder),
            }
        });
    }

    fn command(&mut self, py: Python, input: &PyBytes) -> PyResult<PyObject> {
        let out_bytes = self.backend.run_command_bytes(input.as_bytes());
        let out_obj = PyBytes::new(py, &out_bytes);
        Ok(out_obj.into())
    }
}

#[pymodule]
fn ankirspy(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Backend>()?;
    m.add_wrapped(wrap_pyfunction!(buildhash)).unwrap();

    Ok(())
}
