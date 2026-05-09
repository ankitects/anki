// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use camino::Utf8Path;

/// On Unix, just a normal path. On Windows, c:\foo\bar.txt becomes
/// /c/foo/bar.txt, which msys rsync expects.
pub fn absolute_msys_path(path: &Utf8Path) -> String {
    let path = path.canonicalize_utf8().unwrap().into_string();
    if !cfg!(windows) {
        return path;
    }

    // strip off \\? verbatim prefix, which things like rsync/ninja choke on
    let drive = &path.chars().nth(4).unwrap();
    // and \ -> /
    format!("/{drive}/{}", path[7..].replace('\\', "/"))
}
