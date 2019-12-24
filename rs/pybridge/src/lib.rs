use ankirs::bridge::Bridge as RustBridge;
use pyo3::prelude::*;
use pyo3::types::PyBytes;

#[pyclass]
struct Bridge {
    bridge: RustBridge,
}

#[pymethods]
impl Bridge {
    #[new]
    fn init(obj: &PyRawObject) {
        obj.init({
            Bridge {
                bridge: Default::default(),
            }
        });
    }

    fn command(&mut self, py: Python, input: &PyBytes) -> PyResult<PyObject> {
        let out_bytes = self.bridge.run_command_bytes(input.as_bytes());
        let out_obj = PyBytes::new(py, &out_bytes);
        Ok(out_obj.into())
    }
}

#[pymodule]
fn _ankirs(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Bridge>()?;

    Ok(())
}
