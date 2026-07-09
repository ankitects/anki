// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

fn main() {
    #[cfg(windows)]
    {
        // Prevent Windows UAC "Installer Detection" from requiring elevation.
        // Windows auto-elevates executables whose names match installer heuristics
        // (install/update/setup/patch). An asInvoker manifest overrides this.
        embed_resource::compile("win/update-tools.rc", embed_resource::NONE)
            .manifest_required()
            .unwrap();
    }
}
