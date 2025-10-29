// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
fn main() {
    #[cfg(windows)]
    {
        embed_resource::compile("win/anki-manifest.rc", embed_resource::NONE)
            .manifest_required()
            .unwrap();
    }
    println!("cargo:rerun-if-changed=../../out/buildhash");
    let buildhash = std::fs::read_to_string("../../out/buildhash").unwrap_or_default();
    println!("cargo:rustc-env=BUILDHASH={buildhash}");
}
