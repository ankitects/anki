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
            BackendMethod::AbortSync => true,
            BackendMethod::AbortMediaSync => true,
            BackendMethod::BeforeUpload => true,
            BackendMethod::TranslateString => false,
            BackendMethod::FormatTimespan => false,
            BackendMethod::RegisterTags => true,
            BackendMethod::AllTags => true,
            BackendMethod::GetConfigJson => true,
            BackendMethod::SetConfigJson => true,
            BackendMethod::RemoveConfig => true,
            BackendMethod::SetAllConfig => true,
            BackendMethod::GetAllConfig => true,
            BackendMethod::GetPreferences => true,
            BackendMethod::SetPreferences => true,
            BackendMethod::NoteIsDuplicateOrEmpty => true,
            BackendMethod::SyncLogin => true,
            BackendMethod::SyncCollection => true,
            BackendMethod::LatestProgress => false,
            BackendMethod::SetWantsAbort => false,
            BackendMethod::SyncStatus => true,
            BackendMethod::FullUpload => true,
            BackendMethod::FullDownload => true,
            BackendMethod::RemoveNotes => true,
            BackendMethod::RemoveCards => true,
            BackendMethod::UpdateStats => true,
            BackendMethod::ExtendLimits => true,
            BackendMethod::CountsForDeckToday => true,
            BackendMethod::CardStats => true,
            BackendMethod::Graphs => true,
            BackendMethod::I18nResources => false,
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
        .map_err(BackendError::py_err)
    }

    /// This takes and returns JSON, due to Python's slow protobuf
    /// encoding/decoding.
    fn db_command(&mut self, py: Python, input: &PyBytes) -> PyResult<PyObject> {
        let in_bytes = input.as_bytes();
        let out_res = py.allow_threads(move || {
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
