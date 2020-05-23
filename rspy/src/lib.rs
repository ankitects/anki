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

create_exception!(ankirspy, DBError, Exception);
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
            BackendMethod::ExtractAVTags => false,
            BackendMethod::ExtractLatex => false,
            BackendMethod::GetEmptyCards => true,
            BackendMethod::RenderExistingCard => false,
            BackendMethod::RenderUncommittedCard => false,
            BackendMethod::StripAVTags => false,
            BackendMethod::SearchCards => true,
            BackendMethod::SearchNotes => true,
            BackendMethod::LocalMinutesWest => false,
            BackendMethod::SchedTimingToday => false,
            BackendMethod::CheckMedia => true,
            BackendMethod::SyncMedia => true,
            BackendMethod::TrashMediaFiles => true,
            BackendMethod::AddOrUpdateDeckLegacy => false,
            BackendMethod::DeckTree => true,
            BackendMethod::DeckTreeLegacy => true,
            BackendMethod::GetAllDecksLegacy => true,
            BackendMethod::GetDeckIDByName => true,
            BackendMethod::GetDeckLegacy => true,
            BackendMethod::GetDeckNames => true,
            BackendMethod::NewDeckLegacy => false,
            BackendMethod::RemoveDeck => true,
            BackendMethod::AddOrUpdateDeckConfigLegacy => true,
            BackendMethod::AllDeckConfigLegacy => true,
            BackendMethod::GetDeckConfigLegacy => true,
            BackendMethod::NewDeckConfigLegacy => false,
            BackendMethod::RemoveDeckConfig => true,
            BackendMethod::GetCard => true,
            BackendMethod::UpdateCard => true,
            BackendMethod::AddCard => true,
            BackendMethod::NewNote => true,
            BackendMethod::AddNote => true,
            BackendMethod::UpdateNote => true,
            BackendMethod::GetNote => true,
            BackendMethod::AddNoteTags => true,
            BackendMethod::UpdateNoteTags => true,
            BackendMethod::ClozeNumbersInNote => true,
            BackendMethod::AfterNoteUpdates => true,
            BackendMethod::FieldNamesForNotes => true,
            BackendMethod::AddOrUpdateNotetype => true,
            BackendMethod::GetStockNotetypeLegacy => false,
            BackendMethod::GetNotetypeLegacy => true,
            BackendMethod::GetNotetypeNames => true,
            BackendMethod::GetNotetypeNamesAndCounts => true,
            BackendMethod::GetNotetypeIDByName => true,
            BackendMethod::RemoveNotetype => true,
            BackendMethod::CheckDatabase => true,
            BackendMethod::FindAndReplace => true,
            BackendMethod::SetLocalMinutesWest => false,
            BackendMethod::StudiedToday => false,
            BackendMethod::CongratsLearnMessage => false,
            BackendMethod::AddMediaFile => true,
            BackendMethod::EmptyTrash => true,
            BackendMethod::RestoreTrash => true,
            BackendMethod::OpenCollection => true,
            BackendMethod::CloseCollection => true,
            BackendMethod::AbortMediaSync => true,
            BackendMethod::BeforeUpload => true,
            BackendMethod::TranslateString => false,
            BackendMethod::FormatTimespan => false,
            BackendMethod::RegisterTags => true,
            BackendMethod::AllTags => true,
            BackendMethod::GetChangedTags => true,
            BackendMethod::GetConfigJson => true,
            BackendMethod::SetConfigJson => true,
            BackendMethod::RemoveConfig => true,
            BackendMethod::SetAllConfig => true,
            BackendMethod::GetAllConfig => true,
            BackendMethod::GetPreferences => true,
            BackendMethod::SetPreferences => true,
        }
    } else {
        false
    }
}

#[pymethods]
impl Backend {
    fn command(&mut self, py: Python, method: u32, input: &PyBytes) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        if want_release_gil(method) {
            py.allow_threads(move || self.backend.run_command_bytes(method, in_bytes))
        } else {
            self.backend.run_command_bytes(method, in_bytes)
        }
        .map(|out_bytes| {
            let out_obj = PyBytes::new(py, &out_bytes);
            out_obj.into()
        })
        .map_err(|err_bytes| BackendError::py_err(err_bytes))
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
