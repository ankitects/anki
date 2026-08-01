// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anyhow::Result;
use ninja_gen::cog::cog_render;
use ninja_gen::inputs;
use ninja_gen::Build;

pub fn check_cog(build: &mut Build) -> Result<()> {
    cog_render(
        build,
        "docs",
        inputs!["docs-site/addons/hooks-reference.mdx"],
        inputs!["pylib/tools/genhooks.py", "tools/mintlify_hooks.py"],
    )
}
