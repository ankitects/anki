// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use slog::{debug, error, Logger};
use slog::{slog_o, Drain};
use slog_async::OverflowStrategy;
use std::fs::OpenOptions;
use std::io;
use std::path::Path;

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

fn file(path: &Path) -> io::Result<Logger> {
    let file = OpenOptions::new().create(true).append(true).open(path)?;

    let decorator = slog_term::PlainSyncDecorator::new(file);
    let drain = slog_term::FullFormat::new(decorator).build().fuse();
    let drain = slog_envlogger::new(drain);
    let drain = slog_async::Async::new(drain)
        .chan_size(1_024)
        .overflow_strategy(OverflowStrategy::Block)
        .build()
        .fuse();
    Ok(Logger::root(drain, slog_o!()))
}

/// Get a logger, logging to a file if a path was provided, otherwise terminal.
pub(crate) fn default_logger(path: Option<&Path>) -> io::Result<Logger> {
    Ok(match path {
        Some(path) => file(path)?,
        None => terminal(),
    })
}
