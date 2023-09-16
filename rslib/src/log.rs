// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::fs::OpenOptions;
use std::io;

use once_cell::sync::OnceCell;
use tracing::subscriber::set_global_default;
use tracing_appender::non_blocking::NonBlocking;
use tracing_appender::non_blocking::WorkerGuard;
use tracing_subscriber::fmt;
use tracing_subscriber::fmt::Layer;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::EnvFilter;

use crate::prelude::*;

const LOG_ROTATE_BYTES: u64 = 50 * 1024 * 1024;

/// Enable logging to the console, and optionally also to a file.
pub fn set_global_logger(path: Option<&str>) -> Result<()> {
    if std::env::var("BURN_LOG").is_ok() {
        return Ok(());
    }
    static ONCE: OnceCell<()> = OnceCell::new();
    ONCE.get_or_try_init(|| -> Result<()> {
        let file_writer = if let Some(path) = path {
            Some(Layer::new().with_writer(get_appender(path)?))
        } else {
            None
        };
        let subscriber = tracing_subscriber::registry()
            .with(fmt::layer().with_target(false))
            .with(file_writer)
            .with(EnvFilter::from_default_env());
        set_global_default(subscriber).or_invalid("global subscriber already set")?;
        Ok(())
    })?;
    Ok(())
}

/// Holding on to this guard does not actually ensure the log file will be fully
/// written, as statics do not implement Drop.
static APPENDER_GUARD: OnceCell<WorkerGuard> = OnceCell::new();

fn get_appender(path: &str) -> Result<NonBlocking> {
    maybe_rotate_log(path)?;
    let file = OpenOptions::new().create(true).append(true).open(path)?;
    let (appender, guard) = tracing_appender::non_blocking(file);
    if APPENDER_GUARD.set(guard).is_err() {
        invalid_input!("log file should be set only once");
    }
    Ok(appender)
}

fn maybe_rotate_log(path: &str) -> io::Result<()> {
    let current_bytes = match fs::metadata(path) {
        Ok(meta) => meta.len(),
        Err(e) => {
            if e.kind() == io::ErrorKind::NotFound {
                0
            } else {
                return Err(e);
            }
        }
    };
    if current_bytes < LOG_ROTATE_BYTES {
        return Ok(());
    }

    let path2 = format!("{}.1", path);
    let path3 = format!("{}.2", path);

    // if a rotated file already exists, rename it
    if let Err(e) = fs::rename(&path2, path3) {
        if e.kind() != io::ErrorKind::NotFound {
            return Err(e);
        }
    }

    // and rotate the primary log
    fs::rename(path, path2)
}
