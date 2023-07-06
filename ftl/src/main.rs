// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod garbage_collection;
mod serialize;
mod string;
mod sync;

use anyhow::Result;
use clap::Parser;
use clap::Subcommand;
use garbage_collection::deprecate_ftl_entries;
use garbage_collection::garbage_collect_ftl_entries;
use garbage_collection::write_ftl_json;
use garbage_collection::DeprecateEntriesArgs;
use garbage_collection::GarbageCollectArgs;
use garbage_collection::WriteJsonArgs;

use crate::string::string_operation;
use crate::string::StringCommand;

#[derive(Parser)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Update commit references to the latest translations,
    /// and copy source files to the translation repos. Requires access to the
    /// i18n repos to run.
    Sync,
    /// Extract references from all Rust, Python, TS, Svelte and Designer files
    /// in the given roots, convert them to ftl names case and write them as
    /// a json to the target file.
    WriteJson(WriteJsonArgs),
    /// Delete every entry in the ftl files that is not mentioned in another
    /// message or a given json.
    GarbageCollect(GarbageCollectArgs),
    /// Deprecate unused ftl entries by moving them to the bottom of the file
    /// and adding a deprecation warning. An entry is considered unused if
    /// cannot be found in a source or JSON file.
    Deprecate(DeprecateEntriesArgs),
    /// Operations on individual messages and their translations.
    #[clap(subcommand)]
    String(StringCommand),
}

fn main() -> Result<()> {
    match Cli::parse().command {
        Command::Sync => sync::sync(),
        Command::WriteJson(args) => write_ftl_json(args),
        Command::GarbageCollect(args) => garbage_collect_ftl_entries(args),
        Command::Deprecate(args) => deprecate_ftl_entries(args),
        Command::String(args) => string_operation(args),
    }
}
