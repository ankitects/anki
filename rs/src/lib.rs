use pyo3::prelude::*;
use pyo3::exceptions;

#[pyclass]
struct Bridge {
}

#[pymethods]
impl Bridge {

    #[new]
    fn new(obj: &PyRawObject) {
        obj.init({
            Bridge { }
        });
    }

    fn cmd(&mut self, request: String) -> PyResult<String> {
        Ok("test".to_string())
            .map_err(|e: std::io::Error| {
                exceptions::Exception::py_err(format!("{:?}", e))
            })
    }
}

#[pymodule]
fn _ankirs(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Bridge>()?;

    Ok(())
}
