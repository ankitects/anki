// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{fs, fs::OpenOptions, io};

pub use slog::{debug, error, Logger};
use slog::{slog_o, Drain};
use slog_async::OverflowStrategy;

const LOG_ROTATE_BYTES: u64 = 50 * 1024 * 1024;

pub(crate) fn terminal() -> Logger {
    let decorator = slog_term::TermDecorator::new().build();
    let drain = slog_term::FullFormat::new(decorator).build().fuse();
    let drain = slog_envlogger::new(drain);
    let drain = slog_async::Async::new(drain)
        .chan_size(1_024)
        .overflow_strategy(OverflowStrategy::Block)
        .build()
        .fuse();
    Logger::root(drain, slog_o!())
}

fn file(path: &str) -> io::Result<Logger> {
    maybe_rotate_log(path)?;
    let file = OpenOptions::new().create(true).append(true).open(path)?;

    let decorator = slog_term::PlainSyncDecorator::new(file);
    let drain = slog_term::FullFormat::new(decorator).build().fuse();
    let drain = slog_envlogger::new(drain);

    if std::env::var("LOGTERM").is_ok() {
        // log to the terminal as well
        let decorator = slog_term::TermDecorator::new().build();
        let term_drain = slog_term::FullFormat::new(decorator).build().fuse();
        let term_drain = slog_envlogger::new(term_drain);
        let joined_drain = slog::Duplicate::new(drain, term_drain).fuse();
        let drain = slog_async::Async::new(joined_drain)
            .chan_size(1_024)
            .overflow_strategy(OverflowStrategy::Block)
            .build()
            .fuse();
        Ok(Logger::root(drain, slog_o!()))
    } else {
        let drain = slog_async::Async::new(drain)
            .chan_size(1_024)
            .overflow_strategy(OverflowStrategy::Block)
            .build()
            .fuse();
        Ok(Logger::root(drain, slog_o!()))
    }
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
    if let Err(e) = fs::rename(&path2, &path3) {
        if e.kind() != io::ErrorKind::NotFound {
            return Err(e);
        }
    }

    // and rotate the primary log
    fs::rename(path, path2)
}

/// Get a logger, logging to a file if a path was provided, otherwise terminal.
pub(crate) fn default_logger(path: Option<&str>) -> io::Result<Logger> {
    Ok(match path {
        Some(path) => file(path)?,
        None => terminal(),
    })
}
