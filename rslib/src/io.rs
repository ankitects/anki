use std::path::Path;

use tempfile::NamedTempFile;

use crate::prelude::*;

pub(crate) fn atomic_rename(file: NamedTempFile, target: &Path) -> Result<()> {
    file.as_file().sync_all()?;
    file.persist(&target)
        .map_err(|err| AnkiError::IoError(format!("write {target:?} failed: {err}")))?;
    if !cfg!(windows) {
        if let Some(parent) = target.parent() {
            std::fs::File::open(parent)
                .and_then(|file| file.sync_all())
                .map_err(|err| AnkiError::IoError(format!("sync {parent:?} failed: {err}")))?;
        }
    }
    Ok(())
}
