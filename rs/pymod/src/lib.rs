use ankirs::backend::Backend as RustBackend;
use pyo3::prelude::*;
use pyo3::types::PyBytes;

#[pyclass]
struct Backend {
    backend: RustBackend,
}

#[pymethods]
impl Backend {
    #[new]
    fn init(obj: &PyRawObject) {
        obj.init({
            Backend {
                backend: Default::default(),
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
fn _ankirs(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Backend>()?;

    Ok(())
}
