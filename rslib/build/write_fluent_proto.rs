// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

include!("mergeftl.rs");

fn main() {
    let args: Vec<_> = std::env::args().collect();
    write_fluent_proto(&args[1]);
}
