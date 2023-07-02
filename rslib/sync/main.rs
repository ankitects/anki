// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::env;

use anki::log::set_global_logger;
use anki::sync::http_server::SimpleServer;

fn main() {
    if env::var("RUST_LOG").is_err() {
        env::set_var("RUST_LOG", "anki=info")
    }
    set_global_logger(None).unwrap();
    println!("{}", SimpleServer::run());
}
