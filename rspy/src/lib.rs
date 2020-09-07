// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki::backend::{init_backend, Backend as RustBackend, BackendMethod};
use pyo3::exceptions::Exception;
use pyo3::prelude::*;
use pyo3::types::PyBytes;
use pyo3::{create_exception, exceptions, wrap_pyfunction};
use std::convert::TryFrom;

// Regular backend
//////////////////////////////////

#[pyclass]
struct Backend {
    backend: RustBackend,
}

create_exception!(ankirspy, BackendError, Exception);

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

fn want_release_gil(method: u32) -> bool {
    if let Ok(method) = BackendMethod::try_from(method) {
        match method {
            BackendMethod::ExtractAVTags
            | BackendMethod::ExtractLatex
            | BackendMethod::RenderExistingCard
            | BackendMethod::RenderUncommittedCard
            | BackendMethod::StripAVTags
            | BackendMethod::LocalMinutesWest
            | BackendMethod::SchedTimingToday
            | BackendMethod::AddOrUpdateDeckLegacy
            | BackendMethod::NewDeckLegacy
            | BackendMethod::NewDeckConfigLegacy
            | BackendMethod::GetStockNotetypeLegacy
            | BackendMethod::SetLocalMinutesWest
            | BackendMethod::StudiedToday
            | BackendMethod::TranslateString
            | BackendMethod::FormatTimespan
            | BackendMethod::LatestProgress
            | BackendMethod::SetWantsAbort
            | BackendMethod::I18nResources => false,
            _ => true,
        }
    } else {
        false
    }
}

#[pymethods]
impl Backend {
    fn command(&self, py: Python, method: u32, input: &PyBytes) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        if want_release_gil(method) {
            py.allow_threads(|| self.backend.run_command_bytes(method, in_bytes))
        } else {
            self.backend.run_command_bytes(method, in_bytes)
        }
        .map(|out_bytes| {
            let out_obj = PyBytes::new(py, &out_bytes);
            out_obj.into()
        })
        .map_err(BackendError::py_err)
    }

    /// This takes and returns JSON, due to Python's slow protobuf
    /// encoding/decoding.
    fn db_command(&self, py: Python, input: &PyBytes) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        let out_res = py.allow_threads(|| {
            self.backend
                .run_db_command_bytes(in_bytes)
                .map_err(BackendError::py_err)
        });
        let out_bytes = out_res?;
        let out_obj = PyBytes::new(py, &out_bytes);
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
